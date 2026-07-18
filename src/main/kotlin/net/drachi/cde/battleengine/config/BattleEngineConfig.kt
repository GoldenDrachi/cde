package net.drachi.cde.battleengine.config

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import com.google.gson.annotations.SerializedName

/**
 * Data class representing the global configuration for the Real-Time Battle Engine.
 * This will be serialized to JSON.
 */
data class BattleEngineConfig(
    // Toggles whether the entire real-time system is active
    var isRealtimeEnabled: Boolean = true,
    
    // Toggles whether players' pokemon can damage other players' pokemon in real-time
    var isPvpEnabled: Boolean = false,
    
    // How many seconds 1 "turn" of a status effect or cooldown equals
    var turnToSecondsRatio: Float = 2.0f,
    
    // The default homing strength for projectiles (0.0 to 1.0)
    var baseHomingStrength: Float = 0.5f,
    
    // How many seconds line-of-sight can be broken before lock-on drops
    var losTimeoutSeconds: Float = 3.0f,
    
    @SerializedName("use_flat_status_damage") var useFlatStatusDamage: Boolean = false,
    
    // Toggles whether players can hit their own sent out pokemon
    var hitFriendlies: Boolean = true,
    
    // Morph Settings
    @SerializedName("enable_player_morph") var enablePlayerMorph: Boolean = true,
    
    var flatPoisonDamage: Int = 10,
    var flatBurnDamage: Int = 5,
    var percentPoisonDamage: Float = 12.5f, // 1/8th of Max HP
    var percentBurnDamage: Float = 6.25f, // 1/16th of Max HP
    
    // Default Hostility Configuration per Dimension ("HOSTILE", "NEUTRAL", or "PEACEFUL")
    @SerializedName("dimension_hostility") var dimensionHostility: Map<String, String> = mapOf(
        "minecraft:overworld" to "PEACEFUL",
        "minecraft:the_nether" to "HOSTILE",
        "minecraft:the_end" to "HOSTILE"
    ),
    
    // Held Item Configurations
    var useFlatLeftoversHeal: Boolean = false,
    var flatLeftoversHealAmount: Int = 5,
    var percentLeftoversHealAmount: Float = 6.25f, // 1/16th of Max HP
    
    // Scaling Multipliers
    var hpMultiplier: Float = 1.0f,
    var damageMultiplier: Float = 1.0f,
    var ppMultiplier: Float = 1.0f
)
