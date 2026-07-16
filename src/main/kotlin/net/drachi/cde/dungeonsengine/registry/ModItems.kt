package net.drachi.cde.dungeonsengine.registry

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.drachi.cde.CDE

object ModItems {

    val TREASURE_KEY = registerItem("treasure_key", Item(Item.Properties().stacksTo(64)))

    private fun registerItem(name: String, item: Item): Item {
        return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("cde", name), item)
    }

    fun register() {
        CDE.logger.info("Registering Mod Items for cde")
    }
}
