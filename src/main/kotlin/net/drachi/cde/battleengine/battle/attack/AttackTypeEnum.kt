package net.drachi.cde.battleengine.battle.attack

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import com.google.gson.annotations.SerializedName

/**
 * Defines the physical execution method of a move.
 */
enum class AttackTypeEnum {
    @SerializedName("MELEE")
    MELEE,           // Short raycast / overlap box in front
    
    @SerializedName("PROJECTILE")
    PROJECTILE,      // Spawns a physical moving entity
    
    @SerializedName("BEAM")
    BEAM,            // Instant long raycast
    
    @SerializedName("AURA")
    AURA,            // Radius check around the caster (can hit enemies/friendlies based on move data later)
    
    @SerializedName("TARGETED")
    TARGETED,        // Homes in / hits a locked target (still bounded by range)
    
    @SerializedName("SELF")
    SELF,            // Affects only the caster (e.g. self-buffs)
    
    @SerializedName("SELF_AURA")
    SELF_AURA,       // Affects caster and friendly targets in radius
    
    @SerializedName("CONE")
    CONE,            // Conical area in front of caster
    
    @SerializedName("WAVE")
    WAVE,            // Wide line / wave moving forward
    
    @SerializedName("DOMAIN")
    DOMAIN,          // Persistent area of effect (Weather, Screens)
    
    @SerializedName("HAZARD")
    HAZARD,          // Stationary physical trap on the ground
    
    @SerializedName("DASH")
    DASH,            // Caster rapidly moves forward to attack
    
    @SerializedName("TELEPORT")
    TELEPORT,        // Instant relocation
    
    @SerializedName("COLUMN")
    COLUMN,          // Targeted, but visual indicator goes up (Thunderbolt, Eruption)
    
    @SerializedName("CRAWL")
    CRAWL,           // Like projectile but stays on ground
    
    @SerializedName("VANISH")
    VANISH           // Despawns caster or makes him invisible (Dig, Bounce, Fly)
}

/**
 * Defines the type of semi-invulnerable state.
 */
enum class VanishType {
    @SerializedName("AIR") AIR,
    @SerializedName("GROUND") GROUND,
    @SerializedName("WATER") WATER,
    @SerializedName("SHADOW") SHADOW
}
