package net.drachi.cde.battleengine.battle.utility

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import java.util.UUID

object CombatStateManager {
    data class StatModifier(var stage: Int, var expirationTimeMs: Long)
    
    // UUID -> Stat Name ("attack", "defense", "special_attack", "special_defense", "speed") -> Modifier
    private val statStages = mutableMapOf<UUID, MutableMap<String, StatModifier>>()
    
    // UUID -> Status Condition ("burn", "poison", "paralysis") -> Expiration Time MS
    private val volatileStatuses = mutableMapOf<UUID, MutableMap<String, Long>>()
    
    // UUID -> Substitute HP
    private val substituteHpMap = mutableMapOf<UUID, Int>()

    // Sequential Moves (e.g. Rollout)
    data class SequentialState(val moveId: String, var stacks: Int)
    private val sequentialMoves = mutableMapOf<UUID, SequentialState>()

    fun applyStatChange(uuid: UUID, stat: String, change: Int, durationMs: Long) {
        val entityMap = statStages.getOrPut(uuid) { mutableMapOf() }
        val current = entityMap[stat]
        val currentTime = System.currentTimeMillis()
        val expiration = if (durationMs == Long.MAX_VALUE || durationMs < 0) Long.MAX_VALUE else currentTime + durationMs
        
        if (current == null || current.expirationTimeMs < currentTime) {
            entityMap[stat] = StatModifier(change.coerceIn(-6, 6), expiration)
        } else {
            current.stage = (current.stage + change).coerceIn(-6, 6)
            current.expirationTimeMs = expiration
        }
    }

    fun getStatStage(uuid: UUID, stat: String): Int {
        val current = statStages[uuid]?.get(stat) ?: return 0
        if (current.expirationTimeMs < System.currentTimeMillis()) {
            return 0
        }
        return current.stage
    }
    
    fun getActiveStats(uuid: UUID): Map<String, Int> {
        val active = mutableMapOf<String, Int>()
        val currentTime = System.currentTimeMillis()
        statStages[uuid]?.forEach { (stat, modifier) ->
            if (modifier.expirationTimeMs > currentTime && modifier.stage != 0) {
                active[stat] = modifier.stage
            }
        }
        
        volatileStatuses[uuid]?.forEach { (status, expiration) ->
            if (expiration > currentTime) {
                if (status == "substitute") {
                    active["Substitute"] = substituteHpMap[uuid] ?: 1
                } else {
                    active[status.replaceFirstChar { it.uppercase() }] = 1
                }
            }
        }
        return active
    }
    
    fun applyVolatileStatus(uuid: UUID, status: String, durationMs: Long) {
        val entityMap = volatileStatuses.getOrPut(uuid) { mutableMapOf() }
        val expiration = if (durationMs == Long.MAX_VALUE || durationMs < 0) Long.MAX_VALUE else System.currentTimeMillis() + durationMs
        entityMap[status] = expiration
    }

    fun hasVolatileStatus(uuid: UUID, status: String): Boolean {
        val expiration = volatileStatuses[uuid]?.get(status) ?: return false
        return expiration > System.currentTimeMillis()
    }
    
    fun getAllStatuses(): Map<UUID, Map<String, Long>> {
        // Return a copy to avoid concurrent modification issues during iteration
        val currentTime = System.currentTimeMillis()
        return volatileStatuses.mapValues { (_, statuses) ->
            statuses.filterValues { it > currentTime }
        }.filterValues { it.isNotEmpty() }
    }
    
    fun clearVolatileStatuses(uuid: UUID) {
        volatileStatuses.remove(uuid)
    }

    fun clearStatStages(uuid: UUID) {
        statStages.remove(uuid)
    }

    fun removeVolatileStatus(uuid: UUID, status: String) {
        volatileStatuses[uuid]?.remove(status)
    }

    fun setSubstituteHp(uuid: UUID, hp: Int) {
        substituteHpMap[uuid] = hp
        if (hp > 0) {
            applyVolatileStatus(uuid, "substitute", Long.MAX_VALUE)
        } else {
            removeVolatileStatus(uuid, "substitute")
            substituteHpMap.remove(uuid)
        }
    }

    fun getSubstituteHp(uuid: UUID): Int? {
        return substituteHpMap[uuid]
    }

    fun damageSubstitute(uuid: UUID, amount: Int): Int {
        val current = substituteHpMap[uuid] ?: return amount
        val remaining = current - amount
        if (remaining <= 0) {
            substituteHpMap.remove(uuid)
            removeVolatileStatus(uuid, "substitute")
            return -remaining // Return excess damage
        } else {
            substituteHpMap[uuid] = remaining
            return -1 // Damage fully absorbed
        }
    }

    // --- Sequential Tracking ---
    fun recordSequentialUse(uuid: UUID, moveId: String, maxStacks: Int, resetOnMax: Boolean, startIfStatus: String?) {
        val current = sequentialMoves[uuid]
        if (current != null && current.moveId == moveId) {
            current.stacks++
            if (current.stacks > maxStacks) {
                if (resetOnMax) {
                    current.stacks = 1
                } else {
                    current.stacks = maxStacks
                }
            }
        } else {
            var startStacks = 1
            if (startIfStatus != null && hasVolatileStatus(uuid, startIfStatus)) {
                startStacks = 2 // E.g., Defense Curl buff for Rollout starts it at double power
            }
            sequentialMoves[uuid] = SequentialState(moveId, startStacks)
        }
    }

    fun resetSequentialUse(uuid: UUID) {
        sequentialMoves.remove(uuid)
    }

    fun getSequentialStacks(uuid: UUID, moveId: String): Int {
        val current = sequentialMoves[uuid]
        if (current != null && current.moveId == moveId) {
            return current.stacks
        }
        return 1
    }

    fun getSequentialMoveId(uuid: UUID): String? {
        return sequentialMoves[uuid]?.moveId
    }
}
