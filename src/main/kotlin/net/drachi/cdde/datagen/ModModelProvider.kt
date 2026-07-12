package net.drachi.cdde.datagen

import net.drachi.cdde.registry.ModBlocks
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider
import net.minecraft.data.models.BlockModelGenerators
import net.minecraft.data.models.ItemModelGenerators

class ModModelProvider(output: FabricDataOutput) : FabricModelProvider(output) {
    override fun generateBlockStateModels(blockStateModelGenerator: BlockModelGenerators) {
        blockStateModelGenerator.createTrivialCube(ModBlocks.PALETTE_A)
        blockStateModelGenerator.createTrivialCube(ModBlocks.PALETTE_B)
        blockStateModelGenerator.createTrivialCube(ModBlocks.PALETTE_C)
        blockStateModelGenerator.createTrivialCube(ModBlocks.PALETTE_D)
        blockStateModelGenerator.createTrivialCube(ModBlocks.HAZARD)
        blockStateModelGenerator.createTrivialCube(ModBlocks.POKEMON_SPAWN)
        blockStateModelGenerator.createTrivialCube(ModBlocks.ITEM_SPAWN)
        blockStateModelGenerator.createTrivialCube(ModBlocks.TREASURE_SPAWN)
        blockStateModelGenerator.createTrivialCube(ModBlocks.BOSS_SPAWN)
        blockStateModelGenerator.createTrivialCube(ModBlocks.MINION_SPAWN)
        blockStateModelGenerator.createTrivialCube(ModBlocks.TREASURE_DOOR)
        blockStateModelGenerator.createTrivialCube(ModBlocks.HAZARD_WALL_WATER)
        blockStateModelGenerator.createTrivialCube(ModBlocks.HAZARD_WALL_LAVA)
        blockStateModelGenerator.createTrivialCube(ModBlocks.HAZARD_WALL_VOID)
    }

    override fun generateItemModels(itemModelGenerator: ItemModelGenerators) {
        // Automatically handled by createTrivialCube for simple blocks
        itemModelGenerator.generateFlatItem(net.drachi.cdde.registry.ModItems.TREASURE_KEY, net.minecraft.data.models.model.ModelTemplates.FLAT_ITEM)
    }
}
