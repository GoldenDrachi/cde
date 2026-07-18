package net.drachi.cde.ai

import com.cobblemon.mod.common.api.events.CobblemonEvents
import net.drachi.cde.CDE
import net.drachi.cde.battleengine.battle.utility.HostilityState
import net.drachi.cde.battleengine.battle.utility.SpawnManager
import net.drachi.cde.battleengine.mixin.GoalSelectorAccessor
import net.drachi.cde.battleengine.mixin.MobAccessor

object AiModule {

    fun init() {
        CDE.logger.info("Initializing CDE AI Module")

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register { entity, level ->
            if (entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                injectAI(entity)
            }
        }
    }

    private fun injectAI(entity: com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
        val goalSel = (entity as MobAccessor).goalSelector
        val availableGoals = (goalSel as GoalSelectorAccessor).availableGoals
        
        // Prevent duplicate injection
        if (availableGoals.any { it.goal is net.drachi.cde.ai.HostileRealTimeGoal }) return

        if (entity.pokemon.getOwnerUUID() == null) {
            // Wild Pokemon Logic
            
            var state = SpawnManager.getEntityHostility(entity)
            
            if (state == null) {
                val manualStateStr = entity.pokemon.persistentData.getString("cde_hostility")
                state = if (manualStateStr == "hostile") {
                    HostilityState.HOSTILE
                } else if (manualStateStr == "peaceful") {
                    HostilityState.PEACEFUL
                } else if (manualStateStr == "neutral") {
                    HostilityState.NEUTRAL
                } else {
                    val dimId = entity.level().dimension().location().toString()
                    SpawnManager.getDimensionHostility(dimId)
                }
                SpawnManager.setEntityHostility(entity, state)
            }

            if (state != HostilityState.PEACEFUL) {
                val targetSel = (entity as MobAccessor).targetSelector
                
                // Clear vanilla attack and flee goals
                availableGoals.removeIf {
                    val name = it.goal.javaClass.simpleName.lowercase()
                    name.contains("attack") || name.contains("avoid") || name.contains("panic") || name.contains("flee")
                }
                
                goalSel.addGoal(0, net.drachi.cde.ai.HostileRealTimeGoal(entity))
                
                if (entity.pokemon.persistentData.getBoolean("cde_spawned")) {
                    // Remove standard wander goals to replace with dungeon wander
                    availableGoals.removeIf {
                        val name = it.goal.javaClass.simpleName.lowercase()
                        name.contains("stroll") || name.contains("wander")
                    }
                    goalSel.addGoal(1, net.drachi.cde.ai.DungeonPickupItemGoal(entity))
                    goalSel.addGoal(2, net.drachi.cde.ai.DungeonWanderGoal(entity))
                }
                
                if (state == HostilityState.HOSTILE) {
                    targetSel.addGoal(1, net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal(entity, net.minecraft.world.entity.player.Player::class.java, true))
                    targetSel.addGoal(2, net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal(entity, com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java, 10, true, false) { e ->
                        e is com.cobblemon.mod.common.entity.pokemon.PokemonEntity && e.pokemon.getOwnerUUID() != null
                    })
                } else {
                    targetSel.addGoal(1, net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(entity))
                }
            }
        } else {
            // Owned Pokemon Logic (Pet AI)
            val targetSel = (entity as MobAccessor).targetSelector
            
            // Clear vanilla attack and flee goals
            availableGoals.removeIf {
                val name = it.goal.javaClass.simpleName.lowercase()
                name.contains("attack") || name.contains("avoid") || name.contains("panic") || name.contains("flee")
            }

            goalSel.addGoal(0, net.drachi.cde.ai.HostileRealTimeGoal(entity))
            
            val dim = entity.level().dimension().location()
            if (dim.namespace == "cde" && dim.path == "dungeon") {
                goalSel.addGoal(2, net.drachi.cde.ai.DungeonFollowPlayerGoal(entity))
                goalSel.addGoal(3, net.drachi.cde.ai.DungeonWanderGoal(entity))
            }

            // HurtByTargetGoal so they defend themselves and the owner if attacked
            targetSel.addGoal(1, net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(entity))
            
            // OwnerHurtByTargetGoal & OwnerHurtTargetGoal to make them defend the player
            if (entity is net.minecraft.world.entity.TamableAnimal) {
                val tamable = entity as net.minecraft.world.entity.TamableAnimal
                targetSel.addGoal(2, net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal(tamable))
                targetSel.addGoal(3, net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal(tamable))
            }
            
            if (dim.namespace == "cde" && dim.path == "dungeon") {
                targetSel.addGoal(4, net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal(entity, com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java, 10, true, false) { e ->
                    e is com.cobblemon.mod.common.entity.pokemon.PokemonEntity && e.pokemon.getOwnerUUID() == null
                })
            }
        }
    }
}
