package net.drachi.cdbe.battle.item

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*


import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.entity.LivingEntity
import net.minecraft.network.chat.Component

object ItemEffectHandler {
    
    /**
     * Checks items that trigger when the holder is hit by an attack.
     */
    fun onHitReceived(defenderStats: Pokemon, defenderEntity: LivingEntity, attackerEntity: LivingEntity, damage: Int, isSuperEffective: Boolean, attackType: String) {
        val heldItem = PokemonItemManager.getActiveHeldItem(defenderStats) ?: return
        
        val itemStrategy = net.drachi.cdbe.battle.item.ItemRegistry.getItem(heldItem)
        itemStrategy?.onHitReceived(defenderStats, defenderEntity, attackerEntity, damage, isSuperEffective, attackType)
    }
}
