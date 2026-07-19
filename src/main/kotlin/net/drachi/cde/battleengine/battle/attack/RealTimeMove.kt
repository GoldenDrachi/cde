package net.drachi.cde.battleengine.battle.attack

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import com.google.gson.annotations.SerializedName
import net.minecraft.resources.ResourceLocation

/**
 * Data class representing the parsed JSON of a real-time move.
 */
data class RealTimeMove(
    @SerializedName("cobblemon_move_id") val cobblemonMoveId: String,
    @SerializedName("cooldown_turns") val cooldownTurns: Float = 0f,
    @SerializedName("is_sound_move") val isSoundMove: Boolean = false,
    @SerializedName("hp_cost_percent") val hpCostPercent: Float = 0f,
    @SerializedName("hp_cost_flat") val hpCostFlat: Int = 0,
    @SerializedName("phases") val phases: List<MovePhase> = emptyList()
) {
    val cobblemonMoveIdentifier: ResourceLocation
        get() = ResourceLocation.parse(cobblemonMoveId)
}

data class MovePhase(
    @SerializedName("attack_type") val attackType: AttackTypeEnum? = null,
    @SerializedName("chargeup_ticks") val chargeupTicks: Int = 0,
    @SerializedName("attack_duration_turns") val attackDurationTurns: Float = 0f,
    @SerializedName("attack_message") val attackMessage: String? = null,
    
    @SerializedName("base_power") val basePower: Int = 0,
    @SerializedName("accuracy") val accuracy: Int? = null,
    @SerializedName("range") val range: Float = 1.0f,
    @SerializedName("sound_effect") val soundEffect: String? = null,
    @SerializedName("duration_sound") val durationSound: String? = null,
    @SerializedName("homing_strength") val homingStrength: Float? = null,
    @SerializedName("projectile_speed") val projectileSpeed: Float? = null,
    @SerializedName("particles") val particles: ParticleData? = null,
    @SerializedName("extra_particles") val extraParticles: List<ParticleData>? = null,
    @SerializedName("on_hit_particles") val onHitParticles: ParticleData? = null,
    @SerializedName("status_effects") val statusEffects: List<StatusEffectData>? = null,
    @SerializedName("hits_friendlies") val hitsFriendlies: Boolean = false,
    @SerializedName("multiple_targets") val multipleTargets: Boolean = false,

    @SerializedName("domain_data") val domainData: DomainData? = null,
    @SerializedName("hazard_data") val hazardData: HazardData? = null,
    @SerializedName("mobility_data") val mobilityData: MobilityData? = null,
    @SerializedName("impact_aoe_data") val impactAoeData: ImpactAoeData? = null,
    @SerializedName("health_manipulation_data") val healthManipulationData: HealthManipulationData? = null,
    @SerializedName("random_effects") val randomEffects: List<RandomEffectData>? = null,
    @SerializedName("special_state_data") val specialStateData: SpecialStateData? = null,
    @SerializedName("multi_hit_data") val multiHitData: MultiHitData? = null,
    @SerializedName("power_scaling") val powerScaling: String? = null,
    @SerializedName("sequential_data") val sequentialData: SequentialData? = null,
    @SerializedName("bypasses") val bypasses: BypassData? = null,
    
    // Sky Drop & Vanish properties
    @SerializedName("vanish_type") val vanishType: VanishType? = null,
    @SerializedName("hits_vanish_types") val hitsVanishTypes: List<VanishType>? = null,
    @SerializedName("double_damage_on_vanish") val doubleDamageOnVanish: Boolean = false,
    @SerializedName("skip_with_power_herb") val skipWithPowerHerb: Boolean = false,
    @SerializedName("abort_move_on_miss") val abortMoveOnMiss: Boolean = false,
    @SerializedName("bind_target_to_caster") val bindTargetToCaster: Boolean = false,
    @SerializedName("unmount_target") val unmountTarget: Boolean = false,
    @SerializedName("self_status_effects") val selfStatusEffects: List<StatusEffectData>? = null
)

data class ParticleData(
    @SerializedName("type") val type: String,
    @SerializedName("count") val count: Int = 1,
    @SerializedName("color") val color: String? = null,
    @SerializedName("bind_to_target") val bindToTarget: Boolean = false,
    @SerializedName("bind_to_caster") val bindToCaster: Boolean = false
)

data class StatusEffectData(
    @SerializedName("type") val type: String, // e.g., "defense_down", "paralysis", "burn"
    @SerializedName("duration_turns") val durationTurns: Float,
    @SerializedName("chance") val chance: Float = 100.0f, // 100.0 = 100% chance to apply
    @SerializedName("immunity_cooldown_turns") val immunityCooldownTurns: Float? = null
)

