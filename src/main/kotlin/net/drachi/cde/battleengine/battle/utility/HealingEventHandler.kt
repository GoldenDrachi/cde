package net.drachi.cde.battleengine.battle.utility

import com.cobblemon.mod.common.api.events.CobblemonEvents
import net.drachi.cde.battleengine.battle.attack.PlayerCombatManager
import net.minecraft.server.level.ServerPlayer
import com.cobblemon.mod.common.Cobblemon
import net.minecraft.world.entity.player.Player
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.MinecraftServer

object HealingEventHandler {
    private var serverInstance: MinecraftServer? = null
    
    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            serverInstance = server
        }
        
        ServerLifecycleEvents.SERVER_STOPPED.register {
            serverInstance = null
        }
        
        CobblemonEvents.POKEMON_HEALED.subscribe { event ->
            val ownerId = event.pokemon.getOwnerUUID() ?: return@subscribe
            
            val server = serverInstance ?: return@subscribe
            val player = server.playerList.getPlayer(ownerId) ?: return@subscribe
            
            // If the active pokemon is fully healed, heal the player
            val active = PlayerCombatManager.getActivePokemon(player)
            if (active != null && event.pokemon.uuid == active.uuid) {
                if (active.currentHealth >= active.maxHealth) {
                    player.health = player.maxHealth
                }
            }
        }
    }
}
