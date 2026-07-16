package net.drachi.cde.battleengine.battle.status

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import net.minecraft.world.entity.LivingEntity
import net.minecraft.server.level.ServerPlayer
import net.drachi.cde.battleengine.util.ParticleUtil

class SubstituteEffect : StatusEffect() {
    override fun apply(target: LivingEntity, effectData: StatusEffectData, caster: ServerPlayer?, showLog: Boolean): Boolean {
        CombatStateManager.applyVolatileStatus(target.uuid, "substitute", StatusEffectHandler.calculateDurationMs(effectData.durationTurns))
        return true
    }

    override fun spawnParticles(target: LivingEntity) {
        ParticleUtil.spawnParticle(target.level() as net.minecraft.server.level.ServerLevel, "happy_villager", target.x, target.y + target.bbHeight / 2.0, target.z, 3, target.bbWidth / 1.5, target.bbHeight / 1.5, target.bbWidth / 1.5, 0.0)
    }
}

class ProtectEffect : StatusEffect() {
    override fun apply(target: LivingEntity, effectData: StatusEffectData, caster: ServerPlayer?, showLog: Boolean): Boolean {
        CombatStateManager.applyVolatileStatus(target.uuid, "protect", StatusEffectHandler.calculateDurationMs(effectData.durationTurns))
        return true
    }

    override fun spawnParticles(target: LivingEntity) {
        ParticleUtil.spawnParticle(target.level() as net.minecraft.server.level.ServerLevel, "end_rod", target.x, target.y + target.bbHeight / 2.0, target.z, 4, target.bbWidth / 1.2, target.bbHeight / 1.2, target.bbWidth / 1.2, 0.0)
    }
}

class EndureEffect : StatusEffect() {
    override fun apply(target: LivingEntity, effectData: StatusEffectData, caster: ServerPlayer?, showLog: Boolean): Boolean {
        CombatStateManager.applyVolatileStatus(target.uuid, "endure", StatusEffectHandler.calculateDurationMs(effectData.durationTurns))
        return true
    }

    override fun spawnParticles(target: LivingEntity) {
        ParticleUtil.spawnParticle(target.level() as net.minecraft.server.level.ServerLevel, "damage_indicator", target.x, target.y + target.bbHeight / 2.0, target.z, 2, target.bbWidth / 1.2, target.bbHeight / 1.2, target.bbWidth / 1.2, 0.0)
    }
}
