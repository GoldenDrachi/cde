package net.drachi.cdde.registry

import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntityType
import net.drachi.cdde.blocks.DungeonPortalBlockEntity

object ModBlockEntities {
    
    val DUNGEON_PORTAL: BlockEntityType<DungeonPortalBlockEntity> = Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("cdde", "dungeon_portal"),
        FabricBlockEntityTypeBuilder.create(::DungeonPortalBlockEntity, ModBlocks.DUNGEON_PORTAL).build()
    )

    fun register() {
        // Just calling to initialize the class
    }
}
