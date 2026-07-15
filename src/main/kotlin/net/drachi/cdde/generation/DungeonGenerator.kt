package net.drachi.cdde.generation

import net.drachi.cdde.CobblemonDungeonDungeonsEngine
import net.drachi.cdde.data.DungeonConfig
import net.drachi.cdde.data.DungeonManager
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.drachi.cdde.data.StairDirection
import net.drachi.cdde.registry.ModBlocks
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate

/**
 * Core procedural dungeon layout engine.
 *
 * Architecture: Hauberk-Jigsaw Hybrid with Node & Wire Placement
 *  1. **Logical Phase:** Simulates the maze on a 2D [DungeonGrid] (super-cells).
 *     - Scatters rooms randomly.
 *     - Fills remaining space with a winding maze (Growing Tree).
 *     - Connects all regions ensuring 100% reachability (Union-Find).
 *     - Partially prunes dead ends (configurable percentage).
 *  2. **Physical Phase — Nodes:** Places junction templates (cross, T, corner,
 *     end) at their grid cell positions.
 *  3. **Physical Phase — Wires:** Dynamically chains straight hallway templates
 *     jigsaw-to-jigsaw between adjacent nodes to fill the physical gap.
 *     Supports any template length including 1-block straights.
 *  4. **Post-Processing:** Caps unconnected room exits with end pieces,
 *     seals any remaining gaps with stone brick walls.
 */
