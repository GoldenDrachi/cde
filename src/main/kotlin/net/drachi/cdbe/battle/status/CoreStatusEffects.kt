package net.drachi.cdbe.battle.status

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*


import net.minecraft.world.entity.LivingEntity
import net.minecraft.server.level.ServerPlayer
import net.drachi.cdbe.config.ConfigManager
import net.drachi.cdbe.util.ParticleUtil
import net.minecraft.network.chat.Component

class BurnEffect : StatusEffect() {
    override fun apply(target: LivingEntity, effectData: StatusEffectData, caster: ServerPlayer?, showLog: Boolean): Boolean {
        CombatStateManager.applyVolatileStatus(target.uuid, "burn", StatusEffectHandler.calculateDurationMs(effectData.durationTurns))
        return true
    }

    override fun onTick(target: LivingEntity, activeStatuses: Map<String, Long>) {
        if (!activeStatuses.containsKey("burn")) return
        val maxHp = StatusEffectHandler.resolveMaxHp(target)
        val damage = StatusEffectHandler.calculateBurnDamage(maxHp, ConfigManager.config)
        if (damage > 0) DamageCalculator.applyTrueDamage(target, damage)
    }

    override fun spawnParticles(target: LivingEntity) {
        StatusEffectHandler.spawnBurnParticles(target)
    }
}

class PoisonEffect : StatusEffect() {
    override fun apply(target: LivingEntity, effectData: StatusEffectData, caster: ServerPlayer?, showLog: Boolean): Boolean {
        CombatStateManager.applyVolatileStatus(target.uuid, "poison", StatusEffectHandler.calculateDurationMs(effectData.durationTurns))
        return true
    }

    override fun onTick(target: LivingEntity, activeStatuses: Map<String, Long>) {
        if (!activeStatuses.containsKey("poison") && !activeStatuses.containsKey("bad_poison")) return
        val maxHp = StatusEffectHandler.resolveMaxHp(target)
        val damage = StatusEffectHandler.calculatePoisonDamage(maxHp, ConfigManager.config)
        if (damage > 0) DamageCalculator.applyTrueDamage(target, damage)
    }

    override fun spawnParticles(target: LivingEntity) {
        StatusEffectHandler.spawnPoisonParticles(target)
    }
}

class ParalysisEffect : StatusEffect() {
    override fun apply(target: LivingEntity, effectData: StatusEffectData, caster: ServerPlayer?, showLog: Boolean): Boolean {
        target.addEffect(net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, StatusEffectHandler.calculateDurationTicks(effectData.durationTurns), 2
        ))
        CombatStateManager.applyVolatileStatus(target.uuid, "paralysis", StatusEffectHandler.calculateDurationMs(effectData.durationTurns))
        return true
    }

    override fun spawnParticles(target: LivingEntity) {
        ParticleUtil.spawnParticle(target.level() as net.minecraft.server.level.ServerLevel, "lightning", target.x, target.y + target.bbHeight / 2.0, target.z, 1, target.bbWidth / 1.5, target.bbHeight / 1.5, target.bbWidth / 1.5, 0.0)
    }
}

class SleepEffect : StatusEffect() {
    override fun apply(target: LivingEntity, effectData: StatusEffectData, caster: ServerPlayer?, showLog: Boolean): Boolean {
        target.addEffect(net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, StatusEffectHandler.calculateDurationTicks(effectData.durationTurns), 10
        ))
        CombatStateManager.applyVolatileStatus(target.uuid, "sleep", StatusEffectHandler.calculateDurationMs(effectData.durationTurns))
        return true
    }

    override fun spawnParticles(target: LivingEntity) {
        ParticleUtil.spawnParticle(target.level() as net.minecraft.server.level.ServerLevel, "effect", target.x, target.y + target.bbHeight + 0.5, target.z, 1, 0.2, 0.2, 0.2, 0.0)
    }
}

class FreezeEffect : StatusEffect() {
    override fun apply(target: LivingEntity, effectData: StatusEffectData, caster: ServerPlayer?, showLog: Boolean): Boolean {
        target.addEffect(net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, StatusEffectHandler.calculateDurationTicks(effectData.durationTurns), 10
        ))
        CombatStateManager.applyVolatileStatus(target.uuid, "freeze", StatusEffectHandler.calculateDurationMs(effectData.durationTurns))
        return true
    }

    override fun spawnParticles(target: LivingEntity) {
        ParticleUtil.spawnParticle(target.level() as net.minecraft.server.level.ServerLevel, "snowflake", target.x, target.y + target.bbHeight / 2.0, target.z, 2, target.bbWidth / 1.2, target.bbHeight / 1.2, target.bbWidth / 1.2, 0.0)
    }
}

class FlinchEffect : StatusEffect() {
    override fun apply(target: LivingEntity, effectData: StatusEffectData, caster: ServerPlayer?, showLog: Boolean): Boolean {
        target.addEffect(net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, StatusEffectHandler.calculateDurationTicks(effectData.durationTurns), 10
        ))
        CombatStateManager.applyVolatileStatus(target.uuid, "flinch", StatusEffectHandler.calculateDurationMs(effectData.durationTurns))
        return true
    }

    override fun spawnParticles(target: LivingEntity) {
        ParticleUtil.spawnParticle(target.level() as net.minecraft.server.level.ServerLevel, "angry_villager", target.x, target.y + target.bbHeight + 0.5, target.z, 1, 0.2, 0.2, 0.2, 0.0)
    }
}

class StatChangeEffect : StatusEffect() {
    override fun apply(target: LivingEntity, effectData: StatusEffectData, caster: ServerPlayer?, showLog: Boolean): Boolean {
        if (effectData.type == "random_stat_up_2") {
            val stat = listOf("attack", "defense", "special_attack", "special_defense", "speed", "accuracy", "evasion").random()
            CombatStateManager.applyStatChange(target.uuid, stat, 2, StatusEffectHandler.calculateDurationMs(effectData.durationTurns))
            return true
        }

        val statChange = StatusEffectHandler.parseStatChange(effectData.type) ?: return false
        CombatStateManager.applyStatChange(target.uuid, statChange.first, statChange.second, StatusEffectHandler.calculateDurationMs(effectData.durationTurns))
        return true
    }
}
