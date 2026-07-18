package net.drachi.cde.dungeonsengine.data

import net.drachi.cde.config.*

import net.drachi.cde.dungeonsengine.data.StairDirection
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import net.drachi.cde.CDE
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
import net.drachi.cde.dungeonsengine.generation.DungeonGrid
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
    val collectedLoot: MutableList<net.minecraft.world.item.ItemStack> = mutableListOf(),
    var lastActiveTime: Long = System.currentTimeMillis(),
    var freezeTicks: Int = 0
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
    
    fun addLootToDungeon(level: net.minecraft.server.level.ServerLevel, pos: net.minecraft.core.BlockPos, stack: net.minecraft.world.item.ItemStack) {
        val instanceIndex = pos.z / 10000
        val expectedOriginZ = instanceIndex * 10000
        val dungeon = activeDungeons.values.find { it.originZ == expectedOriginZ }
        if (dungeon != null) {
            dungeon.collectedLoot.add(stack)
            if (net.drachi.cde.config.ConfigManager.globalConfig.debugLogging) {
                net.drachi.cde.CDE.logger.info("Collected loot for dungeon ${dungeon.instanceId}: ${stack.count}x ${stack.item.descriptionId}")
            }
        }
    }

    fun getActiveDungeon(player: net.minecraft.world.entity.player.Player): ActiveDungeon? {
        val level = player.level()
        val dim = level.dimension().location()
        if (dim.namespace != "cde" || dim.path != "dungeon") return null
        val z = player.blockPosition().z
        val instanceIndex = z / 10000
        val expectedOriginZ = instanceIndex * 10000
        return activeDungeons.values.find { it.originZ == expectedOriginZ }
    }

    fun isInDungeon(player: net.minecraft.world.entity.player.Player): Boolean {
        return getActiveDungeon(player) != null
    }

    fun freezeDungeon(instanceId: UUID, ticks: Int = -1) {
        val active = activeDungeons[instanceId]
        if (active != null) {
            active.freezeTicks = ticks
        }
    }

    private fun applyFloorWeather(config: DungeonConfig, floor: Int) {
        if (!FabricLoader.getInstance().isModLoaded("cde")) return
        
        val floorConfig = config.getFloorConfig(floor)
        try {
            net.drachi.cde.dungeonsengine.compat.WeatherCompat.applyWeather(floorConfig.defaultWeather)
        } catch (e: Exception) {
            CDE.logger.error("Failed to apply weather from CDBE", e)
        }
    }

    fun unfreezeDungeon(instanceId: UUID) {
        val instance = activeDungeons[instanceId]
        if (instance != null) {
            instance.freezeTicks = 0
        }
    }
    
    fun clearSpawns() {
        pokemonSpawns.clear()
        itemSpawns.clear()
        treasureSpawns.clear()
        bossSpawns.clear()
        minionSpawns.clear()
        endStairSpawns.clear()
    }

    fun getBrokenDungeons(): List<String> {
        return net.drachi.cde.dungeonsengine.database.DatabaseManager.getBrokenDungeons(ConfigManager.globalConfig.serverId)
    }

    fun getAllDungeons(): List<String> {
        return net.drachi.cde.dungeonsengine.database.DatabaseManager.getAllDungeons(ConfigManager.globalConfig.serverId)
    }
    
    fun performCleanup(uuids: List<String>): Int {
        var count = 0
        for (id in uuids) {
            net.drachi.cde.dungeonsengine.database.DatabaseManager.deleteActiveDungeon(java.util.UUID.fromString(id))
            activeDungeons.remove(java.util.UUID.fromString(id))
            count++
        }
        return count
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
            val loaded = net.drachi.cde.dungeonsengine.database.DatabaseManager.loadActiveDungeons(ConfigManager.globalConfig.serverId)
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

        val structuresDir = FabricLoader.getInstance().configDir.resolve("cde/structures").toFile()
        if (!structuresDir.exists()) structuresDir.mkdirs()

        // Extract default structures from the mod jar to the config folder
        val container = FabricLoader.getInstance().getModContainer("cde").orElse(null)
        if (container != null) {
            val defaultStructuresPath = container.getPath("default_structures")
            if (java.nio.file.Files.exists(defaultStructuresPath)) {
                java.nio.file.Files.walk(defaultStructuresPath).forEach { path ->
                    if (java.nio.file.Files.isRegularFile(path) && path.fileName.toString().endsWith(".nbt")) {
                        val dest = structuresDir.toPath().resolve(defaultStructuresPath.relativize(path).toString())
                        if (!java.nio.file.Files.exists(dest)) {
                            java.nio.file.Files.createDirectories(dest.parent)
                            java.nio.file.Files.copy(path, dest)
                            CDE.logger.info("Extracted default structure: ${dest.fileName}")
                        }
                    }
                }
            }
        }

        structuresDir.listFiles { file -> file.extension == "nbt" }?.forEach { file ->
            val name = file.nameWithoutExtension
            
            val theme = when {
                name.contains("_room_") -> name.substringBefore("_room_")
                name.contains("_hallway_") -> name.substringBefore("_hallway_")
                name.contains("_end_") -> name.substringBefore("_end_")
                name.contains("_stairs_") -> name.substringBefore("_stairs_")
                name.contains("_stair_") -> name.substringBefore("_stair_")
                else -> null
            }

            if (theme != null) {
                val resourceId = ResourceLocation.fromNamespaceAndPath("cde", name)

                // Load the .nbt file directly from disk into a StructureTemplate
                val template = loadNbtFile(file)
                if (template != null) {
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
        }

        CDE.logger.info(
            "Loaded ${loadedTemplates.size} templates: " +
            "${availableRooms.values.flatten().size} rooms, " +
            "${availableHallways.values.flatten().size} hallways, " +
            "${availableEnds.values.flatten().size} ends, " +
            "${availableStairs.values.flatten().size} stairs."
        )
    }

    private fun migrateCddeToCde(tag: net.minecraft.nbt.Tag) {
        when (tag) {
            is net.minecraft.nbt.CompoundTag -> {
                for (key in tag.allKeys.toList()) {
                    val child = tag.get(key)
                    if (child is net.minecraft.nbt.StringTag) {
                        val str = child.asString
                        if (str.startsWith("cdde:")) {
                            tag.putString(key, str.replaceFirst("cdde:", "cde:"))
                        }
                    } else if (child != null) {
                        migrateCddeToCde(child)
                    }
                }
            }
            is net.minecraft.nbt.ListTag -> {
                for (i in 0 until tag.size) {
                    val child = tag.get(i)
                    if (child is net.minecraft.nbt.CompoundTag || child is net.minecraft.nbt.ListTag) {
                        migrateCddeToCde(child)
                    } else if (child is net.minecraft.nbt.StringTag) {
                        val str = child.asString
                        if (str.startsWith("cdde:")) {
                            tag.set(i, net.minecraft.nbt.StringTag.valueOf(str.replaceFirst("cdde:", "cde:")))
                        }
                    }
                }
            }
        }
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
            
            // --- Backwards Compatibility for 'cdde' to 'cde' mod id migration ---
            migrateCddeToCde(nbt)
            // --------------------------------------------------------------------

            val template = StructureTemplate()
            template.load(BuiltInRegistries.BLOCK.asLookup(), nbt)
            template
        } catch (e: Exception) {
            CDE.logger.error("Failed to load NBT structure: ${file.name}", e)
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
        net.drachi.cde.dungeonsengine.database.DatabaseManager.saveActiveDungeon(instance, ConfigManager.globalConfig.serverId)
        return instance
    }

    fun tick(server: net.minecraft.server.MinecraftServer) {
        val level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cde", "dungeon")))
        if (level == null) return
        
        // Handle frozen dungeons (freezeTicks > 0 means timed, freezeTicks < 0 means indefinite)
        val frozenDungeons = activeDungeons.values.filter { it.freezeTicks != 0 }
        frozenDungeons.forEach { 
            if (it.freezeTicks > 0) {
                it.freezeTicks-- 
            }
        }
        if (frozenDungeons.isNotEmpty()) {
            for (entity in level.allEntities) {
                if (entity is net.minecraft.world.entity.LivingEntity) {
                    val z = entity.blockPosition().z
                    val instanceIndex = z / 10000
                    val expectedOriginZ = instanceIndex * 10000
                    
                    val inFrozenDungeon = frozenDungeons.any { it.originZ == expectedOriginZ }
                    if (inFrozenDungeon) {
                        entity.addEffect(net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 2, 255, false, false, false))
                        entity.addEffect(net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.JUMP, 2, 200, false, false, false))
                        entity.addEffect(net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 2, 255, false, false, false))
                        entity.addEffect(net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, 2, 255, false, false, false))
                    }
                }
            }
        }

        val now = System.currentTimeMillis()
        val timeoutMs = net.drachi.cde.dungeonsengine.config.DungeonsEngineConfigManager.config.abandonTimeoutMinutes * 60L * 1000L
        
        val toRemove = mutableListOf<UUID>()
        for ((id, dungeon) in activeDungeons) {
            if (dungeon.returnLocations.isEmpty()) {
                CDE.logger.info("Dungeon $id is completely empty (all players left) and is being cleared.")
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
                        net.drachi.cde.dungeonsengine.database.DatabaseManager.saveActiveDungeon(dungeon, ConfigManager.globalConfig.serverId)
                    }
                } else {
                    if (now - dungeon.lastActiveTime > timeoutMs) {
                        CDE.logger.info("Dungeon $id has timed out and is being cleared.")
                        toRemove.add(id)
                    }
                }
            }
        }
        
        for (id in toRemove) {
            val dungeon = activeDungeons.remove(id) ?: continue
            
            // Move offline players to evicted table
            net.drachi.cde.dungeonsengine.database.DatabaseManager.evictPlayersFromDungeon(id)
            net.drachi.cde.dungeonsengine.database.DatabaseManager.deleteActiveDungeon(id)
            
            // Clear the terrain to make room for future dungeons
            val dungeonLevel = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cde", "dungeon")))
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

                val startX = maxOf(minX, cx * 16)
                val endX = minOf(maxX, cx * 16 + 15)
                val startZ = maxOf(minZ, cz * 16)
                val endZ = minOf(maxZ, cz * 16 + 15)

                for (x in startX..endX) {
                    for (z in startZ..endZ) {
                        for (y in 0..255) {
                            val pos = net.minecraft.core.BlockPos(x, y, z)
                            if (!chunk.getBlockState(pos).isAir) {
                                // 2 = update clients
                                // 16 = no neighbor update (prevent cascades)
                                // 32 = suppress drops
                                level.setBlock(pos, airState, 50)
                            }
                        }
                    }
                }
            }
        }

        // Clean up entities in the area (items, etc.) but not players
        val entities = level.getEntities(null, bounds)
        for (entity in entities) {
            if (entity !is net.minecraft.world.entity.player.Player) {
                if (entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity && entity.pokemon.getOwnerUUID() != null) {
                    continue
                }
                entity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED)
            }
        }
    }

    fun getPartyName(player: net.minecraft.server.level.ServerPlayer): String {
        val partyMembers = net.drachi.cde.dungeonsengine.api.GroupAPI.getPartyMembers(player.uuid)
        return if (partyMembers != null && partyMembers.size > 1) {
            // Assume the first member is the leader if GroupAPI doesn't specify
            val leaderId = partyMembers.first()
            val leader = player.server.playerList.getPlayer(leaderId)
            if (leader != null) {
                "${leader.name.string}'s Party"
            } else {
                "${player.name.string}'s Party"
            }
        } else {
            player.name.string
        }
    }

    fun onPlayerInteractStairs(player: net.minecraft.server.level.ServerPlayer, stairsPos: net.minecraft.core.BlockPos) {
        val level = player.serverLevel()
        val dim = level.dimension().location()
        if (dim.namespace != "cde" || dim.path != "dungeon") return

        // Find which instance the player is in by checking Z coordinate
        val z = player.blockPosition().z
        val instanceIndex = z / 10000
        val expectedOriginZ = instanceIndex * 10000

        val instance = activeDungeons.values.find { it.originZ == expectedOriginZ } ?: return

        val partyMembers = net.drachi.cde.dungeonsengine.api.GroupAPI.getPartyMembers(player.uuid) ?: listOf(player.uuid)
        val playersToTeleport = partyMembers.mapNotNull { player.server.playerList.getPlayer(it) }
        val partyName = getPartyName(player)

        // Check for completion
        if (instance.currentFloor >= instance.config.amountOfFloors) {
            val overworld = player.server.getLevel(net.minecraft.world.level.Level.OVERWORLD)
            playersToTeleport.forEach { member ->
                val returnPos = instance.returnLocations[member.uuid]
                
                member.portalCooldown = 100
                if (returnPos != null && overworld != null) {
                    member.teleportTo(overworld, returnPos.x.toDouble() + 0.5, returnPos.y.toDouble(), returnPos.z.toDouble() + 0.5, member.yRot, member.xRot)
                } else if (overworld != null) {
                    val spawn = overworld.sharedSpawnPos
                    member.teleportTo(overworld, spawn.x.toDouble(), spawn.y.toDouble(), spawn.z.toDouble(), member.yRot, member.xRot)
                }
                
                com.cobblemon.mod.common.Cobblemon.storage.getParty(member).heal()
                net.drachi.cde.network.NetworkHandler.sendDungeonResult(member, instance.config.id, partyName, "message.cde.result.completed")
            }
            return
        }

        // Teleport to next floor
        instance.currentFloor++
        CDE.logger.info("Player ${player.name.string} descending to floor ${instance.currentFloor} of dungeon ${instance.instanceId}")

        // Generate Floor Next
        val generatorNext = net.drachi.cde.dungeonsengine.generation.DungeonGenerator(
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

        applyFloorWeather(instance.config, instance.currentFloor)

        // Start position for the floor we are entering
        val startPos = instance.floorStartPositions[instance.currentFloor] ?: net.minecraft.core.BlockPos(instance.originX + ((instance.currentFloor - 1) * 1000), 64 + 1, instance.originZ)

        // Title text logic
        val isDown = instance.config.stairDirection == StairDirection.DOWN
        val transKey = if (isDown) "message.cde.floor_down" else "message.cde.floor_up"
        val subtitleJson = "{\"translate\":\"$transKey\", \"with\":[\"${instance.currentFloor}\"], \"color\":\"yellow\"}"
        val dungeonName = instance.config.id.split("_").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        val titleJson = "{\"text\":\"$dungeonName\", \"color\":\"gold\"}"

        // Array of offsets to prevent entities from clipping into each other
        val spawnOffsets = arrayOf(
            Pair(0, 0), Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1),
            Pair(1, 1), Pair(-1, 1), Pair(1, -1), Pair(-1, -1),
            Pair(2, 0), Pair(-2, 0), Pair(0, 2), Pair(0, -2)
        )
        var spawnIndex = 0

        // Freeze entities for configured ticks
        freezeDungeon(instance.instanceId, net.drachi.cde.dungeonsengine.config.DungeonsEngineConfigManager.config.floorStartFreezeTicks)

        // Teleport everyone and their Pokemon BEFORE wiping the old chunks
        playersToTeleport.forEach { member ->
            // Pick a spot for the player
            val pOffset = spawnOffsets[spawnIndex % spawnOffsets.size]
            spawnIndex++
            val pPosRaw = startPos.offset(pOffset.first, 0, pOffset.second)
            val pPos = findSafeSpawn(level, pPosRaw)
            
            // Teleport player (add 0.5 to center in block)
            member.teleportTo(level, pPos.x.toDouble() + 0.5, pPos.y.toDouble(), pPos.z.toDouble() + 0.5, member.yRot, member.xRot)

            // Teleport out-of-ball party Pokemon & Enforce PMD mechanics
            val indexRef = IntArray(1) { spawnIndex }
            net.drachi.cde.dungeonsengine.data.DungeonPartyManager.enforceDungeonPartyState(member, level, startPos, spawnOffsets, indexRef)
            spawnIndex = indexRef[0]

            // Show Floor Title
            member.server.commands.performPrefixedCommand(
                member.createCommandSourceStack().withPermission(2).withSuppressedOutput(),
                "title @s subtitle $subtitleJson"
            )
            member.server.commands.performPrefixedCommand(
                member.createCommandSourceStack().withPermission(2).withSuppressedOutput(),
                "title @s title $titleJson"
            )
        }

        // Now that everyone is teleported, wipe the chunks of the floor they just left
        val oldFloor = instance.currentFloor - 1
        val floorOriginZ = instance.originZ
        val floorOriginX = instance.originX + ((oldFloor - 1) * 1000)
        
        val gridDim = net.drachi.cde.dungeonsengine.generation.DungeonGrid.gridSizeForRooms(instance.config.getFloorConfig(oldFloor).maxRooms)
        val maxBlocks = gridDim * net.drachi.cde.dungeonsengine.generation.DungeonGrid.CELL_SIZE
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

    fun getSafeOverworldReturn(level: ServerLevel, returnPos: net.minecraft.core.BlockPos): net.minecraft.core.BlockPos {
        val blockState = level.getBlockState(returnPos)
        if (blockState.block is net.drachi.cde.dungeonsengine.blocks.DungeonPortalBlock) {
            // Search in a small radius for a safe spot that is NOT a portal block
            val maxRadius = 3
            for (r in 1..maxRadius) {
                for (x in -r..r) {
                    for (z in -r..r) {
                        if (kotlin.math.abs(x) != r && kotlin.math.abs(z) != r) continue
                        val yOffsets = listOf(0, 1, -1, 2, -2)
                        for (y in yOffsets) {
                            val pos = returnPos.offset(x, y, z)
                            val below = level.getBlockState(pos.below())
                            val current = level.getBlockState(pos)
                            val above = level.getBlockState(pos.above())
                            
                            if (below.isSolidRender(level, pos.below()) && 
                                current.getCollisionShape(level, pos).isEmpty && 
                                above.getCollisionShape(level, pos.above()).isEmpty &&
                                current.block !is net.drachi.cde.dungeonsengine.blocks.DungeonPortalBlock) {
                                return pos
                            }
                        }
                    }
                }
            }
        }
        return returnPos
    }

    fun executeJoin(entity: net.minecraft.server.level.ServerPlayer, configId: String, bypassUnlockCheck: Boolean = false) {
        val config = configs[configId]
        if (config == null) {
            entity.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cThis dungeon is linked to an invalid or missing config: $configId"))
            return
        }

        val partyMembers = net.drachi.cde.dungeonsengine.api.GroupAPI.getPartyMembers(entity.uuid)
        val playersToTeleport = partyMembers?.mapNotNull { entity.server.playerList.getPlayer(it) } ?: listOf(entity)

        if (partyMembers != null) {
            val leader = partyMembers.first() // first is always the leader
            if (entity.uuid != leader) {
                entity.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cOnly the party leader can initiate a dungeon run."))
                return
            }
        }

        if (!bypassUnlockCheck) {
            for (member in playersToTeleport) {
                if (!net.drachi.cde.dungeonsengine.database.DatabaseManager.hasUnlockedDungeon(member.uuid, configId)) {
                    entity.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cCannot start: Party member ${member.scoreboardName} has not unlocked this dungeon."))
                    return
                }
            }
        }

        // Create instance
        val instance = allocateInstance(config)
        
        val dungeonLevel = entity.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cde", "dungeon")))!!

        // Generate floor 1
        val generator = net.drachi.cde.dungeonsengine.generation.DungeonGenerator(
            dungeonLevel,
            net.minecraft.core.BlockPos(instance.originX, 64, instance.originZ),
            instance.config,
            1
        )
        generator.generate()
        
        val startPos = generator.startPosition ?: net.minecraft.core.BlockPos(instance.originX, 65, instance.originZ)
        instance.floorStartPositions[1] = startPos
        if (generator.stairPosition != null) {
            instance.stairPositions[1] = generator.stairPosition!!
        }

        applyFloorWeather(instance.config, 1)

        // Array of offsets to prevent entities from clipping into each other
        val spawnOffsets = arrayOf(
            Pair(0, 0), Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1),
            Pair(1, 1), Pair(-1, 1), Pair(1, -1), Pair(-1, -1),
            Pair(2, 0), Pair(-2, 0), Pair(0, 2), Pair(0, -2)
        )
        var spawnIndex = 0

        // Freeze entities for configured ticks
        freezeDungeon(instance.instanceId, net.drachi.cde.dungeonsengine.config.DungeonsEngineConfigManager.config.floorStartFreezeTicks)

        playersToTeleport.forEach { member ->
            member.portalCooldown = 100
            
            val safeReturn = getSafeOverworldReturn(entity.server.getLevel(net.minecraft.world.level.Level.OVERWORLD)!!, member.blockPosition())
            instance.returnLocations[member.uuid] = safeReturn
            net.drachi.cde.dungeonsengine.database.DatabaseManager.saveDungeonPlayer(instance.instanceId, member.uuid, safeReturn)
            
            // Unlock it for them!
            net.drachi.cde.dungeonsengine.database.DatabaseManager.unlockDungeon(member.uuid, configId)
            
            val pOffset = spawnOffsets[spawnIndex % spawnOffsets.size]
            spawnIndex++
            val pPosRaw = startPos.offset(pOffset.first, 0, pOffset.second)
            val pPos = findSafeSpawn(dungeonLevel, pPosRaw)
            
            member.teleportTo(dungeonLevel, pPos.x.toDouble() + 0.5, pPos.y.toDouble(), pPos.z.toDouble() + 0.5, member.yRot, member.xRot)

            // Teleport out-of-ball party Pokemon & Enforce PMD mechanics
            val indexRef = IntArray(1) { spawnIndex }
            net.drachi.cde.dungeonsengine.data.DungeonPartyManager.enforceDungeonPartyState(member, dungeonLevel, startPos, spawnOffsets, indexRef)
            spawnIndex = indexRef[0]
            
            val isDown = instance.config.stairDirection == StairDirection.DOWN
            val transKey = if (isDown) "message.cde.floor_down" else "message.cde.floor_up"
            val dungeonName = instance.config.id.split("_").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            
            member.server.commands.performPrefixedCommand(
                member.createCommandSourceStack().withPermission(2).withSuppressedOutput(),
                "title @s subtitle {\"translate\":\"$transKey\", \"with\":[\"1\"], \"color\":\"yellow\"}"
            )
            member.server.commands.performPrefixedCommand(
                member.createCommandSourceStack().withPermission(2).withSuppressedOutput(),
                "title @s title {\"text\":\"$dungeonName\", \"color\":\"gold\"}"
            )
        }
    }
}
