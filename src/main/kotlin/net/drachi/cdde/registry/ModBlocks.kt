package net.drachi.cdde.registry

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour

object ModBlocks {

    val PALETTE_A = registerBlock("palette_a", Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)))
    val PALETTE_B = registerBlock("palette_b", Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)))
    val PALETTE_C = registerBlock("palette_c", Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)))
    val PALETTE_D = registerBlock("palette_d", Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)))
    val HAZARD = registerBlock("hazard", Block(BlockBehaviour.Properties.ofFullCopy(Blocks.MAGMA_BLOCK)))
    val POKEMON_SPAWN = registerBlock("pokemon_spawn", Block(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)))
    val ITEM_SPAWN = registerBlock("item_spawn", Block(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)))
    val TREASURE_SPAWN = registerBlock("treasure_spawn", Block(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)))
    val BOSS_SPAWN = registerBlock("boss_spawn", Block(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)))
    val MINION_SPAWN = registerBlock("minion_spawn", Block(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)))
    val TREASURE_DOOR = registerBlock("treasure_door", net.drachi.cdde.blocks.TreasureDoorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS)))
    val HAZARD_WATER = registerBlock("hazard_water", net.drachi.cdde.blocks.HazardBlock(net.drachi.cdde.mechanics.HazardType.WATER, BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).noCollission().noLootTable().noOcclusion()))
    val HAZARD_LAVA = registerBlock("hazard_lava", net.drachi.cdde.blocks.HazardBlock(net.drachi.cdde.mechanics.HazardType.LAVA, BlockBehaviour.Properties.ofFullCopy(Blocks.LAVA).noCollission().noLootTable().noOcclusion()))
    val HAZARD_VOID = registerBlock("hazard_void", net.drachi.cdde.blocks.HazardBlock(net.drachi.cdde.mechanics.HazardType.VOID, BlockBehaviour.Properties.ofFullCopy(Blocks.AIR).noCollission().noLootTable().noOcclusion()))


    val HAZARD_WALL_WATER = registerBlock("hazard_wall_water", net.drachi.cdde.blocks.HazardWallBlock(net.drachi.cdde.mechanics.HazardType.WATER, BlockBehaviour.Properties.ofFullCopy(Blocks.AIR).noLootTable().noOcclusion()))
    val HAZARD_WALL_LAVA = registerBlock("hazard_wall_lava", net.drachi.cdde.blocks.HazardWallBlock(net.drachi.cdde.mechanics.HazardType.LAVA, BlockBehaviour.Properties.ofFullCopy(Blocks.AIR).noLootTable().noOcclusion()))
    val HAZARD_WALL_VOID = registerBlock("hazard_wall_void", net.drachi.cdde.blocks.HazardWallBlock(net.drachi.cdde.mechanics.HazardType.VOID, BlockBehaviour.Properties.ofFullCopy(Blocks.AIR).noLootTable().noOcclusion()))


    private fun registerBlock(name: String, block: Block): Block {
        registerBlockItem(name, block)
        return Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath("cdde", name), block)
    }

    private fun registerBlockItem(name: String, block: Block): Item {
        return Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath("cdde", name),
            BlockItem(block, Item.Properties())
        )
    }

    fun register() {
        // Just loads the class to execute static field initializers
    }
}
