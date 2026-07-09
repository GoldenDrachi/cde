package net.drachi.cdde.generation

import net.drachi.cdde.CobblemonDungeonDungeonsEngine
import java.io.File
import java.io.PrintWriter
import kotlin.math.max
import kotlin.math.min

object DungeonLogger {

    fun exportMap(placedPieces: List<StructurePiece>, filename: String) {
        if (placedPieces.isEmpty()) {
            CobblemonDungeonDungeonsEngine.logger.warn("DungeonLogger: No pieces to export.")
            return
        }

        var minX = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE

        // 1. Find physical bounds of the entire dungeon
        for (piece in placedPieces) {
            minX = min(minX, piece.boundingBox.minX())
            minZ = min(minZ, piece.boundingBox.minZ())
            maxX = max(maxX, piece.boundingBox.maxX())
            maxZ = max(maxZ, piece.boundingBox.maxZ())
        }

        val width = maxX - minX + 1
        val height = maxZ - minZ + 1

        if (width <= 0 || height <= 0 || width > 2000 || height > 2000) {
            CobblemonDungeonDungeonsEngine.logger.error("DungeonLogger: Invalid or excessively large bounds ($width x $height).")
            return
        }

        val map = Array(width) { CharArray(height) { ' ' } }

        // 2. Plot pieces
        // We plot in order of: Hallways, then Ends, then Rooms (Rooms overlay to stand out)
        val sortedPieces = placedPieces.sortedBy { 
            when (it.type) {
                PieceType.HALLWAY -> 0
                PieceType.END -> 1
                PieceType.ROOM -> 2
                else -> 0
            }
        }

        for (piece in sortedPieces) {
            val char = when (piece.type) {
                PieceType.ROOM -> 'R'
                PieceType.HALLWAY -> 'H'
                PieceType.END -> 'E'
                else -> '?'
            }

            for (x in piece.boundingBox.minX()..piece.boundingBox.maxX()) {
                for (z in piece.boundingBox.minZ()..piece.boundingBox.maxZ()) {
                    val localX = x - minX
                    val localZ = z - minZ
                    if (localX in 0 until width && localZ in 0 until height) {
                        map[localX][localZ] = char
                    }
                }
            }
            
            // Plot Jigsaws specifically to see connection points
            for (jig in piece.parsedJigsaws) {
                val localX = jig.pos.x - minX
                val localZ = jig.pos.z - minZ
                if (localX in 0 until width && localZ in 0 until height) {
                    map[localX][localZ] = 'J'
                }
            }
        }

        // 3. Write to file
        try {
            val file = File(filename)
            file.parentFile?.mkdirs()
            PrintWriter(file).use { writer ->
                writer.println("Dungeon Generation Physical Map")
                writer.println("Bounds: X[$minX..$maxX], Z[$minZ..$maxZ]")
                writer.println("Dimensions: $width x $height")
                writer.println("R = Room, H = Hallway/Node/Wire, E = End, J = Jigsaw Block\n")
                
                // Draw border
                writer.print("+")
                for (x in 0 until width) writer.print("-")
                writer.println("+")
                
                for (z in 0 until height) {
                    writer.print("|")
                    for (x in 0 until width) {
                        writer.print(map[x][z])
                    }
                    writer.println("|")
                }
                
                writer.print("+")
                for (x in 0 until width) writer.print("-")
                writer.println("+")
            }
            CobblemonDungeonDungeonsEngine.logger.info("DungeonLogger: Exported physical map to ${file.absolutePath}")
        } catch (e: Exception) {
            CobblemonDungeonDungeonsEngine.logger.error("DungeonLogger: Failed to write log file: ${e.message}")
        }
    }
}