class DungeonGenerator(
    private val level: ServerLevel,
    private val origin: BlockPos,
    private val config: DungeonConfig,
    private val floor: Int
) {
    val floorConfig = config.getFloorConfig(floor)
    
    var stairPosition: BlockPos? = null
    var startPosition: BlockPos? = null
    private val placedPieces = mutableListOf<StructurePiece>()
    private val roomPieces = mutableListOf<StructurePiece>()
    private val hazardPositions = mutableListOf<BlockPos>()
    private val enclosedWallCells = mutableSetOf<Pair<Int, Int>>()
    private val customPlayerSpawns = mutableListOf<BlockPos>()

    fun generate() {
        placedPieces.clear()
        roomPieces.clear()
        hazardPositions.clear()
        enclosedWallCells.clear()
        customPlayerSpawns.clear()
        DungeonManager.clearSpawns()

        val isEndFloor = floor == config.amountOfFloors && config.endFloorType != net.drachi.cdde.data.EndFloorType.NORMAL
        val finalMaxRooms = if (isEndFloor) 1 else floorConfig.maxRooms
        
        val dim = DungeonGrid.gridSizeForRooms(finalMaxRooms)
        val maxBlocks = dim * DungeonGrid.CELL_SIZE
        val bounds = net.minecraft.world.phys.AABB(
            origin.x.toDouble() - 50.0, -64.0, origin.z.toDouble() - 50.0,
            origin.x.toDouble() + maxBlocks.toDouble() + 50.0, 319.0, origin.z.toDouble() + maxBlocks.toDouble() + 50.0
        )
        DungeonManager.clearRegion(level, bounds, config, floorConfig)

        // ── 0. Resolve Theme ──────────────────────────────────────────────
        val theme = if (isEndFloor && config.endFloorConfig.theme.isNotEmpty()) {
            config.endFloorConfig.theme
        } else {
            val possibleThemes = floorConfig.activeSets.filter { net.drachi.cdde.data.DungeonManager.availableRooms.containsKey(it) }
            if (possibleThemes.isNotEmpty()) {
                possibleThemes[level.random.nextInt(possibleThemes.size)]
            } else {
                val keys = net.drachi.cdde.data.DungeonManager.availableRooms.keys.toList()
                if (keys.isNotEmpty()) keys[level.random.nextInt(keys.size)] else null
            }
        } ?: run {
            CobblemonDungeonDungeonsEngine.logger.error("No themes available — no structures loaded.")
            return
        }
        val roomIds = DungeonManager.availableRooms[theme] ?: emptyList()
        val hallwayIds = DungeonManager.availableHallways[theme] ?: emptyList()
        val endIds = DungeonManager.availableEnds[theme] ?: emptyList()

        if (roomIds.isEmpty()) {
            CobblemonDungeonDungeonsEngine.logger.error("No rooms found for theme '$theme'")
            return
        }

        // ── 1. Build Grid & Load Templates ────────────────────────────────
        val grid = DungeonGrid(dim, dim, level.random, windingPercent = 45, extraConnectorChance = 6)

        val mapper = TemplateMapper(origin, level.random)
        mapper.loadPools(hallwayIds, endIds)

        // ── 2. Scatter Rooms ──────────────────────────────────────────────
        val roomsPlaced = scatterRooms(grid, roomIds, mapper, finalMaxRooms)

        // ── 3. Logical Maze Generation ────────────────────────────────────
        grid.fillMazes()
        grid.connectRegions()
        grid.removeDeadEnds(floorConfig.deadEndPrunePercent)

        // Compute enclosed wall cells after logical maze is complete
        enclosedWallCells.addAll(mapper.computeEnclosedWallCells(grid))

        CobblemonDungeonDungeonsEngine.logger.info("Grid state after maze generation:\n${grid.toAscii()}")

        // ── 4. Map Nodes (Junctions, Corners, Ends) ───────────────────────
        val nodeMap = mapper.mapNodes(grid, placedPieces, floorConfig, enclosedWallCells)

        // ── 5. Map Wires (Dynamic Straight Fill) ──────────────────────────
        mapper.mapWires(grid, nodeMap, roomPieces, placedPieces, floorConfig, enclosedWallCells)
        mapper.optimizeJunctionExits(nodeMap, placedPieces, floorConfig, enclosedWallCells)

        // ── 5b. Cap Unconnected Node Exits (Mystery Dungeon dead-end caps) ─
        val uncappedNodeExits = mapper.capUnconnectedNodeExits(grid, nodeMap, placedPieces, floorConfig, enclosedWallCells)

        // ── 6. Cap Unconnected Room Exits ─────────────────────────────────
        val uncappedRoomExits = mapper.capUnconnectedRoomExits(grid, roomPieces, placedPieces, nodeMap, floorConfig, enclosedWallCells)

        // ── 7. Render All Pieces to World ─────────────────────────────────
        for (piece in placedPieces) {
            renderPiece(piece)
            val relFloorY = (piece.template.filterBlocks(BlockPos.ZERO, StructurePlaceSettings().setRotation(piece.rotation), Blocks.JIGSAW).minOfOrNull { it.pos.y } ?: 1) - 1
            val openDirs = mapper.detectOpenDirections(piece.template, piece.rotation, relFloorY)
            if (openDirs.isNotEmpty()) {
                CobblemonDungeonDungeonsEngine.logger.info("PIECE PLACED: template=${piece.template} rot=${piece.rotation} pos=${piece.pos} openDirs=$openDirs")
            }
        }

        // ── 7.5 Calculate safe player start position ──────────────────────
        if (roomPieces.isNotEmpty()) {
            for (room in roomPieces) {
                val minJigY = room.parsedJigsaws.minOfOrNull { it.pos.y } ?: room.pos.y
                val floorY = minJigY - 1
                val box = room.boundingBox
                
                val exitPositions = room.parsedJigsaws.map { it.pos }.toSet()
                val reachableFromExits = mutableSetOf<net.minecraft.core.BlockPos>()
                if (exitPositions.isNotEmpty()) {
                    val queue = ArrayDeque<net.minecraft.core.BlockPos>()
                    for (exit in exitPositions) {
                        queue.add(exit)
                        reachableFromExits.add(exit)
                    }
                    
                    while (queue.isNotEmpty()) {
                        val current = queue.removeFirst()
                        for (dir in net.minecraft.core.Direction.Plane.HORIZONTAL) {
                            for (dy in -1..1) {
                                val next = current.relative(dir).above(dy)
                                if (!box.isInside(next) || reachableFromExits.contains(next)) continue
                                
                                val state = level.getBlockState(next)
                                val belowState = level.getBlockState(next.below())
                                val aboveState = level.getBlockState(next.above())
                                
                                val isHazard = state.block == net.drachi.cdde.registry.ModBlocks.HAZARD || 
                                               state.block == net.drachi.cdde.registry.ModBlocks.HAZARD_LAVA ||
                                               state.block == net.drachi.cdde.registry.ModBlocks.HAZARD_VOID ||
                                               state.block == net.drachi.cdde.registry.ModBlocks.HAZARD_WATER ||
                                               state.fluidState.isSource ||
                                               belowState.block == net.drachi.cdde.registry.ModBlocks.HAZARD || 
                                               belowState.block == net.drachi.cdde.registry.ModBlocks.HAZARD_LAVA ||
                                               belowState.block == net.drachi.cdde.registry.ModBlocks.HAZARD_VOID ||
                                               belowState.block == net.drachi.cdde.registry.ModBlocks.HAZARD_WATER ||
                                               belowState.fluidState.isSource
                                               
                                val isSafeWalkable = !isHazard && !belowState.isAir && belowState.blocksMotion() && 
                                                     (state.isAir || !state.blocksMotion()) && 
                                                     (aboveState.isAir || !aboveState.blocksMotion())
                                
                                if (isSafeWalkable) {
                                    reachableFromExits.add(next)
                                    queue.add(next)
                                }
                            }
                        }
                    }
                }
                
                val validSpots = mutableListOf<net.minecraft.core.BlockPos>()
                
                for (x in box.minX()..box.maxX()) {
                    for (z in box.minZ()..box.maxZ()) {
                        val pos = net.minecraft.core.BlockPos(x, floorY, z)
                        val floorState = level.getBlockState(pos)
                        val feetState = level.getBlockState(pos.above())
                        val headState = level.getBlockState(pos.above(2))
                        
                        val isHazard = floorState.block == net.drachi.cdde.registry.ModBlocks.HAZARD || 
                                       floorState.block == net.drachi.cdde.registry.ModBlocks.HAZARD_LAVA ||
                                       floorState.block == net.drachi.cdde.registry.ModBlocks.HAZARD_VOID ||
                                       floorState.block == net.drachi.cdde.registry.ModBlocks.HAZARD_WATER ||
                                       floorState.fluidState.isSource
                                       
                        if (!isHazard && !floorState.isAir && floorState.blocksMotion() && 
                            (feetState.isAir || !feetState.blocksMotion()) && 
                            (headState.isAir || !headState.blocksMotion())) {
                            
                            val posAbove = pos.above()
                            if (exitPositions.isEmpty() || reachableFromExits.contains(posAbove)) {
                                validSpots.add(posAbove)
                            }
                        }
                    }
                }
                
                if (validSpots.size >= 4) {
                    val spawnsInRoom = customPlayerSpawns.filter { box.isInside(it) }
                    if (spawnsInRoom.isNotEmpty()) {
                        this.startPosition = spawnsInRoom.first()
                    } else {
                        val center = net.minecraft.core.BlockPos(box.minX() + (box.maxX() - box.minX())/2, floorY + 1, box.minZ() + (box.maxZ() - box.minZ())/2)
                        this.startPosition = validSpots.minByOrNull { it.distSqr(center) } ?: validSpots.first()
                    }
                    break
                }
            }
        }
        
        if (this.startPosition == null && customPlayerSpawns.isNotEmpty()) {
            this.startPosition = customPlayerSpawns.first()
        }


        // Render Hazard Seas first (before stairs, so stairs are not overwritten or placed on hazard sea)
        renderHazardSeas(grid, mapper)

        // Place stairs in one of the rooms
        placeStairs(roomPieces, theme)

        // Export physical map
        DungeonLogger.exportMap(placedPieces, "logs/dungeon_physical_map.txt")

        // ── 8. Post-Process: Hazards & Sealing ───────────────────────────
        val allUncapped = uncappedNodeExits + uncappedRoomExits
        postProcessHazards()
        sealRemainingExits(allUncapped)

        CobblemonDungeonDungeonsEngine.logger.info(
            "Dungeon generation complete! " +
            "Placed ${placedPieces.size} pieces ($roomsPlaced rooms, " +
            "${placedPieces.size - roomsPlaced} corridors/ends). " +
            "Sealed ${allUncapped.size} exits with stone."
        )
        
        spawnEntities()
    }

    private fun spawnEntities() {
        // Spawn Normal Pokémon
        for (pos in DungeonManager.pokemonSpawns) {
            val spawnInfo = selectPokemon(floorConfig.pokemonSpawns) ?: continue
            spawnPokemonEntity(pos, spawnInfo)
        }
        
        // Spawn Normal Items
        for (pos in DungeonManager.itemSpawns) {
            val itemInfo = selectItem(floorConfig.itemSpawns) ?: continue
            spawnItemEntity(pos, itemInfo)
        }
        
        // Spawn Treasure (End Floor)
        for (pos in DungeonManager.treasureSpawns) {
            val itemInfo = selectItem(config.endFloorConfig.treasure) ?: continue
            spawnItemEntity(pos, itemInfo)
        }
        
        // Spawn Boss (End Floor)
        for (pos in DungeonManager.bossSpawns) {
            val spawnInfo = selectPokemon(config.endFloorConfig.boss) ?: continue
            spawnPokemonEntity(pos, spawnInfo)
        }
        
        // Spawn Minions (End Floor)
        for (pos in DungeonManager.minionSpawns) {
            val spawnInfo = selectPokemon(config.endFloorConfig.minion) ?: continue
            spawnPokemonEntity(pos, spawnInfo)
        }
    }
    
    private fun selectPokemon(list: List<net.drachi.cdde.data.PokemonSpawnEntry>): net.drachi.cdde.data.PokemonSpawnEntry? {
        if (list.isEmpty()) return null
        val totalWeight = list.sumOf { it.weight }
        if (totalWeight <= 0) return list.randomOrNull()
        var r = level.random.nextInt(totalWeight)
        for (entry in list) {
            r -= entry.weight
            if (r < 0) return entry
        }
        return list.last()
    }
    
    private fun selectItem(list: List<net.drachi.cdde.data.ItemSpawnEntry>): net.drachi.cdde.data.ItemSpawnEntry? {
        if (list.isEmpty()) return null
        val totalWeight = list.sumOf { it.weight }
        if (totalWeight <= 0) return list.randomOrNull()
        var r = level.random.nextInt(totalWeight)
        for (entry in list) {
            r -= entry.weight
            if (r < 0) return entry
        }
        return list.last()
    }
    
    private fun spawnPokemonEntity(pos: BlockPos, entry: net.drachi.cdde.data.PokemonSpawnEntry) {
        try {
            val speciesName = entry.pokemon.split(":").lastOrNull() ?: entry.pokemon
            val species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(speciesName) ?: return
            
            val levelValue = if (entry.maxLevel > entry.minLevel) {
                level.random.nextInt(entry.maxLevel - entry.minLevel + 1) + entry.minLevel
            } else {
                entry.minLevel
            }
            
            val pokemon = species.create(levelValue)
            val entity = com.cobblemon.mod.common.entity.pokemon.PokemonEntity(level, pokemon)
            entity.setPos(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
            entity.addTag("cdde_spawned")
            level.addFreshEntity(entity)
            
            CobblemonDungeonDungeonsEngine.logger.info("Spawned ${pokemon.species.name} (Lvl $levelValue) at $pos")
        } catch (e: Exception) {
            CobblemonDungeonDungeonsEngine.logger.error("Failed to spawn pokemon: ${entry.pokemon}", e)
        }
    }
    
    private fun spawnItemEntity(pos: BlockPos, entry: net.drachi.cdde.data.ItemSpawnEntry) {
        try {
            val item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(entry.item))
            if (item == net.minecraft.world.item.Items.AIR) return
            
            val amount = if (entry.maxAmount > entry.minAmount) {
                level.random.nextInt(entry.maxAmount - entry.minAmount + 1) + entry.minAmount
            } else {
                entry.minAmount
            }
            
            val itemStack = net.minecraft.world.item.ItemStack(item, amount)
            val entity = net.minecraft.world.entity.item.ItemEntity(level, pos.x + 0.5, pos.y.toDouble() + 0.5, pos.z + 0.5, itemStack)
            entity.addTag("cdde_spawned")
            level.addFreshEntity(entity)
            
            CobblemonDungeonDungeonsEngine.logger.info("Spawned ${amount}x ${entry.item} at $pos")
        } catch (e: Exception) {
            CobblemonDungeonDungeonsEngine.logger.error("Failed to spawn item: ${entry.item}", e)
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  ROOM SCATTERING
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Attempts to place rooms randomly on the grid. Each room:
     *  1. Picks a random room template from the pool.
     *  2. Calculates its grid footprint in cells.
     *  3. Checks for collisions with existing rooms.
     *  4. Blocks the footprint and projects ROOM_EXIT cells for each jigsaw.
     *
     * @return Number of rooms successfully placed.
     */
    private fun scatterRooms(grid: DungeonGrid, roomIds: List<net.minecraft.resources.ResourceLocation>, mapper: TemplateMapper, maxRooms: Int): Int {
        var roomsPlaced = 0
        var tries = 0

        while (roomsPlaced < maxRooms && tries < 50) {
            tries++
            val roomId = roomIds[level.random.nextInt(roomIds.size)]
            val t = DungeonManager.loadedTemplates[roomId] ?: continue
            val rotations = Rotation.values().toList().shuffled(kotlin.random.Random(level.random.nextLong()))
            
            var roomPlacedThisTry = false
            for (rot in rotations) {
                val size = t.getSize(rot)
                val wCells = DungeonGrid.toOdd(kotlin.math.ceil(size.x / DungeonGrid.CELL_SIZE.toDouble()).toInt().coerceAtLeast(1))
                val dCells = DungeonGrid.toOdd(kotlin.math.ceil(size.z / DungeonGrid.CELL_SIZE.toDouble()).toInt().coerceAtLeast(1))

                val maxRx = grid.width - 2 - wCells
                val maxRz = grid.height - 2 - dCells
                if (maxRx < 2 || maxRz < 2) continue

                val rx = (level.random.nextInt((maxRx - 2) / 2 + 1)) * 2 + 2
                val rz = (level.random.nextInt((maxRz - 2) / 2 + 1)) * 2 + 2

                val localJigsaws = TemplateMapper.parseJigsaws(t, rot)
                
                val actualRx = rx
                val actualRz = rz
                val actualWCells = wCells
                val actualDCells = dCells

                if (actualRx < 1 || actualRz < 1 || actualRx + actualWCells >= grid.width - 1 || actualRz + actualDCells >= grid.height - 1) continue

                var blocked = false
                for (x in actualRx until actualRx + actualWCells) {
                    for (z in actualRz until actualRz + actualDCells) {
                        if (grid.isBlocked(x, z) || grid.cellState(x, z) == DungeonGrid.CellState.ROOM_EXIT) {
                            blocked = true
                        }
                    }
                }
                if (blocked) continue
                
                val settings = StructurePlaceSettings().setRotation(rot)
                val zeroBox = t.getBoundingBox(settings, BlockPos.ZERO)
                val roomPos = origin.offset(
                    actualRx * DungeonGrid.CELL_SIZE - zeroBox.minX(),
                    0,
                    actualRz * DungeonGrid.CELL_SIZE - zeroBox.minZ()
                )
                val hasHazard = TemplateMapper.detectHazard(t)
                val roomPiece = StructurePiece(t, roomPos, rot, PieceType.ROOM, hasHazard)

                var exitsValid = true
                val projectedExits = mutableListOf<Pair<Int, Int>>()
                for (jig in localJigsaws) {
                    val relX = roomPos.x - origin.x + jig.pos.x
                    val relZ = roomPos.z - origin.z + jig.pos.z
                    
                    val px = Math.floorDiv(relX + jig.facing.stepX, DungeonGrid.CELL_SIZE)
                    val pz = Math.floorDiv(relZ + jig.facing.stepZ, DungeonGrid.CELL_SIZE)
                    
                    if (!grid.inBounds(px, pz)) {
                        exitsValid = false
                        break
                    }
                    projectedExits.add(px to pz)
                }
                if (!exitsValid) continue

                // If generateHazardSeas is enabled, check spill safety
                if (floorConfig.generateHazardSeas) {
                    val relativeFloorY = (localJigsaws.minOfOrNull { it.pos.y } ?: 1) - 1
                    val openDirs = mapper.detectOpenDirections(t, rot, relativeFloorY)
                    if (openDirs.isNotEmpty()) {
                        val enclosed = mapper.computeEnclosedWallCells(grid)
                        if (!mapper.isPlacementSpillSafe(actualRx, actualRz, actualWCells, actualDCells, openDirs, true, enclosed)) {
                            continue // Try another rotation/scatter pos
                        }
                    }
                }
                
                // Physical intersection check to prevent room overlaps
                if (placedPieces.any { roomPiece.intersects(it) }) {
                    continue
                }

                val roomRegionId = grid.allocateRegion()
                grid.blockRoomFootprint(actualRx, actualRz, actualWCells, actualDCells, roomRegionId)
                
                placedPieces.add(roomPiece)
                roomPieces.add(roomPiece)
                
                for ((px, pz) in projectedExits) {
                    grid.projectRoomExit(px, pz, roomRegionId)
                }
                
                roomsPlaced++
                roomPlacedThisTry = true
                break
            }
        }

        CobblemonDungeonDungeonsEngine.logger.info("Room scatter: placed $roomsPlaced rooms in $tries attempts.")
        return roomsPlaced
    }

    // ═════════════════════════════════════════════════════════════════════
    //  RENDERING
    // ═════════════════════════════════════════════════════════════════════

    /** Renders a single [StructurePiece] into the world with palette processing. */
    private fun renderPiece(piece: StructurePiece) {
        val configuredHazard = if (floorConfig.hazards.isNotEmpty()) floorConfig.hazards[0] else "minecraft:water"
        val hazardState = when {
            configuredHazard.contains("lava") -> ModBlocks.HAZARD_LAVA.defaultBlockState()
            configuredHazard.contains("void") -> ModBlocks.HAZARD_VOID.defaultBlockState()
            else -> ModBlocks.HAZARD_WATER.defaultBlockState()
        }

        val settings = StructurePlaceSettings()
            .setRotation(piece.rotation)
            .setMirror(Mirror.NONE)
            .setIgnoreEntities(true)
            .setKnownShape(true)
            .addProcessor(JigsawReplacementProcessor.INSTANCE)
            .addProcessor(
                PaletteProcessor(
                    paletteAMap = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(floorConfig.paletteA)).defaultBlockState(),
                    paletteBMap = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(floorConfig.paletteB)).defaultBlockState(),
                    paletteCMap = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(floorConfig.paletteC)).defaultBlockState(),
                    paletteDMap = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(floorConfig.paletteD)).defaultBlockState(),
                    hazardMap = hazardState,
                    hazardPositions = hazardPositions
                )
            )

        piece.template.placeInWorld(level, piece.pos, BlockPos.ZERO, settings, level.random, 2)
        
        // Process Spawn Blocks
        val isRoomOrEnd = piece.type == PieceType.ROOM || piece.type == PieceType.END
        
        fun isSafeSpawn(pos: net.minecraft.core.BlockPos): Boolean {
            val belowState = level.getBlockState(pos.below())
            val isHazard = belowState.block == net.drachi.cdde.registry.ModBlocks.HAZARD || 
                           belowState.block == net.drachi.cdde.registry.ModBlocks.HAZARD_LAVA ||
                           belowState.block == net.drachi.cdde.registry.ModBlocks.HAZARD_VOID ||
                           belowState.block == net.drachi.cdde.registry.ModBlocks.HAZARD_WATER ||
                           belowState.fluidState.isSource
            return !isHazard && !belowState.isAir
        }

        val pSpawns = piece.template.filterBlocks(piece.pos, settings, ModBlocks.POKEMON_SPAWN)
        for (info in pSpawns) {
            level.setBlock(info.pos, Blocks.AIR.defaultBlockState(), 2)
            if (isRoomOrEnd && isSafeSpawn(info.pos)) {
                DungeonManager.pokemonSpawns.add(info.pos)
                CobblemonDungeonDungeonsEngine.logger.info("Registered Pokémon spawn at ${info.pos.x}, ${info.pos.y}, ${info.pos.z}")
            }
        }
        
        val iSpawns = piece.template.filterBlocks(piece.pos, settings, ModBlocks.ITEM_SPAWN)
        for (info in iSpawns) {
            level.setBlock(info.pos, Blocks.AIR.defaultBlockState(), 2)
            if (isRoomOrEnd && isSafeSpawn(info.pos)) {
                DungeonManager.itemSpawns.add(info.pos)
                CobblemonDungeonDungeonsEngine.logger.info("Registered Item spawn at ${info.pos.x}, ${info.pos.y}, ${info.pos.z}")
            }
        }
        
        val tSpawns = piece.template.filterBlocks(piece.pos, settings, ModBlocks.TREASURE_SPAWN)
        for (info in tSpawns) {
            level.setBlock(info.pos, Blocks.AIR.defaultBlockState(), 2)
            if (isSafeSpawn(info.pos)) {
                DungeonManager.treasureSpawns.add(info.pos)
                CobblemonDungeonDungeonsEngine.logger.info("Registered Treasure spawn at ${info.pos.x}, ${info.pos.y}, ${info.pos.z}")
            }
        }
        
        val bSpawns = piece.template.filterBlocks(piece.pos, settings, ModBlocks.BOSS_SPAWN)
        for (info in bSpawns) {
            level.setBlock(info.pos, Blocks.AIR.defaultBlockState(), 2)
            if (isSafeSpawn(info.pos)) {
                DungeonManager.bossSpawns.add(info.pos)
                CobblemonDungeonDungeonsEngine.logger.info("Registered Boss spawn at ${info.pos.x}, ${info.pos.y}, ${info.pos.z}")
            }
        }
        
        val mSpawns = piece.template.filterBlocks(piece.pos, settings, ModBlocks.MINION_SPAWN)
        for (info in mSpawns) {
            level.setBlock(info.pos, Blocks.AIR.defaultBlockState(), 2)
            DungeonManager.minionSpawns.add(info.pos)
            CobblemonDungeonDungeonsEngine.logger.info("Registered Minion spawn at ${info.pos.x}, ${info.pos.y}, ${info.pos.z}")
        }
        
        val eSpawns = piece.template.filterBlocks(piece.pos, settings, ModBlocks.END_STAIR_SPAWN)
        for (info in eSpawns) {
            level.setBlock(info.pos, Blocks.AIR.defaultBlockState(), 2)
            if (isSafeSpawn(info.pos)) {
                DungeonManager.endStairSpawns.add(info.pos)
                CobblemonDungeonDungeonsEngine.logger.info("Registered End Stair spawn at ${info.pos.x}, ${info.pos.y}, ${info.pos.z}")
            }
        }
        
        val plSpawns = piece.template.filterBlocks(piece.pos, settings, ModBlocks.PLAYER_SPAWN)
        for (info in plSpawns) {
            level.setBlock(info.pos, Blocks.AIR.defaultBlockState(), 2)
            if (isSafeSpawn(info.pos)) {
                customPlayerSpawns.add(info.pos)
                CobblemonDungeonDungeonsEngine.logger.info("Registered Player start spawn at ${info.pos.x}, ${info.pos.y}, ${info.pos.z}")
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  POST-PROCESSING
    // ═════════════════════════════════════════════════════════════════════

    /** Places barrier blocks below any hazard positions to prevent falling through void. Also places hazard walls above hazards to prevent jumping. */
    private fun postProcessHazards() {
        for (pos in hazardPositions) {
            val below = pos.below()
            if (level.getBlockState(below).isAir) {
                level.setBlock(below, Blocks.BARRIER.defaultBlockState(), 3)
            }
            
            val above = pos.above()
            val stateAtPos = level.getBlockState(above)
            if (stateAtPos.isAir) {
                val hazardState = level.getBlockState(pos)
                val wallState = when (hazardState.block) {
                    net.drachi.cdde.registry.ModBlocks.HAZARD_WATER -> net.drachi.cdde.registry.ModBlocks.HAZARD_WALL_WATER.defaultBlockState()
                    net.drachi.cdde.registry.ModBlocks.HAZARD_LAVA -> net.drachi.cdde.registry.ModBlocks.HAZARD_WALL_LAVA.defaultBlockState()
                    net.drachi.cdde.registry.ModBlocks.HAZARD_VOID -> net.drachi.cdde.registry.ModBlocks.HAZARD_WALL_VOID.defaultBlockState()
                    else -> net.drachi.cdde.registry.ModBlocks.HAZARD_WALL_WATER.defaultBlockState()
                }
                for (dy in 1..50) {
                    val wPos = pos.offset(0, dy, 0)
                    val state = level.getBlockState(wPos)
                    if (state.isAir) {
                        level.setBlock(wPos, wallState, 3)
                    } else if (state.isSolidRender(level, wPos)) {
                        break
                    }
                }
            }
        }
    }

    /**
     * Last-resort sealing for room exits that couldn't be capped with end pieces.
     * Places a stone brick wall covering the standard hallway cross-section.
     */
    private fun sealRemainingExits(uncappedExits: List<ParsedJigsaw>) {
        for (jig in uncappedExits) {
            sealOpenExit(jig.pos, jig.facing)
        }
    }

    /**
     * Fills a [DungeonGrid.CELL_SIZE]-wide × 5-high wall at the given exit position.
     * Used only when no end-piece template is available.
     */
    private fun sealOpenExit(pos: BlockPos, facing: Direction) {
        val sealOrigin = pos.relative(facing)
        val right = if (facing.axis == Direction.Axis.Y) Direction.NORTH else facing.clockWise
        val up = if (facing.axis == Direction.Axis.Y) Direction.EAST else Direction.UP

        val halfWidth = DungeonGrid.CELL_SIZE / 2
        val height = 5

        for (x in -halfWidth..halfWidth) {
            for (y in 0 until height) {
                val blockPos = sealOrigin
                    .relative(right, x)
                    .relative(up, y - 1)
                level.setBlock(blockPos, Blocks.STONE_BRICKS.defaultBlockState(), 3)
            }
        }
    }


    private fun placeStairs(roomPieces: List<StructurePiece>, theme: String) {
        if (DungeonManager.endStairSpawns.isNotEmpty()) {
            this.stairPosition = DungeonManager.endStairSpawns.first()
            CobblemonDungeonDungeonsEngine.logger.info("Using configured END_STAIR_SPAWN at ${this.stairPosition}")
            return
        }

        val shuffledRooms = roomPieces.shuffled(kotlin.random.Random(level.random.nextLong()))
        for (room in shuffledRooms) {
            val minJigY = room.parsedJigsaws.minOfOrNull { it.pos.y } ?: room.pos.y
            val floorY = minJigY - 1
            val box = room.boundingBox
            val validSpots = mutableListOf<BlockPos>()
            
            for (x in box.minX()..box.maxX() - 2) {
                for (z in box.minZ()..box.maxZ() - 2) {
                    var isValid = true
                    for (dx in 0..2) {
                        for (dz in 0..2) {
                            val pos = BlockPos(x + dx, floorY, z + dz)
                            val floorState = level.getBlockState(pos)
                            val feetState = level.getBlockState(pos.above())
                            val headState = level.getBlockState(pos.above(2))
                            
                            val isFloorSolid = !floorState.isAir && floorState.blocksMotion()
                            val isHazard = floorState.block == ModBlocks.HAZARD || 
                                           floorState.fluidState.isSource ||
                                           feetState.block == ModBlocks.HAZARD || 
                                           feetState.fluidState.isSource
                            
                            val isFeetClear = feetState.isAir || !feetState.blocksMotion()
                            val isHeadClear = headState.isAir || !headState.blocksMotion()
                            
                            // Ensure no overlap with hallways/junctions/ends
                            val overlapsOther = placedPieces.any { it.type != PieceType.ROOM && it.boundingBox.isInside(pos) }
                            
                            if (!isFloorSolid || isHazard || !isFeetClear || !isHeadClear || overlapsOther) {
                                isValid = false
                                break
                            }
                        }
                        if (!isValid) break
                    }
                    if (isValid) {
                        validSpots.add(BlockPos(x, floorY, z))
                    }
                }
            }
            if (validSpots.isNotEmpty()) {
                val spot = validSpots[level.random.nextInt(validSpots.size)]
                val stairTemplates = DungeonManager.availableStairs[theme] ?: emptyList()
                
                if (stairTemplates.isNotEmpty()) {
                    val stairLoc = stairTemplates[level.random.nextInt(stairTemplates.size)]
                    val t = DungeonManager.loadedTemplates[stairLoc] ?: continue
                    val rot = Rotation.values()[level.random.nextInt(Rotation.values().size)]
                    val settings = StructurePlaceSettings()
                        .setRotation(rot)
                        .setIgnoreEntities(true)
                    t.placeInWorld(level, spot, BlockPos.ZERO, settings, level.random, 2)
                    CobblemonDungeonDungeonsEngine.logger.info("Placed stairs template '$stairLoc' at $spot")
                    
                    // Track position for step-on detection: center of the 3x3 template
                    this.stairPosition = spot.offset(1, 0, 1)
                } else {
                    val baseState = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(floorConfig.stairBaseBlock)).defaultBlockState()
                    val stepBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(floorConfig.stairStepBlock))
                    val stairBlock = stepBlock as? net.minecraft.world.level.block.StairBlock
                    
                    val validDirs = DungeonGrid.CARDINALS.filter { dir ->
                        val approachPos = when (dir) {
                            net.minecraft.core.Direction.SOUTH -> spot.offset(1, 0, -1)
                            net.minecraft.core.Direction.NORTH -> spot.offset(1, 0, 3)
                            net.minecraft.core.Direction.EAST -> spot.offset(-1, 0, 1)
                            net.minecraft.core.Direction.WEST -> spot.offset(3, 0, 1)
                            else -> spot
                        }
                        val floorState = level.getBlockState(approachPos)
                        val feetState = level.getBlockState(approachPos.above())
                        val headState = level.getBlockState(approachPos.above(2))
                        val isHazard = floorState.block == net.drachi.cdde.registry.ModBlocks.HAZARD || 
                                       floorState.fluidState.isSource ||
                                       feetState.block == net.drachi.cdde.registry.ModBlocks.HAZARD || 
                                       feetState.fluidState.isSource
                        !isHazard && !floorState.isAir && floorState.blocksMotion() && 
                        (feetState.isAir || !feetState.blocksMotion()) && 
                        (headState.isAir || !headState.blocksMotion())
                    }
                    val climbDir = if (validDirs.isNotEmpty()) {
                        validDirs[level.random.nextInt(validDirs.size)]
                    } else {
                        DungeonGrid.CARDINALS[level.random.nextInt(DungeonGrid.CARDINALS.size)]
                    }
                    // Center step dx, dz formula for stairPosition (u = 2, v = 1)
                    val targetDx = when (climbDir) {
                        Direction.SOUTH -> 1
                        Direction.NORTH -> 1
                        Direction.EAST -> 2
                        Direction.WEST -> 0
                        else -> 1
                    }
                    val targetDz = when (climbDir) {
                        Direction.SOUTH -> 2
                        Direction.NORTH -> 0
                        Direction.EAST -> 1
                        Direction.WEST -> 1
                        else -> 2
                    }
                    
                    if (config.stairDirection == StairDirection.UP) {
                        for (u in 0..2) {
                            for (v in 0..2) {
                                val (dx, dz) = when (climbDir) {
                                    Direction.SOUTH -> Pair(v, u)
                                    Direction.NORTH -> Pair(2 - v, 2 - u)
                                    Direction.EAST -> Pair(u, 2 - v)
                                    Direction.WEST -> Pair(2 - u, v)
                                    else -> Pair(v, u)
                                }
                                
                                val bpos = spot.offset(dx, 1, dz)
                                
                                // A full 3-blocks-wide staircase
                                if (u == 0) {
                                    val st = (stairBlock ?: Blocks.STONE_BRICK_STAIRS).defaultBlockState().setValue(StairBlock.FACING, climbDir)
                                    level.setBlock(bpos, st, 3)
                                    level.setBlock(bpos.above(), Blocks.AIR.defaultBlockState(), 3)
                                    level.setBlock(bpos.above(2), Blocks.AIR.defaultBlockState(), 3)
                                } else if (u == 1) {
                                    level.setBlock(bpos, baseState, 3)
                                    val st = (stairBlock ?: Blocks.STONE_BRICK_STAIRS).defaultBlockState().setValue(StairBlock.FACING, climbDir)
                                    level.setBlock(bpos.above(), st, 3)
                                    level.setBlock(bpos.above(2), Blocks.AIR.defaultBlockState(), 3)
                                    level.setBlock(bpos.above(3), Blocks.AIR.defaultBlockState(), 3)
                                } else {
                                    level.setBlock(bpos, baseState, 3)
                                    level.setBlock(bpos.above(), baseState, 3)
                                    level.setBlock(bpos.above(2), baseState, 3)
                                    level.setBlock(bpos.above(3), Blocks.AIR.defaultBlockState(), 3)
                                    level.setBlock(bpos.above(4), Blocks.AIR.defaultBlockState(), 3)
                                }
                            }
                        }
                        
                        this.stairPosition = spot.offset(targetDx, 3, targetDz)
                    } else { // DOWN (Sunken Pit)
                        // 1. Build the stair trench steps inside 3x3 (3-blocks-wide)
                        // Shifted up by 1 block so the top step starts exactly at floorY.
                        // Bottom step at floorY - 2 is also a stair block.
                        for (u in 0..2) {
                            for (v in 0..2) {
                                val (dx, dz) = when (climbDir) {
                                    Direction.SOUTH -> Pair(v, u)
                                    Direction.NORTH -> Pair(2 - v, 2 - u)
                                    Direction.EAST -> Pair(u, 2 - v)
                                    Direction.WEST -> Pair(2 - u, v)
                                    else -> Pair(v, u)
                                }
                                
                                val bpos = spot.offset(dx, 0, dz)
                                
                                val st = (stairBlock ?: Blocks.STONE_BRICK_STAIRS).defaultBlockState().setValue(StairBlock.FACING, climbDir.opposite)
                                val stairY = -u
                                val baseY = stairY - 1
                                
                                // Place base block below the stair
                                level.setBlock(bpos.offset(0, baseY, 0), baseState, 3)
                                // Place stair block
                                level.setBlock(bpos.offset(0, stairY, 0), st, 3)
                                
                                // Clear blocks above the stair to prevent occlusion
                                for (dy in (stairY + 1)..2) {
                                    level.setBlock(bpos.offset(0, dy, 0), Blocks.AIR.defaultBlockState(), 3)
                                }
                            }
                        }
                        
                        // 2. Build solid walls surrounding the trench to prevent seeing the void.
                        for (u in -1..3) {
                            for (v in -1..3) {
                                if (u in 0..2 && v in 0..2) continue // Skip the stairs pit itself!
                                
                                val (dx, dz) = when (climbDir) {
                                    Direction.SOUTH -> Pair(v, u)
                                    Direction.NORTH -> Pair(2 - v, 2 - u)
                                    Direction.EAST -> Pair(u, 2 - v)
                                    Direction.WEST -> Pair(2 - u, v)
                                    else -> Pair(v, u)
                                }
                                
                                // Place solid base blocks from floorY - 1 down to floorY - 5
                                for (dy in -5..-1) {
                                    val wallPos = spot.offset(dx, dy, dz)
                                    level.setBlock(wallPos, baseState, 3)
                                }
                            }
                        }
                        
                        this.stairPosition = spot.offset(targetDx, -2, targetDz)
                    }
                    CobblemonDungeonDungeonsEngine.logger.info("Placed 3x3 recognizable fallback stairs at $spot climbing $climbDir. Target stand pos is ${this.stairPosition}")
                }
                return
            }
        }
        CobblemonDungeonDungeonsEngine.logger.warn("Could not find a valid 3x3 spot to place stairs in any room!")
    }

    private fun findCeilingYAndBlock(piece: StructurePiece): Pair<Int, BlockState>? {
        val settings = StructurePlaceSettings().setRotation(piece.rotation)
        val blocks = piece.template.filterBlocks(BlockPos.ZERO, settings, null as net.minecraft.world.level.block.Block?)
        val ceilingBlocks = blocks.filter {
            it.pos.y >= 3 && !it.state.isAir && it.state.block != Blocks.JIGSAW && it.state.fluidState.isEmpty
        }
        if (ceilingBlocks.isEmpty()) return null
        val maxY = ceilingBlocks.maxOf { it.pos.y }
        val state = ceilingBlocks.first { it.pos.y == maxY }.state
        val worldY = piece.pos.y + maxY
        return Pair(worldY, state)
    }

    private fun renderHazardSeas(grid: DungeonGrid, mapper: TemplateMapper) {
        if (!floorConfig.generateHazardSeas) return
        val hazardSeaCells = mutableSetOf<Pair<Int, Int>>()
        val queue = java.util.ArrayDeque<Pair<Int, Int>>()
        
        for (piece in placedPieces) {
            val relativeFloorY = (piece.template.filterBlocks(BlockPos.ZERO, StructurePlaceSettings().setRotation(piece.rotation), Blocks.JIGSAW).minOfOrNull { it.pos.y } ?: 1) - 1
            val openDirs = mapper.detectOpenDirections(piece.template, piece.rotation, relativeFloorY)
            if (openDirs.isNotEmpty()) {
                val cx = Math.floorDiv(piece.pos.x - origin.x, DungeonGrid.CELL_SIZE)
                val cz = Math.floorDiv(piece.pos.z - origin.z, DungeonGrid.CELL_SIZE)
                val size = piece.template.getSize(piece.rotation)
                val wCells = kotlin.math.ceil(size.x / DungeonGrid.CELL_SIZE.toDouble()).toInt().coerceAtLeast(1)
                val dCells = kotlin.math.ceil(size.z / DungeonGrid.CELL_SIZE.toDouble()).toInt().coerceAtLeast(1)
                for (dir in openDirs) {
                    val adjCells = mapper.getAdjacentCellsForFace(cx, cz, wCells, dCells, dir)
                    for (p in adjCells) {
                        if (enclosedWallCells.contains(p)) {
                            if (hazardSeaCells.add(p)) {
                                queue.add(p)
                            }
                        }
                    }
                }
            }
        }
        
        while (queue.isNotEmpty()) {
            val (cx, cz) = queue.poll()
            for (dir in Direction.values()) {
                if (dir.axis.isVertical) continue
                val nx = cx + dir.stepX
                val nz = cz + dir.stepZ
                val p = Pair(nx, nz)
                if (enclosedWallCells.contains(p)) {
                    if (hazardSeaCells.add(p)) {
                        queue.add(p)
                    }
                }
            }
        }
        
        if (hazardSeaCells.isEmpty()) return
        
        val configuredHazard = if (floorConfig.hazards.isNotEmpty()) floorConfig.hazards[0] else "minecraft:water"
        val hazardState = when {
            configuredHazard.contains("lava") -> ModBlocks.HAZARD_LAVA.defaultBlockState()
            configuredHazard.contains("void") -> ModBlocks.HAZARD_VOID.defaultBlockState()
            else -> ModBlocks.HAZARD_WATER.defaultBlockState()
        }
        val floorState = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(floorConfig.stairBaseBlock)).defaultBlockState()
        val lightState = Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 15)
        
        for ((cx, cz) in hazardSeaCells) {
            val worldStartX = origin.x + cx * DungeonGrid.CELL_SIZE
            val worldStartZ = origin.z + cz * DungeonGrid.CELL_SIZE
            
            val adjacentPieces = placedPieces.filter { piece ->
                val pcx = Math.floorDiv(piece.pos.x - origin.x, DungeonGrid.CELL_SIZE)
                val pcz = Math.floorDiv(piece.pos.z - origin.z, DungeonGrid.CELL_SIZE)
                val size = piece.template.getSize(piece.rotation)
                val pw = kotlin.math.ceil(size.x / DungeonGrid.CELL_SIZE.toDouble()).toInt()
                val pd = kotlin.math.ceil(size.z / DungeonGrid.CELL_SIZE.toDouble()).toInt()
                val pxRange = (pcx - 1)..(pcx + pw)
                val pzRange = (pcz - 1)..(pcz + pd)
                cx in pxRange && cz in pzRange
            }
            
            val ceilings = adjacentPieces.mapNotNull { findCeilingYAndBlock(it) }
            val hasCeiling = ceilings.isNotEmpty()
            val avgCeilingY = if (hasCeiling) ceilings.map { it.first }.average().toInt() else 0
            val ceilingState = if (hasCeiling) ceilings.groupBy { it.second }.maxByOrNull { it.value.size }?.key ?: Blocks.STONE.defaultBlockState() else Blocks.AIR.defaultBlockState()
            
            for (bx in 0 until DungeonGrid.CELL_SIZE) {
                for (bz in 0 until DungeonGrid.CELL_SIZE) {
                    val wx = worldStartX + bx
                    val wz = worldStartZ + bz
                    
                    level.setBlock(BlockPos(wx, origin.y - 3, wz), floorState, 3)
                    for (wy in (origin.y - 2)..origin.y) {
                        level.setBlock(BlockPos(wx, wy, wz), hazardState, 3)
                        hazardPositions.add(BlockPos(wx, wy, wz))
                    }
                    
                    if (hasCeiling) {
                        for (wy in (origin.y + 1) until avgCeilingY) {
                            level.setBlock(BlockPos(wx, wy, wz), Blocks.AIR.defaultBlockState(), 3)
                        }
                        level.setBlock(BlockPos(wx, avgCeilingY, wz), ceilingState, 3)
                        level.setBlock(BlockPos(wx, avgCeilingY + 1, wz), Blocks.STONE.defaultBlockState(), 3)
                    } else {
                        for (wy in (origin.y + 1)..(origin.y + 5)) {
                            level.setBlock(BlockPos(wx, wy, wz), Blocks.AIR.defaultBlockState(), 3)
                        }
                    }
                }
            }
            
            if (hasCeiling) {
                for (bx in listOf(1, 3, 5)) {
                    for (bz in listOf(1, 3, 5)) {
                        val wx = worldStartX + bx
                        val wz = worldStartZ + bz
                        level.setBlock(BlockPos(wx, avgCeilingY - 1, wz), lightState, 3)
                    }
                }
            }
        }
        CobblemonDungeonDungeonsEngine.logger.info("Rendered hazard sea for ${hazardSeaCells.size} grid cells.")
    }
}



