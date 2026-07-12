package net.drachi.cdde.registry

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.drachi.cdde.CobblemonDungeonDungeonsEngine

object ModItems {

    val TREASURE_KEY = registerItem("treasure_key", Item(Item.Properties().stacksTo(64)))

    private fun registerItem(name: String, item: Item): Item {
        return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("cdde", name), item)
    }

    fun register() {
        CobblemonDungeonDungeonsEngine.logger.info("Registering Mod Items for CDDE")
    }
}
