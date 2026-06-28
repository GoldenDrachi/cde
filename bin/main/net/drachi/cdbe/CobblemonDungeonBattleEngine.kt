package net.drachi.cdbe

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*

import net.drachi.cdbe.mixin.MobAccessor

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.healing.PokemonHealedEvent

class CobblemonDungeonBattleEngine : ModInitializer {
    companion object {
        const val MOD_ID = "cdbe"
        val LOGGER = LoggerFactory.getLogger(MOD_ID)
    }

    override fun onInitialize() {
        LOGGER.info("Initializing Cobblemon Dungeon Battle Engine")
        
        // Load the config file
        net.drachi.cdbe.config.ConfigManager.loadConfig()
        
        // Load realtime moves from config
        net.drachi.cdbe.battle.attack.MoveRegistry.load()
        
        // Load abilities
        net.drachi.cdbe.battle.ability.AbilityRegistry.load()
            
        // Register networking
        net.drachi.cdbe.network.NetworkHandler.registerPayloads()
        
        // Register game rules
        net.drachi.cdbe.config.GameRulesManager.register()

        // Register entities
        net.drachi.cdbe.registry.ModEntities.register()

        // Register commands
        net.drachi.cdbe.command.CommandManager.register()

        // Register server tick handler
        net.drachi.cdbe.battle.utility.CombatTickHandler.register()
        
        // Register healing sync handler
        net.drachi.cdbe.battle.utility.HealingEventHandler.register()
        
        // Register Healing Hook for Held Items and Statuses
        com.cobblemon.mod.common.api.events.CobblemonEvents.POKEMON_HEALED.subscribe { event ->
            net.drachi.cdbe.battle.item.PokemonItemManager.triggerItemReset(event.pokemon)
            
            // Clear statuses for the Pokemon's owner (if it belongs to a player)
            val ownerUuid = event.pokemon.getOwnerUUID()
            if (ownerUuid != null) {
                net.drachi.cdbe.battle.utility.CombatStateManager.clearVolatileStatuses(ownerUuid)
                net.drachi.cdbe.battle.utility.CombatStateManager.clearStatStages(ownerUuid)
            }
            
            // Clear statuses for the Pokemon entity itself
            net.drachi.cdbe.battle.utility.CombatStateManager.clearVolatileStatuses(event.pokemon.uuid)
            net.drachi.cdbe.battle.utility.CombatStateManager.clearStatStages(event.pokemon.uuid)
        }

        com.cobblemon.mod.common.api.events.CobblemonEvents.POKEMON_SENT_POST.subscribe { event ->
            val ownerUuid = event.pokemon.getOwnerUUID()
            if (ownerUuid != null) {
                val player = event.level.server.playerList.getPlayer(ownerUuid)
                if (player != null) {
                    val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
                    for (i in 0 until party.size()) {
                        if (party.get(i)?.uuid == event.pokemon.uuid) {
                            net.drachi.cdbe.battle.attack.PlayerCombatManager.setActivePokemon(player, i)
                            net.drachi.cdbe.battle.ability.AbilityExecutor.executeOnSwitchIn(event.pokemon, player)
                            break
                        }
                    }
                }
            }
        }
        
        com.cobblemon.mod.common.api.events.CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe { event ->
            if (event.entity.pokemon.getOwnerUUID() == null) {
                net.drachi.cdbe.battle.ability.AbilityExecutor.executeOnSwitchIn(event.entity.pokemon, event.entity)
                
                var state = net.drachi.cdbe.battle.utility.SpawnManager.getEntityHostility(event.entity)
                
                if (state == null) {
                    val manualStateStr = event.entity.pokemon.persistentData.getString("cdbe_hostility")
                    state = if (manualStateStr == "hostile") {
                        net.drachi.cdbe.battle.utility.HostilityState.HOSTILE
                    } else if (manualStateStr == "peaceful") {
                        net.drachi.cdbe.battle.utility.HostilityState.PEACEFUL
                    } else if (manualStateStr == "neutral") {
                        net.drachi.cdbe.battle.utility.HostilityState.NEUTRAL
                    } else {
                        val dimId = event.entity.level().dimension().location().toString()
                        net.drachi.cdbe.battle.utility.SpawnManager.getDimensionHostility(dimId)
                    }
                    net.drachi.cdbe.battle.utility.SpawnManager.setEntityHostility(event.entity, state)
                }

                if (state != net.drachi.cdbe.battle.utility.HostilityState.PEACEFUL) {
                    val goalSel = (event.entity as net.drachi.cdbe.mixin.MobAccessor).goalSelector
                    val targetSel = (event.entity as net.drachi.cdbe.mixin.MobAccessor).targetSelector
                    
                    // Clear vanilla attack and flee goals
                    val availableGoals = (goalSel as net.drachi.cdbe.mixin.GoalSelectorAccessor).availableGoals
                    availableGoals.removeIf {
                        val name = it.goal.javaClass.simpleName.lowercase()
                        name.contains("attack") || name.contains("avoid") || name.contains("panic") || name.contains("flee")
                    }
                    
                    goalSel.addGoal(0, net.drachi.cdbe.battle.ai.HostileRealTimeGoal(event.entity))
                    
                    if (state == net.drachi.cdbe.battle.utility.HostilityState.HOSTILE) {
                        targetSel.addGoal(1, net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal(event.entity, net.minecraft.world.entity.player.Player::class.java, true))
                        targetSel.addGoal(2, net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal(event.entity, com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java, 10, true, false) { entity ->
                            entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity && entity.pokemon.getOwnerUUID() != null
                        })
                    } else {
                        (event.entity as net.drachi.cdbe.mixin.MobAccessor).targetSelector.addGoal(1, net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(event.entity))
                    }
                }
            } else {
                // Owned Pokemon Logic (Pet AI)
                val goalSel = (event.entity as net.drachi.cdbe.mixin.MobAccessor).goalSelector
                val targetSel = (event.entity as net.drachi.cdbe.mixin.MobAccessor).targetSelector
                
                // Clear vanilla attack and flee goals
                val availableGoals = (goalSel as net.drachi.cdbe.mixin.GoalSelectorAccessor).availableGoals
                availableGoals.removeIf {
                    val name = it.goal.javaClass.simpleName.lowercase()
                    name.contains("attack") || name.contains("avoid") || name.contains("panic") || name.contains("flee")
                }

                goalSel.addGoal(0, net.drachi.cdbe.battle.ai.HostileRealTimeGoal(event.entity))
                // HurtByTargetGoal so they defend themselves and the owner if attacked
                targetSel.addGoal(1, net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(event.entity))
                
                // OwnerHurtByTargetGoal & OwnerHurtTargetGoal to make them defend the player
                if (event.entity is net.minecraft.world.entity.TamableAnimal) {
                    val tamable = event.entity as net.minecraft.world.entity.TamableAnimal
                    targetSel.addGoal(2, net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal(tamable))
                    targetSel.addGoal(3, net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal(tamable))
                }
            }

        }

        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register { entity, damageSource, damageAmount ->
            if (entity is net.minecraft.server.level.ServerPlayer) {
                val activeMon = net.drachi.cdbe.battle.attack.PlayerCombatManager.getActivePokemon(entity)
                if (activeMon != null) {
                    activeMon.currentHealth = 0
                    val switched = net.drachi.cdbe.battle.attack.PlayerCombatManager.handleAutoSwitch(entity)
                    if (switched) {
                        // Prevent death! We switched to a new pokemon.
                        // The health will be updated to the new pokemon's health in setActivePokemon.
                        return@register false
                    }
                }
            }
            true
        }
        
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
            if (!world.isClientSide && player is net.minecraft.server.level.ServerPlayer && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
                val activeMon = net.drachi.cdbe.battle.attack.PlayerCombatManager.getActivePokemon(player)
                if (activeMon != null) {
                    // Check cooldown
                    if (!net.drachi.cdbe.battle.attack.PlayerCombatManager.canCast(player, -1, "cobblemon:neutral_attack")) {
                        return@register net.minecraft.world.InteractionResult.FAIL
                    }
                    
                    // Execute neutral attack
                    val rtMove = net.drachi.cdbe.battle.attack.MoveRegistry.getMove("cobblemon:neutral_attack")
                    val dummyTemplate = com.cobblemon.mod.common.api.moves.Moves.getByName("tackle")
                    if (rtMove != null && rtMove.phases.isNotEmpty() && dummyTemplate != null) {
                        net.drachi.cdbe.battle.attack.AttackExecutor.executePhaseNow(player, activeMon, rtMove, rtMove.phases[0], dummyTemplate, false, null)
                        net.drachi.cdbe.battle.attack.PlayerCombatManager.markCast(player, -1, "cobblemon:neutral_attack", rtMove.cooldownTurns, 500L)
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
                if (player != null && net.drachi.cdbe.battle.attack.PlayerCombatManager.getActivePokemon(player)?.uuid == pokemon.uuid) {
                    val maxHp = pokemon.maxHealth.toFloat() * net.drachi.cdbe.config.ConfigManager.config.hpMultiplier
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
