package net.drachi.cde.dungeonsengine.generation

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.drachi.cde.dungeonsengine.registry.ModBlocks
import net.minecraft.core.BlockPos
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate

class PaletteProcessor(
    private val paletteAMap: BlockState,
    private val paletteBMap: BlockState,
    private val paletteCMap: BlockState,
    private val paletteDMap: BlockState,
    private val hazardMap: BlockState,
    private val hazardPositions: MutableList<BlockPos>? = null
) : StructureProcessor() {

    override fun processBlock(
        level: LevelReader,
        startPos: BlockPos,
        pos: BlockPos,
        blockInfoLocal: StructureTemplate.StructureBlockInfo,
        blockInfoGlobal: StructureTemplate.StructureBlockInfo,
        settings: StructurePlaceSettings
    ): StructureTemplate.StructureBlockInfo {
        val state = blockInfoGlobal.state
        val block = state.block
        val blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString()

        val newState = when (blockId) {
            "cde:palette_a" -> paletteAMap
            "cde:palette_b" -> paletteBMap
            "cde:palette_c" -> paletteCMap
            "cde:palette_d" -> paletteDMap
            "cde:hazard", "minecraft:lava", "minecraft:water" -> {
                hazardPositions?.add(blockInfoGlobal.pos)
                hazardMap
            }
            else -> return blockInfoGlobal
        }

        return StructureTemplate.StructureBlockInfo(blockInfoGlobal.pos, newState, blockInfoGlobal.nbt)
    }

    override fun getType(): StructureProcessorType<*> {
        return TYPE
    }

    companion object {
        val TYPE: StructureProcessorType<PaletteProcessor> = StructureProcessorType { 
            MapCodec.unit(PaletteProcessor(
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
            ))
        }
    }
}