// --- Domain Data ---
data class DomainData(
    @SerializedName("type") val type: String, // "weather", "screen_physical", "screen_special"
    @SerializedName("radius") val radius: Float = 15.0f,
    @SerializedName("duration_turns") val durationTurns: Float = 5.0f,
    @SerializedName("weather_condition") val weatherCondition: String? = null,
    @SerializedName("ambient_particles") val ambientParticles: ParticleData? = null,
    @SerializedName("tick_damage") val tickDamage: TickDamageData? = null,
    @SerializedName("stat_buffs") val statBuffs: List<StatBuffData>? = null,
    @SerializedName("elemental_modifiers") val elementalModifiers: Map<String, Float>? = null
)

data class TickDamageData(
    @SerializedName("damage_percent") val damagePercent: Float,
    @SerializedName("immune_types") val immuneTypes: List<String> = emptyList()
)

data class StatBuffData(
    @SerializedName("target_type") val targetType: String,
    @SerializedName("stat") val stat: String,
    @SerializedName("multiplier") val multiplier: Float
)

// --- Hazard Data ---
data class HazardData(
    @SerializedName("hazard_type") val hazardType: String,
    @SerializedName("placement_range") val placementRange: Float = 15.0f,
    @SerializedName("ring_radius") val ringRadius: Float = 3.0f,
    @SerializedName("duration_turns") val durationTurns: Float = -1.0f,
    @SerializedName("max_triggers") val maxTriggers: Int = -1,
    @SerializedName("max_stacks") val maxStacks: Int = 1,
    @SerializedName("fail_on_max_stacks") val failOnMaxStacks: Boolean = false,
    @SerializedName("damage_percent") val damagePercent: Float = 12.5f,
    @SerializedName("damage_type") val damageType: String? = null,
    @SerializedName("status_effect") val statusEffect: StatusEffectData? = null,
    @SerializedName("stack_status_effects") val stackStatusEffects: Map<String, StatusEffectData>? = null,
    @SerializedName("vanishes_when_touched_by_type") val vanishesWhenTouchedByType: String? = null,
    @SerializedName("affects_flying") val affectsFlying: Boolean = false
)

// --- Mobility Data ---
data class MobilityData(
    @SerializedName("dash_speed") val dashSpeed: Float? = null,
    @SerializedName("dash_range") val dashRange: Float? = null,
    @SerializedName("stop_on_hit") val stopOnHit: Boolean = true,
    @SerializedName("teleport_range") val teleportRange: Float? = null,
    @SerializedName("knockback_force") val knockbackForce: Float? = null,
    @SerializedName("retreat_force") val retreatForce: Float? = null
)

// --- Impact AoE Data ---
data class ImpactAoeData(
    @SerializedName("radius") val radius: Float,
    @SerializedName("damage_falloff") val damageFalloff: Boolean = false,
    @SerializedName("particles") val particles: ParticleData? = null
)

// --- Health Manipulation Data ---
data class HealthManipulationData(
    @SerializedName("drain_percent") val drainPercent: Float = 0f,
    @SerializedName("recoil_percent") val recoilPercent: Float = 0f,
    @SerializedName("heal_self_percent") val healSelfPercent: Float = 0f,
    @SerializedName("heal_target_percent") val healTargetPercent: Float = 0f,
    @SerializedName("cleanses_status") val cleansesStatus: List<String>? = null,
    @SerializedName("sacrificial") val sacrificial: Boolean = false
)

// --- Random Effect Data (Present) ---
data class RandomEffectData(
    @SerializedName("weight") val weight: Int,
    @SerializedName("base_power") val basePower: Int? = null,
    @SerializedName("health_manipulation_data") val healthManipulationData: HealthManipulationData? = null
)

// --- Special State Data ---
data class SpecialStateData(
    @SerializedName("protect") val protect: ProtectData? = null,
    @SerializedName("stat_reset") val statReset: Boolean = false
)

data class ProtectData(
    @SerializedName("duration_turns") val durationTurns: Float,
    @SerializedName("chance_decay") val chanceDecay: Float = 0.5f
)

// --- Bypass Data ---
data class BypassData(
    @SerializedName("ignores_protect") val ignoresProtect: Boolean = false,
    @SerializedName("breaks_protect") val breaksProtect: Boolean = false,
    @SerializedName("ignores_substitute") val ignoresSubstitute: Boolean = false,
    @SerializedName("ignores_stat_changes") val ignoresStatChanges: Boolean = false,
    @SerializedName("flat_damage") val flatDamage: Int? = null,
    @SerializedName("is_ohko") val isOhko: Boolean = false
)

// --- Edge Case Data ---
data class MultiHitData(
    @SerializedName("min_hits") val minHits: Int,
    @SerializedName("max_hits") val maxHits: Int,
    @SerializedName("delay_ticks") val delayTicks: Int = 5
)

data class SequentialData(
    @SerializedName("multiplier") val multiplier: Float = 2.0f,
    @SerializedName("max_stacks") val maxStacks: Int = 5,
    @SerializedName("reset_on_max") val resetOnMax: Boolean = true,
    @SerializedName("start_with_stack_if_status") val startWithStackIfStatus: String? = null
)



