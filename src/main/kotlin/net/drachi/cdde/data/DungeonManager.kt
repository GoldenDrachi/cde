package net.drachi.cdde.data

import net.drachi.cdde.data.StairDirection
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import net.drachi.cdde.CobblemonDungeonDungeonsEngine
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import java.io.File

import net.minecraft.world.phys.AABB
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.chunk.PalettedContainer
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.drachi.cdde.generation.DungeonGrid
import java.util.UUID

data class ActiveDungeon(
    val instanceId: UUID,
    val config: DungeonConfig,
    val originX: Int,
    val originZ: Int,
    var currentFloor: Int,
    val returnLocations: MutableMap<UUID, net.minecraft.core.BlockPos> = mutableMapOf(),
    val floorStartPositions: MutableMap<Int, net.minecraft.core.BlockPos> = mutableMapOf(),
    val stairPositions: MutableMap<Int, net.minecraft.core.BlockPos> = mutableMapOf(),
    var lastActiveTime: Long = System.currentTimeMillis()
)

object DungeonManager {
    
    var nextInstanceIndex = 1
    val activeDungeons = mutableMapOf<UUID, ActiveDungeon>()
    
    val pokemonSpawns = mutableListOf<net.minecraft.core.BlockPos>()
    val itemSpawns = mutableListOf<net.minecraft.core.BlockPos>()
    val treasureSpawns = mutableListOf<net.minecraft.core.BlockPos>()
    val bossSpawns = mutableListOf<net.minecraft.core.BlockPos>()
    val minionSpawns = mutableListOf<net.minecraft.core.BlockPos>()
    val endStairSpawns = mutableListOf<net.minecraft.core.BlockPos>()
    
    fun getActiveDungeon(player: net.minecraft.world.entity.player.Player): ActiveDungeon? {
        val level = player.level()
        val dim = level.dimension().location()
        if (dim.namespace != "cdde" || dim.path != "dungeon") return null
        val z = player.blockPosition().z
        val instanceIndex = z / 10000
        val expectedOriginZ = instanceIndex * 10000
        return activeDungeons.values.find { it.originZ == expectedOriginZ }
    }

    fun isInDungeon(player: net.minecraft.world.entity.player.Player): Boolean {
        return getActiveDungeon(player) != null
    }
    
    fun clearSpawns() {
        pokemonSpawns.clear()
        itemSpawns.clear()
        treasureSpawns.clear()
        bossSpawns.clear()
        minionSpawns.clear()
        endStairSpawns.clear()
    }
    
    val configs = mutableMapOf<String, DungeonConfig>()

    /** Theme → list of ResourceLocations for that theme's rooms. */
    val availableRooms = mutableMapOf<String, MutableList<ResourceLocation>>()
    /** Theme → list of ResourceLocations for that theme's hallways (auto-categorized corridor pieces). */
    val availableHallways = mutableMapOf<String, MutableList<ResourceLocation>>()
    /** Theme → list of ResourceLocations for that theme's dead-end caps. */
    val availableEnds = mutableMapOf<String, MutableList<ResourceLocation>>()
    /** Theme → list of ResourceLocations for that theme's staircases. */
    val availableStairs = mutableMapOf<String, MutableList<ResourceLocation>>()

    /**
     * All loaded structure templates keyed by ResourceLocation.
     * Populated during [scanNbtStructures] by reading .nbt files directly from disk —
     * bypasses StructureTemplateManager which only resolves from datapacks.
     */
    val loadedTemplates = mutableMapOf<ResourceLocation, StructureTemplate>()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun init() {
        ConfigManager.init()
        ConfigManager.loadDungeonConfigs()
        
        val config = configs["default"] ?: configs.values.firstOrNull()
        if (config != null) {
            val loaded = net.drachi.cdde.database.DatabaseManager.loadActiveDungeons(ConfigManager.globalConfig.serverId)
            activeDungeons.putAll(loaded)
        }
        scanNbtStructures()
    }

