package net.drachi.cde.battleengine.battle.item

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*

import net.minecraft.server.level.ServerPlayer

import net.minecraft.network.chat.Component

class LifeOrbItem : BattleItem {
    override val itemId = "cobblemon:life_orb"

    override fun getDamageMultiplier(isSpecial: Boolean, isSuperEffective: Boolean): Float = 1.3f

    override fun onPostAttack(ctx: MoveContext) {
        if (ctx.totalDamageDealt > 0) {
            val recoil = (ctx.pokemonStats.maxHealth * 0.1f).toInt().coerceAtLeast(1)
            val newHp = ctx.pokemonStats.currentHealth - recoil
            ctx.pokemonStats.currentHealth = if (newHp < 0) 0 else newHp
            if (ctx.showLog) {
                (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.life_orb", ctx.pokemonStats.species.name), false)
            }
        }
    }
}

class ExpertBeltItem : BattleItem {
    override val itemId = "cobblemon:expert_belt"

    override fun getDamageMultiplier(isSpecial: Boolean, isSuperEffective: Boolean): Float {
        return if (isSuperEffective) 1.2f else 1.0f
    }
}

class MuscleBandItem : BattleItem {
    override val itemId = "cobblemon:muscle_band"

    override fun getDamageMultiplier(isSpecial: Boolean, isSuperEffective: Boolean): Float {
        return if (!isSpecial) 1.1f else 1.0f
    }
}

class WiseGlassesItem : BattleItem {
    override val itemId = "cobblemon:wise_glasses"

    override fun getDamageMultiplier(isSpecial: Boolean, isSuperEffective: Boolean): Float {
        return if (isSpecial) 1.1f else 1.0f
    }
}
