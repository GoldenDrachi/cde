package net.drachi.cde.battleengine.client

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


object ClientCombatManager {
    // Move ID -> Expiration Time (System.currentTimeMillis)
    val cooldowns = mutableMapOf<String, Long>()
    
    var activeWeather: String? = null
    var activeItem: String = "none"
    var lockedMoveId: String = "none"
    var lastSelectedSlot: Int = -1

    fun setCooldown(moveId: String, expirationMs: Long) {
        cooldowns[moveId] = expirationMs
    }

    fun getRemainingCooldown(moveId: String): Long {
        val expiration = cooldowns[moveId] ?: return 0L
        val remaining = expiration - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }
}