    private fun scanNbtStructures() {
        availableRooms.clear()
        availableHallways.clear()
        availableEnds.clear()
        availableStairs.clear()
        loadedTemplates.clear()

        val structuresDir = FabricLoader.getInstance().configDir.resolve("cdde/structures").toFile()
        if (!structuresDir.exists()) structuresDir.mkdirs()

        // Extract default structures from the mod jar to the config folder
        val container = FabricLoader.getInstance().getModContainer("cdde").orElse(null)
        if (container != null) {
            val defaultStructuresPath = container.getPath("default_structures")
            if (java.nio.file.Files.exists(defaultStructuresPath)) {
                java.nio.file.Files.walk(defaultStructuresPath).forEach { path ->
                    if (java.nio.file.Files.isRegularFile(path) && path.fileName.toString().endsWith(".nbt")) {
                        val dest = structuresDir.toPath().resolve(defaultStructuresPath.relativize(path).toString())
                        if (!java.nio.file.Files.exists(dest)) {
                            java.nio.file.Files.createDirectories(dest.parent)
                            java.nio.file.Files.copy(path, dest)
                            CobblemonDungeonDungeonsEngine.logger.info("Extracted default structure: ${dest.fileName}")
                        }
                    }
                }
            }
        }

        structuresDir.listFiles { file -> file.extension == "nbt" }?.forEach { file ->
            val name = file.nameWithoutExtension
            val parts = name.split("_")
            if (parts.size >= 3) {
                val theme = parts[0]
                val resourceId = ResourceLocation.fromNamespaceAndPath("cdde", name)

                // Load the .nbt file directly from disk into a StructureTemplate
                val template = loadNbtFile(file)
                if (template == null) return@forEach

                loadedTemplates[resourceId] = template

                // Categorize by naming convention: [theme]_room_*, [theme]_hallway_*, [theme]_end_*, [theme]_stairs_*
                when {
                    name.contains("_room_") ->
                        availableRooms.getOrPut(theme) { mutableListOf() }.add(resourceId)
                    name.contains("_hallway_") ->
                        availableHallways.getOrPut(theme) { mutableListOf() }.add(resourceId)
                    name.contains("_end_") ->
                        availableEnds.getOrPut(theme) { mutableListOf() }.add(resourceId)
                    name.contains("_stairs_") || name.contains("_stair_") ->
                        availableStairs.getOrPut(theme) { mutableListOf() }.add(resourceId)
                }
            }
        }

        CobblemonDungeonDungeonsEngine.logger.info(
            "Loaded ${loadedTemplates.size} templates: " +
            "${availableRooms.values.flatten().size} rooms, " +
            "${availableHallways.values.flatten().size} hallways, " +
            "${availableEnds.values.flatten().size} ends, " +
            "${availableStairs.values.flatten().size} stairs."
        )
    }

