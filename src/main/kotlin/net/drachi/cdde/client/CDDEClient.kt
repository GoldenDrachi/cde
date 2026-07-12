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
                    if (ClientPlayNetworking.canSend(net.drachi.cdde.network.SyncSelectedSlotPayload.ID)) {
                        lastSelectedSlot = currentSlot
                        NetworkHandler.sendSyncSelectedSlot(currentSlot)
                    }
                }
            }
        }


        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.HAZARD_WATER, RenderType.translucent())
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.HAZARD_LAVA, RenderType.translucent())
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.HAZARD_VOID, RenderType.translucent())
    }
}
