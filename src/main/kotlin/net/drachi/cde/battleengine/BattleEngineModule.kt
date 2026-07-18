package net.drachi.cde.battleengine

import net.drachi.cde.CDE
import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*
import net.drachi.cde.battleengine.mixin.MobAccessor
import net.drachi.cde.battleengine.mixin.GoalSelectorAccessor
import com.cobblemon.mod.common.api.events.CobblemonEvents

/**
 * Server-side module for the real-time battle engine.
 * Registers all event handlers, networking, and subsystems.
 * Called conditionally from CDE.onInitialize() based on module config.
 */
object BattleEngineModule {

    fun init() {
        CDE.logger.info("Initializing Cobblemon Dungeon Battle Engine")
        
        // Load the config file
        net.drachi.cde.battleengine.config.BattleEngineConfigManager.loadConfig()
        
        // Load realtime moves from config
        net.drachi.cde.battleengine.battle.attack.MoveRegistry.load()
        
        // Load weather templates
        net.drachi.cde.battleengine.battle.weather.WeatherRegistry.load()
        
        // Load abilities
        net.drachi.cde.battleengine.battle.ability.AbilityRegistry.load()
        
        // Register game rules
        net.drachi.cde.battleengine.config.GameRulesManager.register()

        // Register entities
        net.drachi.cde.battleengine.registry.ModEntities.register()

        // Register commands
        net.drachi.cde.battleengine.command.CommandManager.register()

        // Register server tick handler
        net.drachi.cde.battleengine.battle.utility.CombatTickHandler.register()
        
        // Register healing sync handler
        net.drachi.cde.battleengine.battle.utility.HealingEventHandler.register()
        
        // Register Healing Hook for Held Items and Statuses
        CobblemonEvents.POKEMON_HEALED.subscribe { event ->
            net.drachi.cde.battleengine.battle.item.PokemonItemManager.triggerItemReset(event.pokemon)
            
            // Clear statuses for the Pokemon's owner (if it belongs to a player)
            val ownerUuid = event.pokemon.getOwnerUUID()
            if (ownerUuid != null) {
                net.drachi.cde.battleengine.battle.utility.CombatStateManager.clearVolatileStatuses(ownerUuid)
                net.drachi.cde.battleengine.battle.utility.CombatStateManager.clearStatStages(ownerUuid)
            }
            
            // Clear statuses for the Pokemon entity itself
            net.drachi.cde.battleengine.battle.utility.CombatStateManager.clearVolatileStatuses(event.pokemon.uuid)
            net.drachi.cde.battleengine.battle.utility.CombatStateManager.clearStatStages(event.pokemon.uuid)
        }
        
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe { event ->
            if (net.drachi.cde.battleengine.config.BattleEngineConfigManager.config.isRealtimeEnabled) {
                val anyEntity = event.battle.actors.flatMap { it.pokemonList }.mapNotNull { it.entity }.firstOrNull()
                if (anyEntity != null) {
                    val dimId = anyEntity.level().dimension().location().toString()
                    val dimState = net.drachi.cde.battleengine.battle.utility.SpawnManager.getDimensionHostility(dimId)
                    if (dimState != net.drachi.cde.battleengine.battle.utility.HostilityState.PEACEFUL) {
                        event.cancel()
                        event.reason = net.minecraft.network.chat.Component.literal("Turn-based battling is disabled in real-time dimensions!")
                    }
                }
            }
        }

        CobblemonEvents.POKEMON_SENT_POST.subscribe { event ->
            val ownerUuid = event.pokemon.getOwnerUUID()
            if (ownerUuid != null) {
                val player = event.level.server.playerList.getPlayer(ownerUuid)
                if (player != null) {
                    val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
                    for (i in 0 until party.size()) {
                        if (party.get(i)?.uuid == event.pokemon.uuid) {
                            net.drachi.cde.battleengine.battle.attack.PlayerCombatManager.setActivePokemon(player, i)
                            net.drachi.cde.battleengine.battle.ability.AbilityExecutor.executeOnSwitchIn(event.pokemon, player)
                            break
                        }
                    }
                }
            }
        }
        
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe { event ->
            if (event.entity.pokemon.getOwnerUUID() == null) {
                net.drachi.cde.battleengine.battle.ability.AbilityExecutor.executeOnSwitchIn(event.entity.pokemon, event.entity)
            }
        }

        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register { entity, damageSource, damageAmount ->
            if (entity is net.minecraft.server.level.ServerPlayer) {
                val activeMon = PlayerCombatManager.getActivePokemon(entity)
                if (activeMon != null) {
                    activeMon.currentHealth = 0
                    val switched = PlayerCombatManager.handleAutoSwitch(entity)
                    if (switched) {
                        return@register false
                    }
                }
            }
            true
        }
        
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
            if (!world.isClientSide && player is net.minecraft.server.level.ServerPlayer && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
                val activeMon = PlayerCombatManager.getActivePokemon(player)
                if (activeMon != null) {
                    if (!PlayerCombatManager.canCast(player, -1, "cobblemon:neutral_attack")) {
                        return@register net.minecraft.world.InteractionResult.FAIL
                    }
                    
                    val rtMove = MoveRegistry.getMove("cobblemon:neutral_attack")
                    val dummyTemplate = com.cobblemon.mod.common.api.moves.Moves.getByName("tackle")
                    if (rtMove != null && rtMove.phases.isNotEmpty() && dummyTemplate != null) {
                        AttackExecutor.executePhaseNow(player, activeMon, rtMove, rtMove.phases[0], dummyTemplate, false, null)
                        PlayerCombatManager.markCast(player, -1, "cobblemon:neutral_attack", rtMove.cooldownTurns, 500L)
                        return@register net.minecraft.world.InteractionResult.SUCCESS
                    }
                }
            }
            net.minecraft.world.InteractionResult.PASS
        }

        CobblemonEvents.POKEMON_HEALED.subscribe { event ->
            val pokemon = event.pokemon
            val ownerUUID = pokemon.getOwnerUUID()
            if (ownerUUID != null) {
                val server = net.fabricmc.loader.api.FabricLoader.getInstance().gameInstance as? net.minecraft.server.MinecraftServer
                val player = server?.playerList?.getPlayer(ownerUUID)
                if (player != null && PlayerCombatManager.getActivePokemon(player)?.uuid == pokemon.uuid) {
                    val maxHp = pokemon.maxHealth.toFloat() * net.drachi.cde.battleengine.config.BattleEngineConfigManager.config.hpMultiplier
                    if (event.isFullHeal()) {
                        player.health = player.maxHealth
                    } else {
                        val healRatio = event.amount.toFloat() / pokemon.maxHealth.toFloat()
                        player.heal(player.maxHealth * healRatio)
                    }
                }
            }
        }
    }
}
