package net.drachi.cdde.client

import com.cobblemon.mod.common.client.CobblemonClient
import net.drachi.cdde.network.NetworkHandler
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
import net.minecraft.client.renderer.RenderType
import net.drachi.cdde.registry.ModBlocks

class CDDEClient : ClientModInitializer {
    private var lastSelectedSlot: Int = -1

    override fun onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.level != null && client.player != null) {
                val currentSlot = CobblemonClient.storage.selectedSlot
                if (currentSlot != lastSelectedSlot) {
                    if (ClientPlayNetworking.canSend(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cdde", "main"))) {
                        lastSelectedSlot = currentSlot
                        NetworkHandler.sendSyncSelectedSlot(currentSlot)
                    }
                }
            }
        }


        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.HAZARD_WATER, RenderType.translucent())
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.HAZARD_LAVA, RenderType.translucent())
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.HAZARD_VOID, RenderType.translucent())
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.DUNGEON_PORTAL, RenderType.translucent())

        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register({ state, world, pos, tintIndex ->
            if (world != null && pos != null && tintIndex == 0) {
                val be = world.getBlockEntity(pos) as? net.drachi.cdde.blocks.DungeonPortalBlockEntity
                if (be != null) {
                    val config = net.drachi.cdde.data.DungeonManager.configs[be.configId]
                    if (config != null) return@register config.portalColor
                    return@register be.colorHex
                }
            }
            0x800080 // Default purple fallback
        }, ModBlocks.DUNGEON_PORTAL)

        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register({ stack, tintIndex ->
            if (tintIndex == 0) {
                val data = stack.getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.CustomData.EMPTY)
                if (!data.isEmpty) {
                    val tag = data.copyTag()
                    if (tag.contains("ColorHex")) {
                        return@register tag.getInt("ColorHex")
                    }
                }
            }
            0x800080 // Default purple
        }, ModBlocks.DUNGEON_PORTAL)

        NetworkHandler.CHANNEL.registerClientbound(net.drachi.cdde.network.OpenConfigScreenPacket::class.java) { payload, context ->
            val client = net.minecraft.client.Minecraft.getInstance()
            client.execute {
                val parsedConfig = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<net.drachi.cdde.data.DungeonConfig>(payload.configJson)
                client.setScreen(net.drachi.cdde.client.ConfigEditorScreen(parsedConfig))
            }
        }
    }
}
