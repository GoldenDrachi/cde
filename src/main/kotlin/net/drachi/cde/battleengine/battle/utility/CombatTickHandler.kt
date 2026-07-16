package net.drachi.cde.battleengine.battle.utility

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.drachi.cde.network.NetworkHandler

object CombatTickHandler {
    private var tickCount = 0
    private var turnTickCount = 0

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { server ->
            onServerTick(server)
        })
    }

    private fun onServerTick(server: MinecraftServer) {
        tickCount++
        
        DomainManager.tick(server)
        DelayedActionManager.tick(server)
        HazardManager.tick(server)
        
        turnTickCount++
        val turnTicks = (net.drachi.cde.battleengine.config.BattleEngineConfigManager.config.turnToSecondsRatio * 20).toInt()
        if (turnTickCount >= turnTicks) {
            turnTickCount = 0
            processStatusEffects(server)
        }

        if (tickCount % 5 == 0) {
            spawnStatusParticles(server)
        }

        if (tickCount >= 20) {
            tickCount = 0
            
            for (player in server.playerList.players) {
                PlayerCombatManager.syncHealthFromPlayer(player)
                
                // 1. Sync the player's own stats
                val playerStats = CombatStateManager.getActiveStats(player.uuid)
                NetworkHandler.sendSyncEntityStats(player, player.id, playerStats)

                // 2. Sync the locked target's stats (if any)
                val targetId = PlayerCombatManager.getLockedTarget(player)
                if (targetId != -1) {
                    val target = player.level().getEntity(targetId)
                    if (target != null) {
                        val targetStats = CombatStateManager.getActiveStats(target.uuid)
                        NetworkHandler.sendSyncEntityStats(player, target.id, targetStats)
                    } else {
                        // Target no longer exists or isn't loaded
                        PlayerCombatManager.toggleTargetLock(player) // Clear it
                    }
                }

                // 3. Status Actionbar
                val subHp = CombatStateManager.getSubstituteHp(player.uuid)
                if (subHp != null) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("cdbe.message.substitute_hp", subHp).withStyle(net.minecraft.ChatFormatting.GREEN), true)
                }

                // 4. Sync Weather
                val weather = DomainManager.getActiveWeatherCondition(player) ?: ""
                NetworkHandler.sendSyncWeather(player, weather)
            }
        }
    }
    
    private fun processStatusEffects(server: MinecraftServer) {
        StatusEffectHandler.processPeriodicDamage(server)
        processPeriodicItems(server)
    }

    private fun processPeriodicItems(server: MinecraftServer) {
        val config = net.drachi.cde.battleengine.config.BattleEngineConfigManager.config

        for (player in server.playerList.players) {
            val activeMon = PlayerCombatManager.getActivePokemon(player) ?: continue
            val heldItem = PokemonItemManager.getActiveHeldItem(activeMon) ?: continue

            val itemStrategy = net.drachi.cde.battleengine.battle.item.ItemRegistry.getItem(heldItem)
            itemStrategy?.onTurnTick(player, activeMon, config)
        }
    }

    private fun spawnStatusParticles(server: MinecraftServer) {
        StatusEffectHandler.spawnStatusParticles(server)
    }
}
