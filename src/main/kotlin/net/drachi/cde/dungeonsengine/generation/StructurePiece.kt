package net.drachi.cde.dungeonsengine.generation

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.JigsawBlock
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate

/**
 * Represents an instantiated structure template placed (or proposed) in the world.
 * Pre-caches bounding box and parsed jigsaw data on construction.
 */
class StructurePiece(
    val template: StructureTemplate,
    val pos: BlockPos,
    val rotation: Rotation,
    val type: PieceType,
    val hasHazard: Boolean = false
) {
    val parsedJigsaws: List<ParsedJigsaw>
    val boundingBox: BoundingBox

    init {
        val settings = StructurePlaceSettings().setRotation(rotation)
        val transformedJigsaws = template.filterBlocks(BlockPos.ZERO, settings, Blocks.JIGSAW)
        
        this.parsedJigsaws = transformedJigsaws.map { ji ->
            ParsedJigsaw(pos.offset(ji.pos), ji.facing())
        }
        
        this.boundingBox = template.getBoundingBox(settings, pos)
    }

    /**
     * Strict interior-overlap check. Two boxes that share only a face/edge
     * (the Jigsaw seam between two adjacent pieces) do NOT count as intersecting.
     * Only genuine volume overlap (> 0 shared interior blocks) returns true.
     */
    fun intersects(other: StructurePiece): Boolean {
        val a = this.boundingBox
        val b = other.boundingBox
        return a.minX() < b.maxX() && a.maxX() > b.minX() &&
               a.minY() < b.maxY() && a.maxY() > b.minY() &&
               a.minZ() < b.maxZ() && a.maxZ() > b.minZ()
    }
}

/** Pre-parsed jigsaw: world-space position + horizontal facing direction. */
data class ParsedJigsaw(
    val pos: BlockPos,
    val facing: Direction
)

enum class PieceType {
    ROOM,
    HALLWAY,
    END
}

// ── Extension: parse the facing direction from a jigsaw StructureBlockInfo ──
/** Extracts the horizontal front direction from a Jigsaw block's `orientation` property. */
fun StructureTemplate.StructureBlockInfo.facing(): Direction {
    return state.getValue(JigsawBlock.ORIENTATION).front()
}
