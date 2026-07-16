package net.drachi.cde.battleengine.battle.ability

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import com.google.gson.annotations.SerializedName

data class AbilityEventData(
    @SerializedName("target_type") val targetType: String = "enemies", // "self", "enemies", "all"
    @SerializedName("phase") val phase: MovePhase
)

data class AbilityData(
    @SerializedName("cobblemon_ability_id") val cobblemonAbilityId: String,
    @SerializedName("on_switch_in") val onSwitchIn: AbilityEventData? = null,
    @SerializedName("on_damage_dealt") val onDamageDealt: AbilityEventData? = null,
    @SerializedName("on_hit_received") val onHitReceived: AbilityEventData? = null,
    @SerializedName("on_status_applied") val onStatusApplied: AbilityEventData? = null
)
