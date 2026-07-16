package net.drachi.cde.battleengine.battle.status

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import net.minecraft.world.entity.LivingEntity
import net.minecraft.server.level.ServerPlayer
import net.drachi.cde.battleengine.config.BattleEngineConfigManager
import net.drachi.cde.battleengine.util.ParticleUtil
import net.minecraft.network.chat.Component

abstract class StatusEffect {
    // Return true if successfully applied
    abstract fun apply(target: LivingEntity, effectData: StatusEffectData, caster: ServerPlayer?, showLog: Boolean): Boolean
    
    open fun onTick(target: LivingEntity, activeStatuses: Map<String, Long>) {}
    
    // Returns the amount of damage that actually goes through to the Pokémon
    open fun onHitReceived(target: LivingEntity, damage: Int, breakdown: DamageBreakdown): Int {
        return damage
    }
    
    // Called every 5 ticks for visuals
    open fun spawnParticles(target: LivingEntity) {}
}

object StatusEffectRegistry {
    private val effects = mutableMapOf<String, StatusEffect>()

    init {
        register("burn", BurnEffect())
        register("poison", PoisonEffect())
        register("paralysis", ParalysisEffect())
        register("sleep", SleepEffect())
        register("freeze", FreezeEffect())
        register("flinch", FlinchEffect())
        register("substitute", SubstituteEffect())
        register("protect", ProtectEffect())
        register("endure", EndureEffect())
        register("stat_change", StatChangeEffect())
    }

    fun register(id: String, effect: StatusEffect) {
        effects[id] = effect
    }

    fun getEffect(id: String): StatusEffect? {
        if (StatusEffectHandler.parseStatChange(id) != null) return effects["stat_change"]
        return effects[id]
    }
}