    /**
     * Reads a .nbt file from disk and inflates it into a [StructureTemplate].
     * Uses [NbtIo.readCompressed] + [StructureTemplate.load] with the block registry lookup.
     *
     * @return The loaded template, or null if loading failed.
     */
    private fun loadNbtFile(file: File): StructureTemplate? {
        return try {
            val nbt = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap())
            val template = StructureTemplate()
            template.load(BuiltInRegistries.BLOCK.asLookup(), nbt)
            template
        } catch (e: Exception) {
            CobblemonDungeonDungeonsEngine.logger.error("Failed to load NBT structure: ${file.name}", e)
            null
        }
    }

    fun allocateInstance(config: DungeonConfig): ActiveDungeon {
        val id = UUID.randomUUID()
        
        // Find the lowest available Z coordinate that is a multiple of 10000
        var nextZ = 0
        val usedZs = activeDungeons.values.map { it.originZ }.toSet()
        while (usedZs.contains(nextZ)) {
            nextZ += 10000
        }
        
        val instance = ActiveDungeon(
            instanceId = id,
            config = config,
            originX = 0,
            originZ = nextZ,
            currentFloor = 1
        )
        activeDungeons[id] = instance
        net.drachi.cdde.database.DatabaseManager.saveActiveDungeon(instance, ConfigManager.globalConfig.serverId)
        return instance
    }

    fun tickDungeonLifecycle(server: net.minecraft.server.MinecraftServer) {
        val now = System.currentTimeMillis()
        val timeoutMs = ConfigManager.globalConfig.abandonTimeoutMinutes * 60L * 1000L
        
        val toRemove = mutableListOf<UUID>()
        for ((id, dungeon) in activeDungeons) {
            if (dungeon.returnLocations.isEmpty()) {
                CobblemonDungeonDungeonsEngine.logger.info("Dungeon $id is completely empty (all players left) and is being cleared.")
                toRemove.add(id)
            } else {
                var hasOnlinePlayers = false
                for (playerUuid in dungeon.returnLocations.keys) {
                    if (server.playerList.getPlayer(playerUuid) != null) {
                        hasOnlinePlayers = true
                        break
                    }
                }
                
                if (hasOnlinePlayers) {
                    dungeon.lastActiveTime = now
                    if (server.tickCount % 200 == 0) { // save every 10 seconds
                        net.drachi.cdde.database.DatabaseManager.saveActiveDungeon(dungeon, ConfigManager.globalConfig.serverId)
                    }
                } else {
                    if (now - dungeon.lastActiveTime > timeoutMs) {
                        CobblemonDungeonDungeonsEngine.logger.info("Dungeon $id has timed out and is being cleared.")
                        toRemove.add(id)
                    }
                }
            }
        }
        
        for (id in toRemove) {
            val dungeon = activeDungeons.remove(id) ?: continue
            
            // Move offline players to evicted table
            net.drachi.cdde.database.DatabaseManager.evictPlayersFromDungeon(id)
            net.drachi.cdde.database.DatabaseManager.deleteActiveDungeon(id)
            
            // Clear the terrain to make room for future dungeons
            val dungeonLevel = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cdde", "dungeon")))
            if (dungeonLevel != null) {
                // Clear up to currentFloor + 1 (in case a generation was midway)
                for (f in 1..dungeon.currentFloor + 1) {
                    val floorOriginZ = dungeon.originZ
                    val floorOriginX = dungeon.originX + ((f - 1) * 1000)
                    
                    val dim = DungeonGrid.gridSizeForRooms(dungeon.config.getFloorConfig(f).maxRooms)
                    val maxBlocks = dim * DungeonGrid.CELL_SIZE
                    val bounds = net.minecraft.world.phys.AABB(
                        floorOriginX.toDouble() - 50.0, -64.0, floorOriginZ.toDouble() - 50.0,
                        floorOriginX.toDouble() + maxBlocks.toDouble() + 50.0, 319.0, floorOriginZ.toDouble() + maxBlocks.toDouble() + 50.0
                    )
                    clearRegion(dungeonLevel, bounds, dungeon.config, dungeon.config.getFloorConfig(f))
                }
            }
        }
    }

    fun clearRegion(level: ServerLevel, bounds: AABB, config: DungeonConfig?, floorConfig: FloorConfig? = null) {
        val minX = bounds.minX.toInt()
        val maxX = bounds.maxX.toInt()
        val minZ = bounds.minZ.toInt()
        val maxZ = bounds.maxZ.toInt()

        val chunkMinX = minX shr 4
        val chunkMaxX = maxX shr 4
        val chunkMinZ = minZ shr 4
        val chunkMaxZ = maxZ shr 4

        val airState = Blocks.AIR.defaultBlockState()



        for (cx in chunkMinX..chunkMaxX) {
            for (cz in chunkMinZ..chunkMaxZ) {
                val chunk = level.getChunk(cx, cz)
                val blockEntities = chunk.blockEntities.keys.toList()
                blockEntities.forEach { chunk.removeBlockEntity(it) }

                val sections = chunk.sections
                for (i in sections.indices) {
                    val section = sections[i]
                    if (section == null) continue

                    if (!section.hasOnlyAir()) {
                        val registry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                        
                        // Parse configurable biome or fallback to plains
                        val biomeId = ResourceLocation.parse(config?.biome ?: "minecraft:plains")
                        val biomeKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, biomeId)
                        val targetBiome = registry.getHolder(biomeKey).orElse(registry.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS))
                        
                        sections[i] = LevelChunkSection(
                            PalettedContainer(Block.BLOCK_STATE_REGISTRY, airState, PalettedContainer.Strategy.SECTION_STATES),
                            PalettedContainer(registry.asHolderIdMap(), targetBiome, PalettedContainer.Strategy.SECTION_BIOMES)
                        )
                        section.recalcBlockCounts()
                    }
                }
                chunk.initializeLightSources()
                level.chunkSource.lightEngine.lightChunk(chunk, false)
                chunk.isUnsaved = true
            }
        }

        // Clean up entities in the area (items, etc.) but not players
        val entities = level.getEntitiesOfClass(net.minecraft.world.entity.Entity::class.java, bounds)
        for (e in entities) {
            if (e !is net.minecraft.world.entity.player.Player) {
                e.discard()
            }
        }
    }

    fun onPlayerInteractStairs(player: net.minecraft.server.level.ServerPlayer, stairsPos: net.minecraft.core.BlockPos) {
        val level = player.serverLevel()
        val dim = level.dimension().location()
        if (dim.namespace != "cdde" || dim.path != "dungeon") return

        // Find which instance the player is in by checking Z coordinate
        val z = player.blockPosition().z
        val instanceIndex = z / 10000
        val expectedOriginZ = instanceIndex * 10000

        val instance = activeDungeons.values.find { it.originZ == expectedOriginZ } ?: return

        // Teleport to next floor
        instance.currentFloor++
        CobblemonDungeonDungeonsEngine.logger.info("Player ${player.name.string} descending to floor ${instance.currentFloor} of dungeon ${instance.instanceId}")

        // Generate Floor Next
        val generatorNext = net.drachi.cdde.generation.DungeonGenerator(
            level,
            net.minecraft.core.BlockPos(instance.originX + ((instance.currentFloor - 1) * 1000), 64, instance.originZ),
            instance.config,
            instance.currentFloor
        )
        generatorNext.generate()
        val startPosFloorNext = generatorNext.startPosition ?: net.minecraft.core.BlockPos(instance.originX + ((instance.currentFloor - 1) * 1000), 65, instance.originZ)
        instance.floorStartPositions[instance.currentFloor] = startPosFloorNext
        if (generatorNext.stairPosition != null) {
            instance.stairPositions[instance.currentFloor] = generatorNext.stairPosition!!
        }

        // Find all party members (fallback to just the player if not in a party)
        val partyMembers = net.drachi.cdde.api.GroupAPI.getPartyMembers(player.uuid) ?: listOf(player.uuid)
        val playersToTeleport = partyMembers.mapNotNull { player.server.playerList.getPlayer(it) }

        // Start position for the floor we are entering
        val startPos = instance.floorStartPositions[instance.currentFloor] ?: net.minecraft.core.BlockPos(instance.originX + ((instance.currentFloor - 1) * 1000), 64 + 1, instance.originZ)

        // Title text logic
        val isDown = instance.config.stairDirection == StairDirection.DOWN
        val titleJson = if (instance.currentFloor == 1) {
            "{\"translate\":\"message.cdde.floor_eg\", \"color\":\"yellow\"}"
        } else if (isDown) {
            "{\"translate\":\"message.cdde.floor_down\", \"with\":[\"${instance.currentFloor - 1}\"], \"color\":\"yellow\"}"
        } else {
            "{\"translate\":\"message.cdde.floor_up\", \"with\":[\"${instance.currentFloor - 1}\"], \"color\":\"yellow\"}"
        }

        // Array of offsets to prevent entities from clipping into each other
        val spawnOffsets = arrayOf(
            Pair(0, 0), Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1),
            Pair(1, 1), Pair(-1, 1), Pair(1, -1), Pair(-1, -1),
            Pair(2, 0), Pair(-2, 0), Pair(0, 2), Pair(0, -2)
        )
        var spawnIndex = 0

        // Teleport everyone and their Pokemon BEFORE wiping the old chunks
        playersToTeleport.forEach { member ->
            // Pick a spot for the player
            val pOffset = spawnOffsets[spawnIndex % spawnOffsets.size]
            spawnIndex++
            val pPosRaw = startPos.offset(pOffset.first, 0, pOffset.second)
            val pPos = findSafeSpawn(level, pPosRaw)
            
            // Teleport player (add 0.5 to center in block)
            member.teleportTo(level, pPos.x.toDouble() + 0.5, pPos.y.toDouble(), pPos.z.toDouble() + 0.5, member.yRot, member.xRot)

            // Teleport out-of-ball party Pokemon
            val memberParty = com.cobblemon.mod.common.Cobblemon.storage.getParty(member)
            for (i in 0 until memberParty.size()) {
                val pokemon = memberParty.get(i)
                if (pokemon != null && pokemon.entity != null) {
                    val pEntity = pokemon.entity!!
                    
                    val pokeOffset = spawnOffsets[spawnIndex % spawnOffsets.size]
                    spawnIndex++
                    val pokePosRaw = startPos.offset(pokeOffset.first, 0, pokeOffset.second)
                    val pokePos = findSafeSpawn(level, pokePosRaw)
                    
                    pEntity.teleportTo(pokePos.x.toDouble() + 0.5, pokePos.y.toDouble(), pokePos.z.toDouble() + 0.5)
                }
            }

            // Show Floor Title
            member.server.commands.performPrefixedCommand(
                member.createCommandSourceStack().withPermission(2).withSuppressedOutput(),
                "title @s title $titleJson"
            )
        }

        // Now that everyone is teleported, wipe the chunks of the floor they just left
        val oldFloor = instance.currentFloor - 1
        val floorOriginZ = instance.originZ
        val floorOriginX = instance.originX + ((oldFloor - 1) * 1000)
        
        val gridDim = net.drachi.cdde.generation.DungeonGrid.gridSizeForRooms(instance.config.getFloorConfig(oldFloor).maxRooms)
        val maxBlocks = gridDim * net.drachi.cdde.generation.DungeonGrid.CELL_SIZE
        val bounds = net.minecraft.world.phys.AABB(
            floorOriginX.toDouble() - 50.0, -64.0, floorOriginZ.toDouble() - 50.0,
            floorOriginX.toDouble() + maxBlocks.toDouble() + 50.0, 319.0, floorOriginZ.toDouble() + maxBlocks.toDouble() + 50.0
        )
        clearRegion(level, bounds, instance.config, instance.config.getFloorConfig(oldFloor))
    }

    fun findSafeSpawn(level: ServerLevel, centerPos: net.minecraft.core.BlockPos): net.minecraft.core.BlockPos {
        val maxRadius = 5
        for (r in 0..maxRadius) {
            for (x in -r..r) {
                for (z in -r..r) {
                    if (kotlin.math.abs(x) != r && kotlin.math.abs(z) != r && r != 0) continue
                    val yOffsets = listOf(0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5)
                    for (y in yOffsets) { // Check downwards to find floor, and upwards to find clearance
                        val pos = centerPos.offset(x, y, z)
                        val below = level.getBlockState(pos.below())
                        val current = level.getBlockState(pos)
                        val above = level.getBlockState(pos.above())
                        
                        if (below.isSolidRender(level, pos.below()) && current.getCollisionShape(level, pos).isEmpty && above.getCollisionShape(level, pos.above()).isEmpty) {
                            return pos
                        }
                    }
                }
            }
        }
        return centerPos // Fallback
    }
}
