package net.drachi.cde.battleengine.client

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import com.mojang.blaze3d.platform.InputConstants
import net.drachi.cde.network.NetworkHandler
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW

object KeybindManager {
    lateinit var move1Key: KeyMapping
    lateinit var move2Key: KeyMapping
    lateinit var move3Key: KeyMapping
    lateinit var move4Key: KeyMapping
    lateinit var targetKey: KeyMapping

    fun register() {
        val category = "key.categories.cdbe"

        move1Key = KeyBindingHelper.registerKeyBinding(KeyMapping("key.cdbe.move1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, category))
        move2Key = KeyBindingHelper.registerKeyBinding(KeyMapping("key.cdbe.move2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, category))
        move3Key = KeyBindingHelper.registerKeyBinding(KeyMapping("key.cdbe.move3", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category))
        move4Key = KeyBindingHelper.registerKeyBinding(KeyMapping("key.cdbe.move4", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, category))
        targetKey = KeyBindingHelper.registerKeyBinding(KeyMapping("key.cdbe.target", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, category))

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client ->
            if (client.player == null) return@EndTick

            // Fetch the actual selected slot from the client's party UI
            val selectedPartySlot = com.cobblemon.mod.common.client.CobblemonClient.storage.selectedSlot 
            if (selectedPartySlot < 0) return@EndTick

            if (move1Key.consumeClick()) NetworkHandler.sendCastMove(selectedPartySlot, 0)
            if (move2Key.consumeClick()) NetworkHandler.sendCastMove(selectedPartySlot, 1)
            if (move3Key.consumeClick()) NetworkHandler.sendCastMove(selectedPartySlot, 2)
            if (move4Key.consumeClick()) NetworkHandler.sendCastMove(selectedPartySlot, 3)
            if (targetKey.consumeClick()) NetworkHandler.sendToggleTarget()
        })
    }
}
