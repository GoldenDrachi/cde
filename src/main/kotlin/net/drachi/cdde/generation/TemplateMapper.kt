package net.drachi.cdde.generation

import net.drachi.cdde.CobblemonDungeonDungeonsEngine
import net.drachi.cdde.data.DungeonConfig
import net.drachi.cdde.data.DungeonManager
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import kotlin.random.asKotlinRandom
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.registries.BuiltInRegistries

// ═════════════════════════════════════════════════════════════════════════════
//  DATA CLASSES
// ═════════════════════════════════════════════════════════════════════════════

data class ParsedTemplate(
    val res: ResourceLocation,
    val template: StructureTemplate,
    val jigsaws: List<ParsedJigsaw>,
    val type: Category,
    val hasHazard: Boolean
) {
    enum class Category { ROOM, CROSS, T_JUNCTION, CORNER, STRAIGHT, END, UNKNOWN }
}

// ═════════════════════════════════════════════════════════════════════════════
//  TEMPLATE MAPPER — Logical-Grid Jigsaw Raycaster
// ═════════════════════════════════════════════════════════════════════════════

class TemplateMapper(
    private val origin: BlockPos,
    private val random: RandomSource
) {
    private val straights = mutableListOf<ParsedTemplate>()
    private val crosses = mutableListOf<ParsedTemplate>()
    private val ts = mutableListOf<ParsedTemplate>()
    private val corners = mutableListOf<ParsedTemplate>()
    private val ends = mutableListOf<ParsedTemplate>()

    fun loadPools(hallwayIds: List<ResourceLocation>, endIds: List<ResourceLocation>) {
        straights.clear(); crosses.clear(); ts.clear(); corners.clear(); ends.clear()

        for (id in hallwayIds) {
            val t = DungeonManager.loadedTemplates[id] ?: continue
            val jigsaws = parseJigsaws(t)

            var effectiveJigsaws = if (jigsaws.size == 1) {
                listOf(jigsaws[0], ParsedJigsaw(jigsaws[0].pos, jigsaws[0].facing.opposite))
            } else {
                jigsaws
            }

            val cat = when (effectiveJigsaws.size) {
                4 -> ParsedTemplate.Category.CROSS
                3 -> ParsedTemplate.Category.T_JUNCTION
                2 -> {
                    val p1 = effectiveJigsaws[0].pos
                    val p2 = effectiveJigsaws[1].pos
                    val dx = p2.x - p1.x
                    val dz = p2.z - p1.z

                    if (dx == 0 && dz != 0) {
                        val expectedP2Facing = if (dz > 0) Direction.SOUTH else Direction.NORTH
                        effectiveJigsaws = listOf(
                            ParsedJigsaw(p1, expectedP2Facing.opposite),
                            ParsedJigsaw(p2, expectedP2Facing)
                        )
                        CobblemonDungeonDungeonsEngine.logger.info("Auto-Corrected Z-Straight: $id. p1=$p1->${expectedP2Facing.opposite}, p2=$p2->$expectedP2Facing")
                        ParsedTemplate.Category.STRAIGHT
                    } else if (dz == 0 && dx != 0) {
                        val expectedP2Facing = if (dx > 0) Direction.EAST else Direction.WEST
                        effectiveJigsaws = listOf(
                            ParsedJigsaw(p1, expectedP2Facing.opposite),
                            ParsedJigsaw(p2, expectedP2Facing)
                        )
                        CobblemonDungeonDungeonsEngine.logger.info("Auto-Corrected X-Straight: $id. p1=$p1->${expectedP2Facing.opposite}, p2=$p2->$expectedP2Facing")
                        ParsedTemplate.Category.STRAIGHT
                    } else if (dx == 0 && dz == 0) {
                        // 1-block straight (virtual jigsaws handled above) or corner on same block
                        if (effectiveJigsaws[0].facing.axis == effectiveJigsaws[1].facing.axis)
                            ParsedTemplate.Category.STRAIGHT
                        else
                            ParsedTemplate.Category.CORNER
                    } else {
                        // Diagonal / Corner
                        ParsedTemplate.Category.CORNER
                    }
                }
                else -> ParsedTemplate.Category.UNKNOWN
            }

            val hasHazard = detectHazard(t)
            val pt = ParsedTemplate(id, t, effectiveJigsaws, cat, hasHazard)
            when (cat) {
                ParsedTemplate.Category.CROSS -> crosses.add(pt)
                ParsedTemplate.Category.T_JUNCTION -> ts.add(pt)
                ParsedTemplate.Category.CORNER -> corners.add(pt)
                ParsedTemplate.Category.STRAIGHT -> straights.add(pt)
                else -> CobblemonDungeonDungeonsEngine.logger.warn("Skipped un-categorizable template: $id")
            }
        }

        for (id in endIds) {
            val t = DungeonManager.loadedTemplates[id] ?: continue
            val jigsaws = parseJigsaws(t)
            if (jigsaws.size == 1) {
                val hasHazard = detectHazard(t)
                ends.add(ParsedTemplate(id, t, jigsaws, ParsedTemplate.Category.END, hasHazard))
            }
        }

        CobblemonDungeonDungeonsEngine.logger.info(
            "Template pools loaded: ${straights.size} straights, ${corners.size} corners, " +
            "${ts.size} T-junctions, ${crosses.size} crosses, ${ends.size} ends."
        )
    }

    // ═════════════════════════════════════════════════════════════════════
    //  PHASE 2: MAP NODES (Grid-Based Placement for Anchors)
    // ═════════════════════════════════════════════════════════════════════

    fun mapNodes(
        grid: DungeonGrid,
        placedPieces: MutableList<StructurePiece>,
        config: DungeonConfig,
        floorConfig: net.drachi.cdde.data.FloorConfig,
        enclosedWallCells: Set<Pair<Int, Int>>
    ): MutableMap<Long, StructurePiece> {
        val nodeMap = mutableMapOf<Long, StructurePiece>()
        var placed = 0

        val roomPieces = placedPieces.filter { it.type == PieceType.ROOM }

        for (x in 0 until grid.width) {
            for (z in 0 until grid.height) {
                val state = grid.cellState(x, z)
                if (state != DungeonGrid.CellState.CORRIDOR && state != DungeonGrid.CellState.ROOM_EXIT) continue

                val openDirs = grid.openNeighborDirs(x, z).toMutableList()
                
                // If it is a ROOM_EXIT cell, include its connection to the room (jig.facing.opposite)
                if (state == DungeonGrid.CellState.ROOM_EXIT) {
                    val roomJigs = roomPieces.flatMap { it.parsedJigsaws }.filter { jig ->
                        val rel = jig.pos.subtract(origin)
                        val cx = Math.floorDiv(rel.x + jig.facing.stepX, DungeonGrid.CELL_SIZE)
                        val cz = Math.floorDiv(rel.z + jig.facing.stepZ, DungeonGrid.CELL_SIZE)
                        cx == x && cz == z
                    }
                    roomJigs.forEach { jig ->
                        val roomConnectionDir = jig.facing.opposite
                        if (!openDirs.contains(roomConnectionDir)) {
                            openDirs.add(roomConnectionDir)
                        }
                    }
                }

                if (openDirs.isEmpty()) continue

                val isStraight = openDirs.size == 2 && openDirs[0].axis == openDirs[1].axis
                if (isStraight) continue

                val type = when (openDirs.size) {
                    4 -> ParsedTemplate.Category.CROSS
                    3 -> ParsedTemplate.Category.T_JUNCTION
                    2 -> ParsedTemplate.Category.CORNER
                    1 -> ParsedTemplate.Category.END
                    else -> continue
                }

                val candidatePools = mutableListOf<List<ParsedTemplate>>()
                when (openDirs.size) {
                    1 -> {
                        candidatePools.add(ends)
                        candidatePools.add(corners)
                        candidatePools.add(ts)
                        candidatePools.add(crosses)
                    }
                    2 -> {
                        candidatePools.add(corners)
                        candidatePools.add(ts)
                        candidatePools.add(crosses)
                    }
                    3 -> {
                        candidatePools.add(ts)
                        candidatePools.add(crosses)
                    }
                    4 -> {
                        candidatePools.add(crosses)
                    }
                }

                // Determine preferred hazard from connected rooms
                var preferredHazard: Boolean? = null
                if (state == DungeonGrid.CellState.ROOM_EXIT) {
                    val matchingRoomJig = roomPieces.flatMap { it.parsedJigsaws }.firstOrNull { jig ->
                        val rel = jig.pos.subtract(origin)
                        val cx = Math.floorDiv(rel.x + jig.facing.stepX, DungeonGrid.CELL_SIZE)
                        val cz = Math.floorDiv(rel.z + jig.facing.stepZ, DungeonGrid.CELL_SIZE)
                        cx == x && cz == z
                    }
                    if (matchingRoomJig != null) {
                        val room = roomPieces.firstOrNull { it.parsedJigsaws.contains(matchingRoomJig) }
                        preferredHazard = room?.hasHazard
                    }
                }

                var pt: ParsedTemplate? = null
                var rotation = Rotation.NONE

                for (pool in candidatePools) {
                    if (pool.isEmpty()) continue
                    val shuffledPool = pool.shuffled(kotlin.random.Random(random.nextLong()))
                    val sortedPool = if (preferredHazard != null) {
                        shuffledPool.sortedBy { if (it.hasHazard == preferredHazard) 0 else 1 }
                    } else {
                        shuffledPool
                    }
                    for (candidate in sortedPool) {
                        val rot = findMatchingRotation(candidate, openDirs)
                        if (rot != null) {
                            val relativeFloorY = (candidate.jigsaws.minOfOrNull { it.pos.y } ?: 1) - 1
                            val openDirsOnTemplate = detectOpenDirections(candidate.template, rot, relativeFloorY)
                            if (!isPlacementSpillSafe(x, z, 1, 1, openDirsOnTemplate, config.generateHazardSeas, enclosedWallCells)) {
                                continue
                            }
                            pt = candidate
                            rotation = rot
                            break
                        }
                    }
                    if (pt != null) break
                }

                if (pt == null) {
                    CobblemonDungeonDungeonsEngine.logger.warn("No junction template found to cover open directions $openDirs at ($x, $z)")
                    continue
                }
                
                val cx = (x * DungeonGrid.CELL_SIZE) + origin.x
                val cz = (z * DungeonGrid.CELL_SIZE) + origin.z
                val settings = StructurePlaceSettings().setRotation(rotation)
                val rotatedCenter = StructureTemplate.calculateRelativePosition(settings, BlockPos(3, 0, 3))
                
                val pType = if (type == ParsedTemplate.Category.END) PieceType.END else PieceType.HALLWAY
                val worldPos = BlockPos(cx + 3 - rotatedCenter.x, origin.y, cz + 3 - rotatedCenter.z)
                val piece = StructurePiece(pt.template, worldPos, rotation, pType, pt.hasHazard)
                
                // Room collision check to prevent junctions from cutting into/replacing rooms
                if (intersectsBlockedCell(piece, grid) || intersectsRoomPhysically(piece, roomPieces)) {
                    CobblemonDungeonDungeonsEngine.logger.warn("Skipping junction at ($x, $z) because it intersects room")
                    continue
                }
                
                CobblemonDungeonDungeonsEngine.logger.info("Placed Node: template=${pt.template} rot=$rotation pos=$worldPos jigsaws=${piece.parsedJigsaws.map { "${it.pos.x},${it.pos.y},${it.pos.z}:${it.facing}" }}")
                
                placedPieces.add(piece)
                nodeMap[cellKey(x, z)] = piece
                placed++
            }
        }

        CobblemonDungeonDungeonsEngine.logger.info("Node mapping complete: placed $placed nodes.")
        return nodeMap
    }

    // ═════════════════════════════════════════════════════════════════════
    //  PHASE 3: FLAWLESS LOGICAL-GRID RAYCAST WIRES
    // ═════════════════════════════════════════════════════════════════════

    val connectedJigsaws = mutableSetOf<net.minecraft.core.BlockPos>()

    fun mapWires(
        grid: DungeonGrid,
        nodeMap: Map<Long, StructurePiece>,
        roomPieces: List<StructurePiece>,
        placedPieces: MutableList<StructurePiece>,
        config: DungeonConfig,
        enclosedWallCells: Set<Pair<Int, Int>>
    ) {
        if (straights.isEmpty()) return

        // Map EVERY jigsaw to its EXACT logical grid cell.
        val anchorJigsaws = mutableMapOf<Long, MutableList<ParsedJigsaw>>()

        fun registerJigsaw(jig: ParsedJigsaw) {
            val rel = jig.pos.subtract(origin)
            // A jigsaw sits on the edge of a cell. We want the cell it belongs TO,
            // NOT the one it points INTO. Jigsaws on exact cell boundaries (e.g.,
            // x = cellX * CELL_SIZE) can be mis-assigned by floorDiv. Bias inward
            // by 1 block opposite the facing direction to guarantee correct cell.
            val biasedX = rel.x - jig.facing.stepX
            val biasedZ = rel.z - jig.facing.stepZ
            val cx = Math.floorDiv(biasedX, DungeonGrid.CELL_SIZE)
            val cz = Math.floorDiv(biasedZ, DungeonGrid.CELL_SIZE)
            
            if (grid.inBounds(cx, cz)) {
                val key = cellKey(cx, cz)
                anchorJigsaws.computeIfAbsent(key) { mutableListOf() }.add(jig)
            }
        }

        // Register Node Jigsaws
        nodeMap.values.forEach { piece -> piece.parsedJigsaws.forEach { registerJigsaw(it) } }
        // Register Room Jigsaws
        roomPieces.forEach { piece -> piece.parsedJigsaws.forEach { registerJigsaw(it) } }

        val visitedPairs = mutableSetOf<Pair<net.minecraft.core.BlockPos, net.minecraft.core.BlockPos>>()
        var wiresPlaced = 0
        var segmentsPlaced = 0

        // Iterate through all registered Grid Cells that have Jigsaws
        for ((startKey, jigsaws) in anchorJigsaws) {
            val sx = (startKey shr 32).toInt()
            val sz = startKey.toInt()

            for (startJig in jigsaws) {
                val dir = startJig.facing
                
                // Logical Grid Raycast!
                var cx = sx + dir.stepX
                var cz = sz + dir.stepZ
                var hitKey: Long? = null
                
                while (grid.inBounds(cx, cz)) {
                    val currentKey = cellKey(cx, cz)
                    
                    // Does this cell contain a Jigsaw that faces the opposite direction?
                    if (anchorJigsaws.containsKey(currentKey)) {
                        val matchingEndJig = anchorJigsaws[currentKey]?.firstOrNull { it.facing == dir.opposite }
                        if (matchingEndJig != null) {
                            hitKey = currentKey
                            break
                        }
                    }
                    
                    // If this cell is not open (e.g. wall or blocked room), we cannot traverse past it
                    if (!grid.isOpen(cx, cz)) {
                        break
                    }
                    
                    // Move forward along the grid line
                    cx += dir.stepX
                    cz += dir.stepZ
                }

                if (hitKey != null) {
                    val endJig = anchorJigsaws[hitKey]!!.first { it.facing == dir.opposite }
                    
                    // Order the two jigsaw positions consistently to avoid duplicate key issues
                    val pairKey = if (startJig.pos.compareTo(endJig.pos) <= 0) {
                        startJig.pos to endJig.pos
                    } else {
                        endJig.pos to startJig.pos
                    }
                    
                    if (visitedPairs.contains(pairKey)) continue
                    visitedPairs.add(pairKey)

                    // Adjacent jigsaws (touching) don't need wire fill — mark as connected directly
                    val distVec = endJig.pos.subtract(startJig.pos)
                    val distSq = distVec.x * distVec.x + distVec.y * distVec.y + distVec.z * distVec.z
                    if (distSq <= 1) {
                        connectedJigsaws.add(startJig.pos)
                        connectedJigsaws.add(endJig.pos)
                        wiresPlaced++
                        continue
                    }

                    val segments = fillGapJigsawToJigsaw(startJig, endJig, roomPieces, placedPieces, grid, config, enclosedWallCells)
                    if (segments >= 0) {
                        segmentsPlaced += segments
                        connectedJigsaws.add(startJig.pos)
                        connectedJigsaws.add(endJig.pos)
                    }
                    wiresPlaced++
                }
            }
        }

        CobblemonDungeonDungeonsEngine.logger.info("Wire mapping complete: filled $wiresPlaced wires with $segmentsPlaced segments.")
    }

    private fun fillGapJigsawToJigsaw(
        start: ParsedJigsaw,
        end: ParsedJigsaw,
        roomPieces: List<StructurePiece>,
        placedPieces: MutableList<StructurePiece>,
        grid: DungeonGrid,
        config: DungeonConfig,
        enclosedWallCells: Set<Pair<Int, Int>>
    ): Int {
        var currentJig = start
        val startPiece = placedPieces.firstOrNull { it.parsedJigsaws.contains(start) } 
            ?: roomPieces.firstOrNull { it.parsedJigsaws.contains(start) }
        var currentHasHazard = startPiece?.hasHazard ?: false
        
        var placed = 0
        var iter = 0
        val tempPlaced = mutableListOf<StructurePiece>()
        var success = false
        
        while (iter < 200) {
            iter++
            val distVec = end.pos.subtract(currentJig.pos)
            val distSq = distVec.x * distVec.x + distVec.y * distVec.y + distVec.z * distVec.z
            
            if (distSq <= 1) {
                success = true
                break
            }
            
            val dot = distVec.x * currentJig.facing.stepX + distVec.y * currentJig.facing.stepY + distVec.z * currentJig.facing.stepZ
            if (dot <= 0) break

            val requiredFacing = currentJig.facing.opposite
            var placedPiece = false
            
            val shuffledStraights = straights.shuffled(kotlin.random.Random(random.nextLong()))
            val sortedStraights = shuffledStraights.sortedBy { if (it.hasHazard == currentHasHazard) 0 else 1 }
            
            for (pt in sortedStraights) {
                if (!config.generateHazardSeas && pt.res.path.lowercase().contains("open")) {
                    continue
                }
                
                var bestRot = Rotation.NONE
                var matchedLocalJig: ParsedJigsaw? = null
                
                for (rot in Rotation.values()) {
                    val match = pt.jigsaws.firstOrNull { rot.rotate(it.facing) == requiredFacing }
                    if (match != null) {
                        bestRot = rot
                        matchedLocalJig = match
                        break
                    }
                }
                
                if (matchedLocalJig == null) continue
                
                val targetPos = currentJig.pos.relative(currentJig.facing)
                
                val dummy = StructurePiece(pt.template, BlockPos.ZERO, bestRot, PieceType.HALLWAY, pt.hasHazard)
                val dummyJigPos = if (dummy.parsedJigsaws.size == 1) {
                    dummy.parsedJigsaws[0].pos
                } else {
                    dummy.parsedJigsaws.firstOrNull { it.facing == requiredFacing }?.pos ?: dummy.parsedJigsaws[0].pos
                }
                
                val origin = targetPos.subtract(dummyJigPos)
                val newPiece = StructurePiece(pt.template, origin, bestRot, PieceType.HALLWAY, pt.hasHazard)
                
                // Reject if the hallway piece intersects with any room cell
                if (intersectsBlockedCell(newPiece, grid) || intersectsRoomPhysically(newPiece, roomPieces)) {
                    continue
                }

                // Check spill safety using precomputed enclosed wall cells
                val relativeFloorY = (pt.jigsaws.minOfOrNull { it.pos.y } ?: 1) - 1
                val openDirs = detectOpenDirections(pt.template, bestRot, relativeFloorY)
                val cx = Math.floorDiv(targetPos.x - this.origin.x, DungeonGrid.CELL_SIZE)
                val cz = Math.floorDiv(targetPos.z - this.origin.z, DungeonGrid.CELL_SIZE)
                if (!isPlacementSpillSafe(cx, cz, 1, 1, openDirs, config.generateHazardSeas, enclosedWallCells)) {
                    continue // Try another straight template in the pool
                }
                
                val openJig = if (newPiece.parsedJigsaws.size == 1) {
                    ParsedJigsaw(newPiece.parsedJigsaws[0].pos, currentJig.facing)
                } else {
                    newPiece.parsedJigsaws.firstOrNull { it.facing == currentJig.facing && it.pos != targetPos }
                        ?: newPiece.parsedJigsaws.firstOrNull { it.facing == currentJig.facing }
                }
                
                if (openJig == null) continue
                
                val newDistVec = end.pos.subtract(openJig.pos)
                val newDistSq = newDistVec.let { it.x * it.x + it.y * it.y + it.z * it.z }
                val newDot = newDistVec.x * currentJig.facing.stepX + newDistVec.y * currentJig.facing.stepY + newDistVec.z * currentJig.facing.stepZ
                 
                // Reject if this piece overshoots or doesn't move us closer
                if (newDot < 0 || (newDistSq >= distSq && newDistSq > 1)) {
                    continue
                }
                
                // Piece fits!
                placedPieces.add(newPiece)
                tempPlaced.add(newPiece)
                placed++
                currentJig = openJig
                currentHasHazard = pt.hasHazard
                placedPiece = true
                break
            }
            
            if (!placedPiece) {
                val availableDistances = straights.map { 
                    val dummy = StructurePiece(it.template, BlockPos.ZERO, Rotation.NONE, PieceType.HALLWAY, it.hasHazard)
                    val jigs = dummy.parsedJigsaws
                    if (jigs.size >= 2) jigs[0].pos.distManhattan(jigs[1].pos) else 0 
                }.distinct()
                CobblemonDungeonDungeonsEngine.logger.warn("Gap fill failed! Start: ${start.pos}, Target: ${end.pos}, Remaining distSq: $distSq. Pool piece distances: $availableDistances")
                break
            }
        }

        if (!success) {
            placedPieces.removeAll(tempPlaced)
            return -1
        }

        return placed
    }

    // ═════════════════════════════════════════════════════════════════════
    //  END PIECE PLACEMENT (CAPPING)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Attempts to place an end piece at [jig]'s exit. Returns true if successfully
     * placed, false if no matching end piece could be found/rotated.
     */
    private fun tryPlaceEndPiece(
        jig: ParsedJigsaw,
        placedPieces: MutableList<StructurePiece>,
        grid: DungeonGrid,
        config: DungeonConfig,
        enclosedWallCells: Set<Pair<Int, Int>>
    ): Boolean {
        if (ends.isEmpty()) return false

        val endPt = ends[random.nextInt(ends.size)]
        val endFacing = jig.facing.opposite

        var bestRot = Rotation.NONE
        var matchedLocalJig: ParsedJigsaw? = null

        for (rot in Rotation.values()) {
            val match = endPt.jigsaws.firstOrNull { rot.rotate(it.facing) == endFacing }
            if (match != null) {
                bestRot = rot
                matchedLocalJig = match
                break
            }
        }

        if (matchedLocalJig == null) return false

        val targetPos = jig.pos.relative(jig.facing)
        val dummy = StructurePiece(endPt.template, BlockPos.ZERO, bestRot, PieceType.END, endPt.hasHazard)
        val dummyJigPos = if (dummy.parsedJigsaws.size == 1) {
            dummy.parsedJigsaws[0].pos
        } else {
            dummy.parsedJigsaws.firstOrNull { it.facing == endFacing }?.pos ?: dummy.parsedJigsaws[0].pos
        }

        val origin = targetPos.subtract(dummyJigPos)
        
        val endPiece = StructurePiece(endPt.template, origin, bestRot, PieceType.END, endPt.hasHazard)
        
        // Reject if the end piece intersects with any room cell
        val roomPieces = placedPieces.filter { it.type == PieceType.ROOM }
        if (intersectsBlockedCell(endPiece, grid) || intersectsRoomPhysically(endPiece, roomPieces)) {
            return false
        }
        
        // Check spill safety for the cell the end piece occupies
        val cx = Math.floorDiv(jig.pos.x - this.origin.x, DungeonGrid.CELL_SIZE) + jig.facing.stepX
        val cz = Math.floorDiv(jig.pos.z - this.origin.z, DungeonGrid.CELL_SIZE) + jig.facing.stepZ
        val relativeFloorY = (endPt.jigsaws.minOfOrNull { it.pos.y } ?: 1) - 1
        val openDirs = detectOpenDirections(endPt.template, bestRot, relativeFloorY)
        if (!isPlacementSpillSafe(cx, cz, 1, 1, openDirs, config.generateHazardSeas, enclosedWallCells)) {
            return false
        }
        
        placedPieces.add(endPiece)
        return true
    }

    fun capUnconnectedRoomExits(
        grid: DungeonGrid,
        roomPieces: List<StructurePiece>,
        placedPieces: MutableList<StructurePiece>,
        nodeMap: MutableMap<Long, StructurePiece>,
        config: DungeonConfig,
        enclosedWallCells: Set<Pair<Int, Int>>
    ): List<ParsedJigsaw> {
        val uncapped = mutableListOf<ParsedJigsaw>()

        for (room in roomPieces) {
            for (jig in room.parsedJigsaws) {
                if (connectedJigsaws.contains(jig.pos)) continue

                if (tryConnectToAdjacentPiece(jig, nodeMap, placedPieces, enclosedWallCells, config, grid)) {
                    // Connected to adjacent piece
                } else if (!tryPlaceEndPiece(jig, placedPieces, grid, config, enclosedWallCells)) {
                    uncapped.add(jig)
                }
            }
        }

        CobblemonDungeonDungeonsEngine.logger.info(
            "Room exit capping: capped ${roomPieces.sumOf { it.parsedJigsaws.size } - uncapped.size - connectedJigsaws.count { pos -> roomPieces.any { r -> r.parsedJigsaws.any { it.pos == pos } } }} room exits with end pieces."
        )
        return uncapped
    }

    fun capUnconnectedNodeExits(
        grid: DungeonGrid,
        nodeMap: MutableMap<Long, StructurePiece>,
        placedPieces: MutableList<StructurePiece>,
        config: DungeonConfig,
        enclosedWallCells: Set<Pair<Int, Int>>
    ): List<ParsedJigsaw> {
        val uncapped = mutableListOf<ParsedJigsaw>()
        var capped = 0

        for (piece in nodeMap.values) {
            for (jig in piece.parsedJigsaws) {
                if (connectedJigsaws.contains(jig.pos)) continue

                if (tryConnectToAdjacentPiece(jig, nodeMap, placedPieces, enclosedWallCells, config, grid)) {
                    // Connected to adjacent piece
                } else if (tryPlaceEndPiece(jig, placedPieces, grid, config, enclosedWallCells)) {
                    capped++
                } else {
                    uncapped.add(jig)
                }
            }
        }

        CobblemonDungeonDungeonsEngine.logger.info(
            "Node exit capping: capped $capped node exits with end pieces, ${uncapped.size} remain uncapped."
        )
        return uncapped
    }

    private fun tryConnectToAdjacentPiece(
        jig: ParsedJigsaw,
        nodeMap: MutableMap<Long, StructurePiece>,
        placedPieces: MutableList<StructurePiece>,
        enclosedWallCells: Set<Pair<Int, Int>>,
        config: DungeonConfig,
        grid: DungeonGrid
    ): Boolean {
        val sx = Math.floorDiv(jig.pos.x - origin.x, DungeonGrid.CELL_SIZE)
        val sz = Math.floorDiv(jig.pos.z - origin.z, DungeonGrid.CELL_SIZE)
        
        val cx = sx + jig.facing.stepX
        val cz = sz + jig.facing.stepZ
        val adjKey = cellKey(cx, cz)
        
        val cellCenter = origin.offset(
            cx * DungeonGrid.CELL_SIZE + DungeonGrid.CELL_SIZE / 2,
            jig.pos.y - origin.y, // relative to origin.y
            cz * DungeonGrid.CELL_SIZE + DungeonGrid.CELL_SIZE / 2
        )
        
        val adjPiece = nodeMap[adjKey] ?: placedPieces.firstOrNull { 
            it.type == PieceType.HALLWAY && it.boundingBox.isInside(cellCenter) 
        } ?: return false
        
        // Find all directions it currently connects to
        val currentConnectedDirs = adjPiece.parsedJigsaws
            .filter { connectedJigsaws.contains(it.pos) }
            .map { it.facing }
            .toSet()
            
        val newDir = jig.facing.opposite
        val targetDirs = (currentConnectedDirs + newDir).toList()
        
        val category = when (targetDirs.size) {
            4 -> ParsedTemplate.Category.CROSS
            3 -> ParsedTemplate.Category.T_JUNCTION
            2 -> {
                if (targetDirs[0] == targetDirs[1].opposite) ParsedTemplate.Category.STRAIGHT
                else ParsedTemplate.Category.CORNER
            }
            1 -> ParsedTemplate.Category.END
            else -> return false
        }
        
        val pool = poolForType(category)
        if (pool.isEmpty()) return false
        
        for (candidate in pool.shuffled(kotlin.random.Random(random.nextLong()))) {
            val rot = findMatchingRotation(candidate, targetDirs)
            if (rot != null) {
                val anchorDir = targetDirs.firstOrNull { dir -> adjPiece.parsedJigsaws.any { it.facing == dir } } ?: continue
                val targetJigPos = adjPiece.parsedJigsaws.first { it.facing == anchorDir }.pos
                
                val dummy = StructurePiece(candidate.template, BlockPos.ZERO, rot, PieceType.HALLWAY, candidate.hasHazard)
                val dummyJigPos = dummy.parsedJigsaws.firstOrNull { it.facing == anchorDir }?.pos ?: continue
                val newWorldPos = targetJigPos.subtract(dummyJigPos)
                
                val newPiece = StructurePiece(
                    candidate.template,
                    newWorldPos,
                    rot,
                    if (category == ParsedTemplate.Category.END) PieceType.END else PieceType.HALLWAY,
                    candidate.hasHazard
                )
                
                // Room collision check
                val roomPieces = placedPieces.filter { it.type == PieceType.ROOM }
                if (intersectsBlockedCell(newPiece, grid) || intersectsRoomPhysically(newPiece, roomPieces)) {
                    continue
                }
                
                // Spill safety check
                val relativeFloorY = (candidate.jigsaws.minOfOrNull { it.pos.y } ?: 1) - 1
                val openDirs = detectOpenDirections(candidate.template, rot, relativeFloorY)
                if (!isPlacementSpillSafe(cx, cz, 1, 1, openDirs, config.generateHazardSeas, enclosedWallCells)) {
                    continue
                }
                
                // Swap successfully!
                placedPieces.remove(adjPiece)
                placedPieces.add(newPiece)
                nodeMap[adjKey] = newPiece
                
                // Mark connection as connected
                connectedJigsaws.add(jig.pos)
                for (nj in newPiece.parsedJigsaws) {
                    if (targetDirs.contains(nj.facing)) {
                        connectedJigsaws.add(nj.pos)
                    }
                }
                
                CobblemonDungeonDungeonsEngine.logger.info("Upgraded adjacent piece at ($cx, $cz) to $category to connect with exit at ${jig.pos}")
                return true
            }
        }
        
        return false
    }

    private fun intersectsBlockedCell(piece: StructurePiece, grid: DungeonGrid): Boolean {
        val box = piece.boundingBox
        val minX = box.minX() + 1
        val maxX = box.maxX() - 1
        val minZ = box.minZ() + 1
        val maxZ = box.maxZ() - 1
        
        if (minX > maxX || minZ > maxZ) return false
        
        val minCellX = Math.floorDiv(minX - origin.x, DungeonGrid.CELL_SIZE)
        val maxCellX = Math.floorDiv(maxX - origin.x, DungeonGrid.CELL_SIZE)
        val minCellZ = Math.floorDiv(minZ - origin.z, DungeonGrid.CELL_SIZE)
        val maxCellZ = Math.floorDiv(maxZ - origin.z, DungeonGrid.CELL_SIZE)
        
        for (cx in minCellX..maxCellX) {
            for (cz in minCellZ..maxCellZ) {
                if (grid.isBlocked(cx, cz)) {
                    return true
                }
            }
        }
        return false
    }

    private fun intersectsRoomPhysically(piece: StructurePiece, roomPieces: List<StructurePiece>): Boolean {
        val a = piece.boundingBox
        for (room in roomPieces) {
            val b = room.boundingBox
            if (a.minY() <= b.maxY() && a.maxY() >= b.minY()) {
                val overlapX = Math.max(0, Math.min(a.maxX(), b.maxX()) - Math.max(a.minX(), b.minX()) + 1)
                val overlapZ = Math.max(0, Math.min(a.maxZ(), b.maxZ()) - Math.max(a.minZ(), b.minZ()) + 1)
                if (overlapX > 1 && overlapZ > 1) {
                    return true
                }
            }
        }
        return false
    }

    private fun poolForType(type: ParsedTemplate.Category): List<ParsedTemplate> = when (type) {
        ParsedTemplate.Category.CROSS -> crosses
        ParsedTemplate.Category.T_JUNCTION -> ts
        ParsedTemplate.Category.CORNER -> corners
        ParsedTemplate.Category.STRAIGHT -> straights
        ParsedTemplate.Category.END -> ends
        else -> emptyList()
    }

    private fun findMatchingRotation(pt: ParsedTemplate, openDirs: List<Direction>): Rotation? {
        val requiredSet = openDirs.toSet()
        for (rot in Rotation.values()) {
            val rotatedFacings = pt.jigsaws.map { rot.rotate(it.facing) }.toSet()
            if (rotatedFacings == requiredSet) return rot
        }
        for (rot in Rotation.values()) {
            val rotatedFacings = pt.jigsaws.map { rot.rotate(it.facing) }.toSet()
            if (rotatedFacings.containsAll(requiredSet)) return rot
        }
        return null
    }

    private fun cellKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    private fun isOpenBlock(state: BlockState): Boolean {
        if (state.isAir) return true
        if (!state.blocksMotion()) return true
        val name = BuiltInRegistries.BLOCK.getKey(state.block).toString().lowercase()
        return name.contains("void") || !state.fluidState.isEmpty
    }

    fun detectOpenDirections(
        template: StructureTemplate,
        rotation: Rotation,
        relativeFloorY: Int
    ): Set<Direction> {
        val settings = StructurePlaceSettings().setRotation(rotation)
        val localBox = template.getBoundingBox(settings, BlockPos.ZERO)
        
        val palettes = getPalettes(template)
        val firstPalette = palettes.firstOrNull()
        val rawBlocks = firstPalette?.blocks() ?: emptyList()
        val blocks = rawBlocks.map { info ->
            val rotatedPos = StructureTemplate.calculateRelativePosition(settings, info.pos)
            val rotatedState = info.state.rotate(rotation)
            StructureTemplate.StructureBlockInfo(rotatedPos, rotatedState, info.nbt)
        }
        
        val blockMap = mutableMapOf<BlockPos, net.minecraft.world.level.block.state.BlockState>()
        for (b in blocks) {
            blockMap[b.pos] = b.state
        }
        
        val jigsawBlocks = template.filterBlocks(BlockPos.ZERO, settings, Blocks.JIGSAW)
        val jigsawPositions = jigsawBlocks.map { it.pos }
        
        val openDirs = mutableSetOf<Direction>()
        
        val jigsawMinZ = jigsawPositions.minOfOrNull { it.z } ?: (localBox.minZ() + localBox.maxZ()) / 2
        val jigsawMaxZ = jigsawPositions.maxOfOrNull { it.z } ?: (localBox.minZ() + localBox.maxZ()) / 2
        val jigsawMinX = jigsawPositions.minOfOrNull { it.x } ?: (localBox.minX() + localBox.maxX()) / 2
        val jigsawMaxX = jigsawPositions.maxOfOrNull { it.x } ?: (localBox.minX() + localBox.maxX()) / 2
        
        val innerX = if (localBox.maxX() - localBox.minX() <= 1) localBox.minX()..localBox.maxX() else (localBox.minX() + 1)..(localBox.maxX() - 1)
        val innerZ = if (localBox.maxZ() - localBox.minZ() <= 1) localBox.minZ()..localBox.maxZ() else (localBox.minZ() + 1)..(localBox.maxZ() - 1)

        // North face: z = localBox.minZ
        var northOpen = true
        val hasJigsawNorth = jigsawBlocks.any { Math.abs(it.pos.z - localBox.minZ()) <= 1 && it.facing() == Direction.NORTH } || (localBox.maxZ() - localBox.minZ() <= 1 && jigsawBlocks.any { it.facing() == Direction.SOUTH })
        if (hasJigsawNorth) {
            northOpen = false
        } else {
            val checkRangeZ = localBox.minZ()..jigsawMinZ
            for (z in checkRangeZ) {
                var layerHasSolid = false
                for (x in innerX) {
                    val hasSolid = (relativeFloorY + 1..relativeFloorY + 2).any { y ->
                        val state = blockMap[BlockPos(x, y, z)]
                        state != null && !state.isAir && !isOpenBlock(state) && state.block != Blocks.JIGSAW
                    }
                    if (hasSolid) {
                        layerHasSolid = true
                        break
                    }
                }
                if (layerHasSolid) {
                    northOpen = false
                    break
                }
            }
        }
        if (northOpen) openDirs.add(Direction.NORTH)
        
        // South face: z = localBox.maxZ
        var southOpen = true
        val hasJigsawSouth = jigsawBlocks.any { Math.abs(it.pos.z - localBox.maxZ()) <= 1 && it.facing() == Direction.SOUTH } || (localBox.maxZ() - localBox.minZ() <= 1 && jigsawBlocks.any { it.facing() == Direction.NORTH })
        if (hasJigsawSouth) {
            southOpen = false
        } else {
            val checkRangeZ = jigsawMaxZ..localBox.maxZ()
            for (z in checkRangeZ.reversed()) {
                var layerHasSolid = false
                for (x in innerX) {
                    val hasSolid = (relativeFloorY + 1..relativeFloorY + 2).any { y ->
                        val state = blockMap[BlockPos(x, y, z)]
                        state != null && !state.isAir && !isOpenBlock(state) && state.block != Blocks.JIGSAW
                    }
                    if (hasSolid) {
                        layerHasSolid = true
                        break
                    }
                }
                if (layerHasSolid) {
                    southOpen = false
                    break
                }
            }
        }
        if (southOpen) openDirs.add(Direction.SOUTH)
        
        // West face: x = localBox.minX
        var westOpen = true
        val hasJigsawWest = jigsawBlocks.any { Math.abs(it.pos.x - localBox.minX()) <= 1 && it.facing() == Direction.WEST } || (localBox.maxX() - localBox.minX() <= 1 && jigsawBlocks.any { it.facing() == Direction.EAST })
        if (hasJigsawWest) {
            westOpen = false
        } else {
            val checkRangeX = localBox.minX()..jigsawMinX
            for (x in checkRangeX) {
                var layerHasSolid = false
                for (z in innerZ) {
                    val hasSolid = (relativeFloorY + 1..relativeFloorY + 2).any { y ->
                        val state = blockMap[BlockPos(x, y, z)]
                        state != null && !state.isAir && !isOpenBlock(state) && state.block != Blocks.JIGSAW
                    }
                    if (hasSolid) {
                        layerHasSolid = true
                        break
                    }
                }
                if (layerHasSolid) {
                    westOpen = false
                    break
                }
            }
        }
        if (westOpen) openDirs.add(Direction.WEST)
        
        // East face: x = localBox.maxX
        var eastOpen = true
        val hasJigsawEast = jigsawBlocks.any { Math.abs(it.pos.x - localBox.maxX()) <= 1 && it.facing() == Direction.EAST } || (localBox.maxX() - localBox.minX() <= 1 && jigsawBlocks.any { it.facing() == Direction.WEST })
        if (hasJigsawEast) {
            eastOpen = false
        } else {
            val checkRangeX = jigsawMaxX..localBox.maxX()
            for (x in checkRangeX.reversed()) {
                var layerHasSolid = false
                for (z in innerZ) {
                    val hasSolid = (relativeFloorY + 1..relativeFloorY + 2).any { y ->
                        val state = blockMap[BlockPos(x, y, z)]
                        state != null && !state.isAir && !isOpenBlock(state) && state.block != Blocks.JIGSAW
                    }
                    if (hasSolid) {
                        layerHasSolid = true
                        break
                    }
                }
                if (layerHasSolid) {
                    eastOpen = false
                    break
                }
            }
        }
        if (eastOpen) openDirs.add(Direction.EAST)
        
        return openDirs
    }

    fun getAdjacentCellsForFace(gx: Int, gz: Int, wCells: Int, dCells: Int, dir: Direction): List<Pair<Int, Int>> {
        val list = mutableListOf<Pair<Int, Int>>()
        when (dir) {
            Direction.NORTH -> {
                for (x in gx until gx + wCells) {
                    list.add(Pair(x, gz - 1))
                }
            }
            Direction.SOUTH -> {
                for (x in gx until gx + wCells) {
                    list.add(Pair(x, gz + dCells))
                }
            }
            Direction.WEST -> {
                for (z in gz until gz + dCells) {
                    list.add(Pair(gx - 1, z))
                }
            }
            Direction.EAST -> {
                for (z in gz until gz + dCells) {
                    list.add(Pair(gx + wCells, z))
                }
            }
            else -> {}
        }
        return list
    }

    fun canOpenPartReachBoundary(
        grid: DungeonGrid,
        startX: Int,
        startY: Int,
        openDir: Direction
    ): Boolean {
        var cx = startX
        var cz = startY
        while (grid.inBounds(cx, cz)) {
            if (cx <= 0 || cx >= grid.width - 1 || cz <= 0 || cz >= grid.height - 1) {
                return true
            }
            if (grid.isBlocked(cx, cz) || grid.isOpen(cx, cz)) {
                return false
            }
            cx += openDir.stepX
            cz += openDir.stepZ
        }
        return true
    }

    fun isPlacementSpillSafe(
        cx: Int, cz: Int, wCells: Int, dCells: Int,
        openDirs: Set<Direction>,
        generateHazardSeas: Boolean,
        enclosedWallCells: Set<Pair<Int, Int>>
    ): Boolean {
        if (openDirs.isEmpty()) return true
        if (!generateHazardSeas) return false // Reject open templates if hazard seas are disabled!
        
        for (dir in openDirs) {
            val adjCells = getAdjacentCellsForFace(cx, cz, wCells, dCells, dir)
            for (p in adjCells) {
                if (!enclosedWallCells.contains(p)) {
                    return false
                }
            }
        }
        return true
    }

    fun computeEnclosedWallCells(grid: DungeonGrid): Set<Pair<Int, Int>> {
        val enclosed = mutableSetOf<Pair<Int, Int>>()
        val visited = mutableSetOf<Pair<Int, Int>>()
        
        for (x in 0 until grid.width) {
            for (z in 0 until grid.height) {
                if (grid.cellState(x, z) == DungeonGrid.CellState.WALL && !visited.contains(Pair(x, z))) {
                    val region = mutableListOf<Pair<Int, Int>>()
                    val queue = java.util.ArrayDeque<Pair<Int, Int>>()
                    queue.add(Pair(x, z))
                    visited.add(Pair(x, z))
                    var reachesBoundary = false
                    
                    while (queue.isNotEmpty()) {
                        val curr = queue.poll()
                        region.add(curr)
                        
                        if (curr.first <= 0 || curr.first >= grid.width - 1 || curr.second <= 0 || curr.second >= grid.height - 1) {
                            reachesBoundary = true
                        }
                        
                        for (dir in Direction.values()) {
                            if (dir.axis.isVertical) continue
                            val nx = curr.first + dir.stepX
                            val nz = curr.second + dir.stepZ
                            if (grid.inBounds(nx, nz) && grid.cellState(nx, nz) == DungeonGrid.CellState.WALL) {
                                val p = Pair(nx, nz)
                                if (!visited.contains(p)) {
                                    visited.add(p)
                                    queue.add(p)
                                }
                            }
                        }
                    }
                    if (!reachesBoundary) {
                        enclosed.addAll(region)
                    }
                }
            }
        }
        return enclosed
    }

    fun optimizeJunctionExits(
        nodeMap: MutableMap<Long, StructurePiece>,
        placedPieces: MutableList<StructurePiece>,
        config: DungeonConfig,
        enclosedWallCells: Set<Pair<Int, Int>>
    ) {
        val keys = nodeMap.keys.toList()
        for (key in keys) {
            val piece = nodeMap[key] ?: continue
            val originalJigsaws = piece.parsedJigsaws
            val connectedJigs = originalJigsaws.filter { connectedJigsaws.contains(it.pos) }
            
            if (connectedJigs.size < originalJigsaws.size) {
                val connectedDirs = connectedJigs.map { it.facing }
                if (connectedDirs.isEmpty()) {
                    placedPieces.remove(piece)
                    nodeMap.remove(key)
                    continue
                }
                
                val targetCategory = when (connectedDirs.size) {
                    3 -> ParsedTemplate.Category.T_JUNCTION
                    2 -> ParsedTemplate.Category.CORNER
                    1 -> ParsedTemplate.Category.END
                    else -> null
                }
                
                if (targetCategory != null) {
                    val pool = poolForType(targetCategory)
                    var foundReplacement = false
                    
                    for (candidate in pool.shuffled(kotlin.random.Random(random.nextLong()))) {
                        val rot = findMatchingRotation(candidate, connectedDirs)
                        if (rot != null) {
                            val relativeFloorY = (candidate.jigsaws.minOfOrNull { it.pos.y } ?: 1) - 1
                            val openDirs = detectOpenDirections(candidate.template, rot, relativeFloorY)
                            val x = (key shr 32).toInt()
                            val z = key.toInt()
                            
                            if (!isPlacementSpillSafe(x, z, 1, 1, openDirs, config.generateHazardSeas, enclosedWallCells)) {
                                continue
                            }
                            
                            val cx = x * DungeonGrid.CELL_SIZE + origin.x
                            val cz = z * DungeonGrid.CELL_SIZE + origin.z
                            val settings = StructurePlaceSettings().setRotation(rot)
                            val rotatedCenter = StructureTemplate.calculateRelativePosition(settings, BlockPos(3, 0, 3))
                            val newWorldPos = BlockPos(cx + 3 - rotatedCenter.x, origin.y, cz + 3 - rotatedCenter.z)
                            
                            val newPiece = StructurePiece(
                                candidate.template,
                                newWorldPos,
                                rot,
                                if (targetCategory == ParsedTemplate.Category.END) PieceType.END else PieceType.HALLWAY,
                                candidate.hasHazard
                            )
                            
                            placedPieces.remove(piece)
                            placedPieces.add(newPiece)
                            nodeMap[key] = newPiece
                            
                            for (nj in newPiece.parsedJigsaws) {
                                if (connectedDirs.contains(nj.facing)) {
                                    connectedJigsaws.add(nj.pos)
                                }
                            }
                            
                            foundReplacement = true
                            CobblemonDungeonDungeonsEngine.logger.info("Optimized node at cell $key: swapped with ${candidate.template.toString()} (reduced exits to ${connectedDirs.size})")
                            break
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun parseJigsaws(template: StructureTemplate, rotation: Rotation = Rotation.NONE): List<ParsedJigsaw> {
            val settings = StructurePlaceSettings().setRotation(rotation)
            return template.filterBlocks(BlockPos.ZERO, settings, Blocks.JIGSAW).map {
                ParsedJigsaw(it.pos, it.facing())
            }
        }
        
        fun getPalettes(template: StructureTemplate): List<StructureTemplate.Palette> {
            return try {
                val field = try {
                    StructureTemplate::class.java.getDeclaredField("field_15586")
                } catch (e: NoSuchFieldException) {
                    StructureTemplate::class.java.getDeclaredField("palettes")
                }
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                field.get(template) as List<StructureTemplate.Palette>
            } catch (e: Exception) {
                CobblemonDungeonDungeonsEngine.logger.error("Failed to get palettes field via reflection", e)
                emptyList()
            }
        }

        fun detectHazard(template: StructureTemplate): Boolean {
            val palettes = getPalettes(template)
            for (palette in palettes) {
                for (block in palette.blocks()) {
                    if (block.state.`is`(net.drachi.cdde.registry.ModBlocks.HAZARD)) {
                        return true
                    }
                }
            }
            return false
        }
    }
}




