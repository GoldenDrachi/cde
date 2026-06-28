package net.drachi.cdbe.config

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*


import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry
import net.minecraft.world.level.GameRules

object GameRulesManager {
    lateinit var DISABLE_SWAPPING_ON_COOLDOWN: GameRules.Key<GameRules.BooleanValue>
    lateinit var DISABLE_SWAPPING_COMPLETELY: GameRules.Key<GameRules.BooleanValue>

    fun register() {
        DISABLE_SWAPPING_ON_COOLDOWN = GameRuleRegistry.register(
            "cdbe_disable_swapping_on_cooldown",
            GameRules.Category.PLAYER,
            GameRuleFactory.createBooleanRule(false)
        )

        DISABLE_SWAPPING_COMPLETELY = GameRuleRegistry.register(
            "cdbe_disable_swapping_completely",
            GameRules.Category.PLAYER,
            GameRuleFactory.createBooleanRule(false)
        )
    }
}
