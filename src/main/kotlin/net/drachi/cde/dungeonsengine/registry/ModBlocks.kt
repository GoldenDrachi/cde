package net.drachi.cde.dungeonsengine.registry

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.drachi.cde.dungeonsengine.blocks.DungeonPortalBlock

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
    val END_STAIR_SPAWN = registerBlock("end_stair_spawn", Block(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)))
    val PLAYER_SPAWN = registerBlock("player_spawn", Block(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)))
    val TREASURE_DOOR = registerBlock("treasure_door", net.drachi.cde.dungeonsengine.blocks.TreasureDoorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS)))
    val BOSS_DOOR = registerBlock("boss_door", net.drachi.cde.dungeonsengine.blocks.BossDoorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS)))
    val HAZARD_WATER = registerBlock("hazard_water", net.drachi.cde.dungeonsengine.blocks.HazardBlock(net.drachi.cde.dungeonsengine.mechanics.HazardType.WATER, BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).noCollission().noLootTable().noOcclusion()))
    val HAZARD_LAVA = registerBlock("hazard_lava", net.drachi.cde.dungeonsengine.blocks.HazardBlock(net.drachi.cde.dungeonsengine.mechanics.HazardType.LAVA, BlockBehaviour.Properties.ofFullCopy(Blocks.LAVA).noCollission().noLootTable().noOcclusion()))
    val HAZARD_VOID = registerBlock("hazard_void", net.drachi.cde.dungeonsengine.blocks.HazardBlock(net.drachi.cde.dungeonsengine.mechanics.HazardType.VOID, BlockBehaviour.Properties.ofFullCopy(Blocks.AIR).noCollission().noLootTable().noOcclusion()))


    val HAZARD_WALL_WATER = registerBlock("hazard_wall_water", net.drachi.cde.dungeonsengine.blocks.HazardWallBlock(net.drachi.cde.dungeonsengine.mechanics.HazardType.WATER, BlockBehaviour.Properties.ofFullCopy(Blocks.BARRIER).noLootTable().noOcclusion().isSuffocating { _, _, _ -> false }.isViewBlocking { _, _, _ -> false }))
    val HAZARD_WALL_LAVA = registerBlock("hazard_wall_lava", net.drachi.cde.dungeonsengine.blocks.HazardWallBlock(net.drachi.cde.dungeonsengine.mechanics.HazardType.LAVA, BlockBehaviour.Properties.ofFullCopy(Blocks.BARRIER).noLootTable().noOcclusion().isSuffocating { _, _, _ -> false }.isViewBlocking { _, _, _ -> false }))
    val HAZARD_WALL_VOID = registerBlock("hazard_wall_void", net.drachi.cde.dungeonsengine.blocks.HazardWallBlock(net.drachi.cde.dungeonsengine.mechanics.HazardType.VOID, BlockBehaviour.Properties.ofFullCopy(Blocks.BARRIER).noLootTable().noOcclusion().isSuffocating { _, _, _ -> false }.isViewBlocking { _, _, _ -> false }))


    val DUNGEON_PORTAL: Block = registerBlock(
        "dungeon_portal",
        DungeonPortalBlock(BlockBehaviour.Properties.of().noCollission().strength(-1.0f, 3600000.0f).noLootTable().noOcclusion().lightLevel { 11 })
    )

    private fun registerBlock(name: String, block: Block): Block {
        registerBlockItem(name, block)
        return Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath("cde", name), block)
    }

    private fun registerBlockItem(name: String, block: Block): Item {
        return Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath("cde", name),
            BlockItem(block, Item.Properties())
        )
    }

    fun register() {
        // Just loads the class to execute static field initializers
    }
}
