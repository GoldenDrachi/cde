package net.drachi.cde.battleengine.battle.item

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*

import net.minecraft.server.level.ServerPlayer

import net.minecraft.network.chat.Component

class BlunderPolicyItem : BattleItem {
    override val itemId = "cobblemon:blunder_policy"

    override fun onPostAttack(ctx: MoveContext) {
        if (ctx.blunderPolicyTriggered) {
            PokemonItemManager.consumeItem(ctx.pokemonStats, itemId)
            CombatStateManager.applyStatChange(ctx.caster.uuid, "speed", 2, -1)
            if (ctx.showLog) {
                (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.blunder_policy", ctx.pokemonStats.species.name), false)
            }
        }
    }
}

class ThroatSprayItem : BattleItem {
    override val itemId = "cobblemon:throat_spray"

    override fun onPostAttack(ctx: MoveContext) {
        if (ctx.throatSprayTriggered && ctx.totalDamageDealt > 0) {
            PokemonItemManager.consumeItem(ctx.pokemonStats, itemId)
            CombatStateManager.applyStatChange(ctx.caster.uuid, "special_attack", 1, -1)
            if (ctx.showLog) {
                (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.throat_spray", ctx.pokemonStats.species.name), false)
            }
        }
    }
}

class ShellBellItem : BattleItem {
    override val itemId = "cobblemon:shell_bell"

    override fun onPostAttack(ctx: MoveContext) {
        if (ctx.totalDamageDealt > 0) {
            val heal = (ctx.totalDamageDealt / 8).coerceAtLeast(1)
            val newHp = (ctx.pokemonStats.currentHealth + heal).coerceIn(0, ctx.pokemonStats.maxHealth)
            ctx.pokemonStats.currentHealth = newHp
            if (ctx.showLog) {
                (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.shell_bell", ctx.pokemonStats.species.name), false)
            }
        }
    }
}
