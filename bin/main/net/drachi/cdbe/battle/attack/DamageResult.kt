package net.drachi.cdbe.battle.attack

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*


/**
 * Contains the final damage value along with a full breakdown of
 * every step in the damage formula, used to build detailed combat log messages.
 */
data class DamageResult(
    val finalDamage: Int,
    val breakdown: DamageBreakdown
)

data class DamageBreakdown(
    val attackerLevel: Int,
    val moveName: String,
    val movePower: Double,
    val isSpecial: Boolean,
    val rawAttackStat: Int,
    val rawDefenseStat: Int,
    val attackStatAfterStages: Float,
    val defenseStatAfterStages: Float,
    val attackStage: Int,
    val defenseStage: Int,
    val baseDamage: Double,
    val multiTargetMod: Boolean,
    val isCrit: Boolean,
    val randomRoll: Int,           // 85-100
    val hasStab: Boolean,
    val typeEffectiveness: Float,
    val isBurned: Boolean,
    val weatherMultiplier: Float = 1.0f
)
