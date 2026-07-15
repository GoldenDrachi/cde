package net.drachi.cdde.registry

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object ModItemGroups {
    val CDDE_GROUP: CreativeModeTab = Registry.register(
        BuiltInRegistries.CREATIVE_MODE_TAB,
        ResourceLocation.fromNamespaceAndPath("cdde", "cdde_group"),
        FabricItemGroup.builder()
            .title(Component.translatable("itemgroup.cdde.cdde_group"))
            .icon { ItemStack(ModBlocks.PALETTE_A) }
            .displayItems { _, entries ->
                entries.accept(ModBlocks.PALETTE_A)
                entries.accept(ModBlocks.PALETTE_B)
                entries.accept(ModBlocks.PALETTE_C)
                entries.accept(ModBlocks.PALETTE_D)
                entries.accept(ModBlocks.HAZARD)
                entries.accept(ModBlocks.POKEMON_SPAWN)
                entries.accept(ModBlocks.ITEM_SPAWN)
                entries.accept(ModBlocks.TREASURE_SPAWN)
                entries.accept(ModBlocks.BOSS_SPAWN)
                entries.accept(ModBlocks.MINION_SPAWN)
                entries.accept(ModBlocks.END_STAIR_SPAWN)
                entries.accept(ModBlocks.PLAYER_SPAWN)
                entries.accept(ModBlocks.TREASURE_DOOR)
                entries.accept(ModBlocks.BOSS_DOOR)
                entries.accept(ModItems.TREASURE_KEY)
                entries.accept(Items.STRUCTURE_BLOCK)
                entries.accept(Items.JIGSAW)
            }
            .build()
    )

    fun register() {
        // Loads class to execute static init
    }
}
