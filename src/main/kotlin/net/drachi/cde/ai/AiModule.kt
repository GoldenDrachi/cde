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

        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe { event ->
            if (event.entity.pokemon.getOwnerUUID() == null) {
                // Wild Pokemon Logic
                
                // Note: AbilityExecutor.executeOnSwitchIn might technically belong to BattleEngine 
                // but since we are modifying AI goals here, we just run the hostility checks.
                var state = SpawnManager.getEntityHostility(event.entity)
                
                if (state == null) {
                    val manualStateStr = event.entity.pokemon.persistentData.getString("cde_hostility")
                    state = if (manualStateStr == "hostile") {
                        HostilityState.HOSTILE
                    } else if (manualStateStr == "peaceful") {
                        HostilityState.PEACEFUL
                    } else if (manualStateStr == "neutral") {
                        HostilityState.NEUTRAL
                    } else {
                        val dimId = event.entity.level().dimension().location().toString()
                        SpawnManager.getDimensionHostility(dimId)
                    }
                    SpawnManager.setEntityHostility(event.entity, state)
                }

                if (state != HostilityState.PEACEFUL) {
                    val goalSel = (event.entity as MobAccessor).goalSelector
                    val targetSel = (event.entity as MobAccessor).targetSelector
                    
                    // Clear vanilla attack and flee goals
                    val availableGoals = (goalSel as GoalSelectorAccessor).availableGoals
                    availableGoals.removeIf {
                        val name = it.goal.javaClass.simpleName.lowercase()
                        name.contains("attack") || name.contains("avoid") || name.contains("panic") || name.contains("flee")
                    }
                    
                    goalSel.addGoal(0, net.drachi.cde.ai.HostileRealTimeGoal(event.entity))
                    
                    if (state == HostilityState.HOSTILE) {
                        targetSel.addGoal(1, net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal(event.entity, net.minecraft.world.entity.player.Player::class.java, true))
                        targetSel.addGoal(2, net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal(event.entity, com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java, 10, true, false) { entity ->
                            entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity && entity.pokemon.getOwnerUUID() != null
                        })
                    } else {
                        targetSel.addGoal(1, net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(event.entity))
                    }
                }
            } else {
                // Owned Pokemon Logic (Pet AI)
                val goalSel = (event.entity as MobAccessor).goalSelector
                val targetSel = (event.entity as MobAccessor).targetSelector
                
                // Clear vanilla attack and flee goals
                val availableGoals = (goalSel as GoalSelectorAccessor).availableGoals
                availableGoals.removeIf {
                    val name = it.goal.javaClass.simpleName.lowercase()
                    name.contains("attack") || name.contains("avoid") || name.contains("panic") || name.contains("flee")
                }

                goalSel.addGoal(0, net.drachi.cde.ai.HostileRealTimeGoal(event.entity))
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
    }
}
