package net.drachi.cdbe.battle.item

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*

import net.minecraft.server.level.ServerPlayer

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.entity.LivingEntity
import net.minecraft.network.chat.Component

class EjectButtonItem : BattleItem {
    override val itemId = "cobblemon:eject_button"

    override fun onHitReceived(defenderStats: Pokemon, defenderEntity: LivingEntity, attackerEntity: LivingEntity, damage: Int, isSuperEffective: Boolean, attackType: String) {
        if (damage > 0) {
            PokemonItemManager.consumeItem(defenderStats, itemId)
            val toAttacker = attackerEntity.position().subtract(defenderEntity.position()).normalize()
            val pushForce = 1.5
            defenderEntity.deltaMovement = defenderEntity.deltaMovement.add(toAttacker.multiply(-pushForce, -pushForce, -pushForce))
            defenderEntity.hurtMarked = true
            if (defenderEntity is net.minecraft.server.level.ServerPlayer) {
                defenderEntity.sendSystemMessage(Component.translatable("cdbe.message.eject_button").withStyle(net.minecraft.ChatFormatting.YELLOW))
            }
        }
    }
}

class WeaknessPolicyItem : BattleItem {
    override val itemId = "cobblemon:weakness_policy"

    override fun onHitReceived(defenderStats: Pokemon, defenderEntity: LivingEntity, attackerEntity: LivingEntity, damage: Int, isSuperEffective: Boolean, attackType: String) {
        if (isSuperEffective && damage > 0) {
            PokemonItemManager.consumeItem(defenderStats, itemId)
            CombatStateManager.applyStatChange(defenderEntity.uuid, "attack", 2, -1)
            CombatStateManager.applyStatChange(defenderEntity.uuid, "special_attack", 2, -1)
            if (defenderEntity is net.minecraft.server.level.ServerPlayer) {
                defenderEntity.sendSystemMessage(Component.translatable("cdbe.message.weakness_policy").withStyle(net.minecraft.ChatFormatting.YELLOW))
            }
        }
    }
}

class AbsorbBulbItem : BattleItem {
    override val itemId = "cobblemon:absorb_bulb"

    override fun onHitReceived(defenderStats: Pokemon, defenderEntity: LivingEntity, attackerEntity: LivingEntity, damage: Int, isSuperEffective: Boolean, attackType: String) {
        if (attackType == "water" && damage > 0) {
            PokemonItemManager.consumeItem(defenderStats, itemId)
            CombatStateManager.applyStatChange(defenderEntity.uuid, "special_attack", 1, -1)
            if (defenderEntity is net.minecraft.server.level.ServerPlayer) {
                defenderEntity.sendSystemMessage(Component.translatable("cdbe.message.absorb_bulb").withStyle(net.minecraft.ChatFormatting.YELLOW))
            }
        }
    }
}

class CellBatteryItem : BattleItem {
    override val itemId = "cobblemon:cell_battery"

    override fun onHitReceived(defenderStats: Pokemon, defenderEntity: LivingEntity, attackerEntity: LivingEntity, damage: Int, isSuperEffective: Boolean, attackType: String) {
        if (attackType == "electric" && damage > 0) {
            PokemonItemManager.consumeItem(defenderStats, itemId)
            CombatStateManager.applyStatChange(defenderEntity.uuid, "attack", 1, -1)
            if (defenderEntity is net.minecraft.server.level.ServerPlayer) {
                defenderEntity.sendSystemMessage(Component.translatable("cdbe.message.cell_battery").withStyle(net.minecraft.ChatFormatting.YELLOW))
            }
        }
    }
}

class RedCardItem : BattleItem {
    override val itemId = "cobblemon:red_card"

    override fun onHitReceived(defenderStats: Pokemon, defenderEntity: LivingEntity, attackerEntity: LivingEntity, damage: Int, isSuperEffective: Boolean, attackType: String) {
        if (damage > 0) {
            PokemonItemManager.consumeItem(defenderStats, itemId)
            val toDefender = defenderEntity.position().subtract(attackerEntity.position()).normalize()
            val pushForce = 1.5
            attackerEntity.deltaMovement = attackerEntity.deltaMovement.add(toDefender.multiply(-pushForce, -pushForce, -pushForce))
            attackerEntity.hurtMarked = true
            if (defenderEntity is net.minecraft.server.level.ServerPlayer) {
                defenderEntity.sendSystemMessage(Component.translatable("cdbe.message.red_card").withStyle(net.minecraft.ChatFormatting.YELLOW))
            }
        }
    }
}
