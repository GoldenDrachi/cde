package net.drachi.cde.battleengine.client

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


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
