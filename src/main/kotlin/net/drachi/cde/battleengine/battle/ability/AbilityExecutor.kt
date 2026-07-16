package net.drachi.cde.battleengine.battle.ability

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.entity.LivingEntity
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB

object AbilityExecutor {

    fun executeOnSwitchIn(pokemon: Pokemon, entity: LivingEntity) {
        val eventData = AbilityRegistry.getAbility(pokemon.ability.name.lowercase())?.onSwitchIn ?: return
        executeAbilityPhase(pokemon, entity, eventData)
    }

    fun executeOnDamageDealt(pokemon: Pokemon, casterEntity: LivingEntity, targetEntity: LivingEntity, damage: Int) {
        val eventData = AbilityRegistry.getAbility(pokemon.ability.name.lowercase())?.onDamageDealt ?: return
        executeAbilityPhase(pokemon, casterEntity, eventData, targetEntity)
    }

    fun executeOnHitReceived(pokemon: Pokemon, targetEntity: LivingEntity, casterEntity: LivingEntity, damage: Int) {
        val eventData = AbilityRegistry.getAbility(pokemon.ability.name.lowercase())?.onHitReceived ?: return
        executeAbilityPhase(pokemon, targetEntity, eventData, casterEntity)
    }

    fun executeOnStatusApplied(pokemon: Pokemon, entity: LivingEntity, statusType: String) {
        val eventData = AbilityRegistry.getAbility(pokemon.ability.name.lowercase())?.onStatusApplied ?: return
        executeAbilityPhase(pokemon, entity, eventData)
    }

    private fun executeAbilityPhase(pokemon: Pokemon, casterEntity: LivingEntity, eventData: AbilityEventData, targetEntity: LivingEntity? = null) {
        val server = casterEntity.level().server ?: return
        val casterPlayer = if (casterEntity is ServerPlayer) casterEntity else {
            val ownerId = pokemon.getOwnerUUID()
            if (ownerId != null) server.playerList.getPlayer(ownerId) else null
        }
        
        // We need a dummy MoveTemplate since it's an ability
        // But AttackExecutor takes a MoveTemplate. We can use a dummy or modify AttackExecutor.
        // For simplicity, we just pass null if we can, but AttackExecutor requires a MoveTemplate.
        // So we might need a generic dummy template.
        val dummyTemplate = com.cobblemon.mod.common.api.moves.Moves.getByName("tackle") ?: return
        val dummyMove = RealTimeMove("cobblemon:tackle", 0f, phases = listOf(eventData.phase))

        if (casterPlayer != null) {
            AttackExecutor.executePhaseNow(casterPlayer, pokemon, dummyMove, eventData.phase, dummyTemplate, false, null)
        }
    }
}
