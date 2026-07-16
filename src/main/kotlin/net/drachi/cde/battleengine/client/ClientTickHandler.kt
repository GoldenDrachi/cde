package net.drachi.cde.battleengine.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

object ClientTickHandler {
    private var lastSlot = -1
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player
            if (player != null) {
                val currentSlot = com.cobblemon.mod.common.client.CobblemonClient.storage.selectedSlot
                if (currentSlot != lastSlot) {
                    lastSlot = currentSlot
                    player.refreshDimensions()
                }
            }
        }
    }
}
