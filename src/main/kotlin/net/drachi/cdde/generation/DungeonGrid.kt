package net.drachi.cdde.generation

import net.drachi.cdde.CobblemonDungeonDungeonsEngine
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource

/**
 * 2D super-grid for Hauberk-style dungeon layout generation.
 * Each cell maps to a [CELL_SIZE]×[CELL_SIZE] block area in the Minecraft world.
 *
 * Uses Hauberk's odd-index convention: corridors exist only at odd coordinates,
 * walls at even coordinates, guaranteeing [CELL_SIZE]-block separation between
 * parallel corridors (zero clipping).
 *
 * Algorithm (adapted from hauberk/dungeon.dart):
 *  1. [blockRoomFootprint]: Mark room areas as BLOCKED.
 *  2. [fillMazes]:          Growing-tree fills all remaining empty space.
 *  3. [connectRegions]:     Union-Find merges all isolated regions.
 *  4. [removeDeadEnds]:     Prune corridors with only 1 exit.
 *
 * After all 4 steps, the grid contains a fully-connected maze with no dead ends,
 * ready for template mapping.
 */
class DungeonGrid(
    val width: Int,
    val height: Int,
    private val random: RandomSource,
    private val windingPercent: Int = 45,
    private val extraConnectorChance: Int = 20
) {
    companion object {
        /** Each super-cell = 7×7 world blocks (matches hallway width). */
        const val CELL_SIZE = 7

        /** The 4 horizontal cardinal directions. */
        val CARDINALS = listOf(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)

        /** Compute the minimum odd grid dimension for a given room count. */
        fun gridSizeForRooms(maxRooms: Int): Int {
            val raw = (Math.ceil(Math.sqrt(maxRooms.toDouble()) * 8.0).toInt() + 5)
            return if (raw % 2 == 0) raw + 1 else raw
        }

        /** Round a cell count UP to the nearest odd number. */
        fun toOdd(n: Int) = if (n % 2 == 0) n + 1 else n
    }

    /** Possible states for each grid cell. */
    enum class CellState {
        /** Solid wall — available for maze carving. */
        WALL,
        /** Carved corridor — part of the maze network. */
        CORRIDOR,
        /** Reserved by a room template — permanently blocked. */
        BLOCKED,
        /** A permanent exit from a room, acts as a corridor but immune to pruning. */
        ROOM_EXIT
    }

    // ── Grid state ─────────────────────────────────────────────────────────
    private val cells = Array(width) { Array(height) { CellState.WALL } }
    private val regionMap = Array(width) { IntArray(height) { -1 } }
    private var currentRegion = -1

    init {
        require(width % 2 == 1 && height % 2 == 1) {
            "Grid dimensions must be odd (got $width × $height)"
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  QUERY API
    // ═══════════════════════════════════════════════════════════════════════

    fun cellState(x: Int, z: Int): CellState =
        if (inBounds(x, z)) cells[x][z] else CellState.WALL

    fun isOpen(x: Int, z: Int): Boolean =
        inBounds(x, z) && (cells[x][z] == CellState.CORRIDOR || cells[x][z] == CellState.ROOM_EXIT)

    fun isBlocked(x: Int, z: Int): Boolean =
        inBounds(x, z) && cells[x][z] == CellState.BLOCKED

    fun region(x: Int, z: Int): Int =
        if (inBounds(x, z)) regionMap[x][z] else -1

    fun inBounds(x: Int, z: Int): Boolean =
        x in 0 until width && z in 0 until height

    fun allocateRegion(): Int {
        currentRegion++
        return currentRegion
    }

    /** Count open (CORRIDOR or ROOM_EXIT) cardinal neighbors for the given cell. */
    fun countOpenNeighbors(x: Int, z: Int): Int =
        CARDINALS.count { isOpen(x + it.stepX, z + it.stepZ) }

    /** Returns cardinal directions where neighbors are open (CORRIDOR). */
    fun openNeighborDirs(x: Int, z: Int): List<Direction> =
        CARDINALS.filter { isOpen(x + it.stepX, z + it.stepZ) }

    // ═══════════════════════════════════════════════════════════════════════
    //  STEP 1: BLOCK ROOM FOOTPRINTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Marks a rectangular area of cells as [CellState.BLOCKED].
     * The maze will never carve into these cells.
     *
     * @param startX Top-left X (should be an odd index for proper Hauberk alignment)
     * @param startZ Top-left Z (should be an odd index)
     * @param w      Width in cells (should be odd)
     * @param h      Height in cells (should be odd)
     */
    fun blockRoomFootprint(startX: Int, startZ: Int, w: Int, h: Int, regionId: Int) {
        for (x in startX until (startX + w).coerceAtMost(width)) {
            for (z in startZ until (startZ + h).coerceAtMost(height)) {
                cells[x][z] = CellState.BLOCKED
                regionMap[x][z] = regionId
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  STEP 2: FILL MAZES (Growing Tree)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Fills all remaining empty space with winding maze corridors.
     * Iterates over every odd-indexed cell; if still WALL, grows a maze from it.
     * Each new maze gets its own region ID.
     */
    fun fillMazes() {
        for (z in 1 until height step 2) {
            for (x in 1 until width step 2) {
                if (cells[x][z] == CellState.WALL) {
                    growMaze(x, z)
                }
            }
        }
        CobblemonDungeonDungeonsEngine.logger.info(
            "Maze fill complete: ${currentRegion + 1} regions created."
        )
    }

    /**
     * Growing-tree maze algorithm (adapted from hauberk/dungeon.dart _growMaze).
     * Uses DFS (stack.last) producing winding corridors.
     *
     * [windingPercent] controls randomness:
     *   0   = very straight (always continues in same direction)
     *   100 = very winding (always picks random direction)
     */
    private fun growMaze(startX: Int, startZ: Int) {
        val stack = mutableListOf(startX to startZ)
        var lastDir: Direction? = null

        startRegion()
        carve(startX, startZ)

        while (stack.isNotEmpty()) {
            val (cx, cz) = stack.last()

            // Find which cardinal directions we can carve toward
            val unmade = CARDINALS.filter { canCarve(cx, cz, it) }

            if (unmade.isNotEmpty()) {
                // Prefer continuing straight for less winding mazes
                val dir = if (lastDir != null &&
                    unmade.contains(lastDir) &&
                    random.nextInt(100) > windingPercent
                ) {
                    lastDir
                } else {
                    unmade[random.nextInt(unmade.size)]
                }

                // Carve the intermediate wall cell
                carve(cx + dir.stepX, cz + dir.stepZ)
                // Carve the destination cell (2 steps away)
                carve(cx + dir.stepX * 2, cz + dir.stepZ * 2)

                stack.add((cx + dir.stepX * 2) to (cz + dir.stepZ * 2))
                lastDir = dir
            } else {
                // Dead end — backtrack
                stack.removeAt(stack.lastIndex)
                lastDir = null
            }
        }
    }

    /**
     * Can we carve from (x, z) in the given direction?
     * Requires: destination (2 steps) is WALL, and 3 steps is in-bounds.
     */
    private fun canCarve(x: Int, z: Int, dir: Direction): Boolean {
        val dx = dir.stepX
        val dz = dir.stepZ
        // Bounds check 3 steps ahead (ensures wall beyond destination)
        if (!inBounds(x + dx * 3, z + dz * 3)) return false
        // Intermediate cell must be WALL (we cannot carve through ROOM_EXIT or BLOCKED)
        if (cells[x + dx][z + dz] != CellState.WALL) return false
        // Destination must be uncarved wall
        return cells[x + dx * 2][z + dz * 2] == CellState.WALL
    }

    /**
     * Projects a room's physical jigsaw into the grid as an un-prunable corridor cell.
     * Ensures the maze can connect to this exit without it being deleted.
     */
    fun projectRoomExit(wallX: Int, wallZ: Int, regionId: Int) {
        if (inBounds(wallX, wallZ)) {
            cells[wallX][wallZ] = CellState.ROOM_EXIT
            regionMap[wallX][wallZ] = regionId
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  STEP 3: CONNECT REGIONS (Union-Find)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Finds wall cells adjacent to 2+ different regions (connectors) and
     * opens them until all regions are merged into one.
     * Occasionally opens extra connectors to create loops (~5% chance).
     *
     * Adapted from hauberk/dungeon.dart _connectRegions.
     */
    fun connectRegions() {
        if (currentRegion < 0) return

        // 1. Union-Find: merged[originalRegion] → currentDestination
        val merged = IntArray(currentRegion + 1) { it }
        val openRegions = (0..currentRegion).toMutableSet()

        // Pre-merge adjacent open cells (e.g. ROOM_EXIT next to CORRIDOR)
        for (x in 0 until width) {
            for (z in 0 until height) {
                if (!isOpen(x, z)) continue
                val r1 = region(x, z)
                if (r1 < 0) continue
                for (dir in CARDINALS) {
                    val nx = x + dir.stepX
                    val nz = z + dir.stepZ
                    if (isOpen(nx, nz)) {
                        val r2 = region(nx, nz)
                        if (r2 >= 0 && r1 != r2) {
                            val dest = merged[r1]
                            val source = merged[r2]
                            if (dest != source) {
                                for (i in merged.indices) {
                                    if (merged[i] == source) merged[i] = dest
                                }
                                openRegions.remove(source)
                            }
                        }
                    }
                }
            }
        }

        // 2. Find all connector candidates
        val connectorRegions = mutableMapOf<Long, MutableSet<Int>>()
        for (x in 1 until width - 1) {
            for (z in 1 until height - 1) {
                if (cells[x][z] != CellState.WALL) continue

                val isAligned = (x % 2 == 0) != (z % 2 == 0)
                var touchesRoomExit = false
                val neighborRegions = mutableSetOf<Int>()

                for (dir in CARDINALS) {
                    val nx = x + dir.stepX
                    val nz = z + dir.stepZ
                    if (inBounds(nx, nz)) {
                        if (cells[nx][nz] == CellState.ROOM_EXIT) {
                            touchesRoomExit = true
                        }
                        // Ignore BLOCKED cells (room footprint) for region merging
                        if (cells[nx][nz] != CellState.BLOCKED) {
                            val r = region(nx, nz)
                            if (r >= 0) neighborRegions.add(r)
                        }
                    }
                }

                if (neighborRegions.size >= 2 && (isAligned || touchesRoomExit)) {
                    connectorRegions[cellKey(x, z)] = neighborRegions
                }
            }
        }

        val connectors = connectorRegions.keys.toMutableList()
        if (connectors.isEmpty()) {
            CobblemonDungeonDungeonsEngine.logger.warn("No connectors found — maze may be disconnected.")
            return
        }

        // 3. Keep opening connectors until all regions are one
        while (openRegions.size > 1 && connectors.isNotEmpty()) {
            val key = connectors[random.nextInt(connectors.size)]
            val cx = keyToX(key)
            val cz = keyToZ(key)

            // Open the connector
            carveConnector(cx, cz)

            // Determine which merged regions this connector bridges
            val bridgedRegions = connectorRegions[key]!!
                .map { merged[it] }
                .toSet().toList()

            if (bridgedRegions.size < 2) {
                connectors.remove(key)
                continue
            }

            val dest = bridgedRegions[0]
            val sources = bridgedRegions.drop(1).toSet()

            // Merge: remap all source entries to dest
            for (i in merged.indices) {
                if (merged[i] in sources) merged[i] = dest
            }
            openRegions.removeAll(sources)

            // Prune stale connectors
            connectors.removeAll { k ->
                // Remove connectors too close to the one we just opened
                val kx = keyToX(k)
                val kz = keyToZ(k)
                if (Math.abs(cx - kx) + Math.abs(cz - kz) < 2) return@removeAll true

                // Check if this connector still bridges different regions
                val kRegions = connectorRegions[k]!!.map { merged[it] }.toSet()
                if (kRegions.size > 1) return@removeAll false // Still useful

                // No longer needed — but occasionally open for loops
                if (random.nextInt(extraConnectorChance) == 0 && !createsSmallLoop(kx, kz)) {
                    carveConnector(kx, kz)
                }
                true
            }
        }

        // After the main loop, we might still have left-over connectors that were not evaluated.
        for (k in connectors) {
            val kx = keyToX(k)
            val kz = keyToZ(k)
            if (random.nextInt(extraConnectorChance) == 0 && !createsSmallLoop(kx, kz)) {
                carveConnector(kx, kz)
            }
        }

        CobblemonDungeonDungeonsEngine.logger.info(
            "Region connection complete: ${openRegions.size} region(s) remaining."
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  STEP 4: REMOVE DEAD ENDS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Prunes a percentage of dead-end corridor cells (cells with ≤1 open neighbor).
     *
     * @param prunePercent 0–100. At 100, behaves like the original (removes ALL dead ends).
     *   At 0, keeps all dead ends. At 50, each dead-end cell has a 50% chance of being
     *   removed per pass. Runs iteratively until no more cells are eligible.
     */
    fun removeDeadEnds(prunePercent: Int = 100) {
        val clampedPercent = prunePercent.coerceIn(0, 100)
        if (clampedPercent == 0) {
            CobblemonDungeonDungeonsEngine.logger.info("Dead-end pruning skipped (prunePercent=0).")
            return
        }

        var pruned = 0
        var changed = true
        while (changed) {
            changed = false
            for (x in 1 until width - 1) {
                for (z in 1 until height - 1) {
                    if (cells[x][z] != CellState.CORRIDOR) continue
                    if (countOpenNeighbors(x, z) <= 1) {
                        // Probabilistic pruning — roll per dead-end cell
                        if (clampedPercent < 100 && random.nextInt(100) >= clampedPercent) continue

                        cells[x][z] = CellState.WALL
                        regionMap[x][z] = -1
                        changed = true
                        pruned++
                    }
                }
            }
        }
        CobblemonDungeonDungeonsEngine.logger.info(
            "Dead-end pruning complete (${clampedPercent}%): removed $pruned cells."
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private fun startRegion() { currentRegion++ }

    private fun carve(x: Int, z: Int) {
        cells[x][z] = CellState.CORRIDOR
        regionMap[x][z] = currentRegion
    }

    /** Opens a connector cell, inheriting the region of the first open neighbor. */
    private fun carveConnector(x: Int, z: Int) {
        cells[x][z] = CellState.CORRIDOR
        for (dir in CARDINALS) {
            val r = region(x + dir.stepX, z + dir.stepZ)
            if (r >= 0) {
                regionMap[x][z] = r
                return
            }
        }
    }

    private fun createsSmallLoop(cx: Int, cz: Int): Boolean {
        val adjacentEvens = if (cx % 2 == 0) {
            listOf(Pair(cx, cz - 1), Pair(cx, cz + 1))
        } else {
            listOf(Pair(cx - 1, cz), Pair(cx + 1, cz))
        }
        
        for ((ex, ez) in adjacentEvens) {
            if (inBounds(ex, ez) && cells[ex][ez] == CellState.WALL) {
                var openNeighbors = 0
                for (dir in CARDINALS) {
                    val nx = ex + dir.stepX
                    val nz = ez + dir.stepZ
                    if (isOpen(nx, nz)) {
                        openNeighbors++
                    }
                }
                if (openNeighbors >= 3) {
                    return true
                }
            }
        }
        return false
    }

    private fun cellKey(x: Int, z: Int): Long = x.toLong() * height + z
    private fun keyToX(key: Long): Int = (key / height).toInt()
    private fun keyToZ(key: Long): Int = (key % height).toInt()

    // ═══════════════════════════════════════════════════════════════════════
    //  DEBUG: ASCII dump for development
    // ═══════════════════════════════════════════════════════════════════════

    /** Returns a multi-line ASCII representation of the grid for logging. */
    fun toAscii(): String {
        val sb = StringBuilder()
        for (z in 0 until height) {
            for (x in 0 until width) {
                sb.append(when (cells[x][z]) {
                    CellState.WALL -> '#'
                    CellState.CORRIDOR -> '.'
                    CellState.BLOCKED -> 'R'
                    CellState.ROOM_EXIT -> 'E'
                })
            }
            sb.append('\n')
        }
        return sb.toString()
    }
}



