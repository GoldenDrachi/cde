package net.drachi.cdbe.battle.item

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*

import net.minecraft.server.level.ServerPlayer

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.network.chat.Component
import net.drachi.cdbe.config.BattleEngineConfig

class LeftoversItem : BattleItem {
    override val itemId = "cobblemon:leftovers"

    override fun onTurnTick(player: ServerPlayer, mon: Pokemon, config: BattleEngineConfig) {
        val heal = if (config.useFlatLeftoversHeal) config.flatLeftoversHealAmount else (mon.maxHealth * (config.percentLeftoversHealAmount / 100.0f)).toInt()
        val newHp = (mon.currentHealth + heal).coerceIn(0, mon.maxHealth)
        if (newHp > mon.currentHealth) {
            mon.currentHealth = newHp
            player.displayClientMessage(Component.translatable("cdbe.message.leftovers", mon.species.name), false)
        }
    }
}

class BlackSludgeItem : BattleItem {
    override val itemId = "cobblemon:black_sludge"

    override fun onTurnTick(player: ServerPlayer, mon: Pokemon, config: BattleEngineConfig) {
        val isPoison = mon.primaryType?.name?.lowercase() == "poison" || mon.secondaryType?.name?.lowercase() == "poison"
        if (isPoison) {
            val heal = if (config.useFlatLeftoversHeal) config.flatLeftoversHealAmount else (mon.maxHealth * (config.percentLeftoversHealAmount / 100.0f)).toInt()
            val newHp = (mon.currentHealth + heal).coerceIn(0, mon.maxHealth)
            if (newHp > mon.currentHealth) {
                mon.currentHealth = newHp
                player.displayClientMessage(Component.translatable("cdbe.message.black_sludge_heal", mon.species.name), false)
            }
        } else {
            val dmg = (mon.maxHealth * 0.125f).toInt().coerceAtLeast(1)
            val newHp = mon.currentHealth - dmg
            mon.currentHealth = if (newHp < 0) 0 else newHp
            player.displayClientMessage(Component.translatable("cdbe.message.black_sludge_hurt", mon.species.name), false)
        }
    }
}

class FlameOrbItem : BattleItem {
    override val itemId = "cobblemon:flame_orb"

    override fun onTurnTick(player: ServerPlayer, mon: Pokemon, config: BattleEngineConfig) {
        if (!CombatStateManager.hasVolatileStatus(player.uuid, "burn") && mon.status?.status?.name?.path != "burn") {
            CombatStateManager.applyVolatileStatus(player.uuid, "burn", -1)
            player.displayClientMessage(Component.translatable("cdbe.message.flame_orb", mon.species.name), false)
        }
    }
}

class ToxicOrbItem : BattleItem {
    override val itemId = "cobblemon:toxic_orb"

    override fun onTurnTick(player: ServerPlayer, mon: Pokemon, config: BattleEngineConfig) {
        if (!CombatStateManager.hasVolatileStatus(player.uuid, "poison") && mon.status?.status?.name?.path != "poison" && mon.status?.status?.name?.path != "bad_poison") {
            CombatStateManager.applyVolatileStatus(player.uuid, "poison", -1)
            player.displayClientMessage(Component.translatable("cdbe.message.toxic_orb", mon.species.name), false)
        }
    }
}
