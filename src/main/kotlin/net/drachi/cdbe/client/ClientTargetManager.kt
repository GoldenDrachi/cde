package net.drachi.cdbe.client

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*


object ClientTargetManager {
    var lockedTargetId: Int = -1
    
    // Store latest known stat stages for entities (entityId -> Map<statName, stage>)
    val entityStats = mutableMapOf<Int, Map<String, Int>>()
    
    fun updateEntityStats(entityId: Int, stats: Map<String, Int>) {
        if (stats.isEmpty()) {
            entityStats.remove(entityId)
        } else {
            entityStats[entityId] = stats
        }
    }
}
