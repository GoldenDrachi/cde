package net.drachi.cdbe.battle.utility

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*


import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.api.moves.MoveTemplate
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import java.util.UUID

object DelayedActionManager {

    data class QueuedPhase(
        val executionId: UUID,
        val casterUuid: UUID,
        val pokemonStats: Pokemon,
        val move: RealTimeMove,
        val phase: MovePhase,
        val moveTemplate: MoveTemplate,
        var fireTimeMs: Long
    )

    data class RepeatingPhase(
        val casterUuid: UUID,
        val pokemonStats: Pokemon,
        val move: RealTimeMove,
        val phase: MovePhase,
        val moveTemplate: MoveTemplate,
        var nextFireTimeMs: Long,
        var remainingHits: Int,
        val delayTicks: Int
    )

    private val queuedPhases = mutableListOf<QueuedPhase>()
    private val repeatingPhases = mutableListOf<RepeatingPhase>()

    fun queuePhase(executionId: UUID, casterEntity: net.minecraft.world.entity.LivingEntity, pokemonStats: Pokemon, move: RealTimeMove, moveTemplate: MoveTemplate, phase: MovePhase, delayTicks: Long) {
        val fireTime = System.currentTimeMillis() + (delayTicks * 50L) // 50ms per tick
        
        queuedPhases.add(QueuedPhase(executionId, casterEntity.uuid, pokemonStats, move, phase, moveTemplate, fireTime))
        
        // Legacy charge phase message support
        phase.attackMessage?.let { msg ->
            if (delayTicks > 0) (casterEntity as? net.minecraft.server.level.ServerPlayer)?.sendSystemMessage(Component.literal("§e$msg"))
        }

        if (delayTicks > 0) {
            CombatStateManager.applyVolatileStatus(casterEntity.uuid, "charging", delayTicks * 50L)
        }
        
        // Spawn Entity Particle ONCE if it's a cobblemon particle
        phase.particles?.let { pData ->
            if (pData.type.startsWith("cobblemon:") && delayTicks > 0) {
                net.drachi.cdbe.util.ParticleUtil.spawnEntityParticle(casterEntity.level() as net.minecraft.server.level.ServerLevel, pData.type, casterEntity)
            }
        }
    }
    
    fun cancelExecution(executionId: UUID) {
        queuedPhases.removeIf { it.executionId == executionId }
    }

    fun queueRepeatingPhase(casterEntity: net.minecraft.world.entity.LivingEntity, pokemonStats: Pokemon, move: RealTimeMove, moveTemplate: MoveTemplate, phase: MovePhase, remainingHits: Int, delayTicks: Int) {
        val nextFireTimeMs = System.currentTimeMillis() + (delayTicks * 50L)
        repeatingPhases.add(RepeatingPhase(casterEntity.uuid, pokemonStats, move, phase, moveTemplate, nextFireTimeMs, remainingHits, delayTicks))
    }

    fun isCharging(playerUuid: UUID): Boolean {
        return CombatStateManager.hasVolatileStatus(playerUuid, "charging")
    }

    fun isRecharging(playerUuid: UUID): Boolean {
        return CombatStateManager.hasVolatileStatus(playerUuid, "recharging")
    }
    
    fun applyRecharge(playerUuid: UUID, durationTurns: Float, casterEntity: net.minecraft.world.entity.LivingEntity, attackMessage: String?) {
        if (durationTurns <= 0) return
        val durationMs = (durationTurns * net.drachi.cdbe.config.ConfigManager.config.turnToSecondsRatio * 1000).toLong()
        CombatStateManager.applyVolatileStatus(playerUuid, "recharging", durationMs)
        attackMessage?.let { msg ->
            (casterEntity as? net.minecraft.server.level.ServerPlayer)?.sendSystemMessage(Component.literal("§e$msg"))
        }
    }

    fun tick(server: MinecraftServer) {
        val currentTime = System.currentTimeMillis()
        val iterator = queuedPhases.iterator()
        
        while (iterator.hasNext()) {
            val queued = iterator.next()
            val player = net.drachi.cdbe.battle.utility.EntityUtil.findLivingEntityByUUID(server, queued.casterUuid)
            
            if (currentTime >= queued.fireTimeMs) {
                iterator.remove()
                if (player != null && player.isAlive) {
                    // Execute immediately without requeuing
                    AttackExecutor.executePhaseNow(player, queued.pokemonStats, queued.move, queued.phase, queued.moveTemplate, isRepeating = false, executionId = queued.executionId)
                }
            } else {
                // Spawn charging particles (vanilla only)
                if (server.tickCount % 5 == 0 && player != null) {
                    queued.phase.particles?.let { pData ->
                        if (!pData.type.startsWith("cobblemon:")) {
                            val level = player.level() as net.minecraft.server.level.ServerLevel
                            for (i in 0 until pData.count) {
                                val rx = player.x + (Math.random() - 0.5) * 2.0
                                val ry = player.y + Math.random() * 2.0
                                val rz = player.z + (Math.random() - 0.5) * 2.0
                                net.drachi.cdbe.util.ParticleUtil.spawnParticle(level, pData.type, rx, ry, rz, 1, 0.0, 0.0, 0.0, 0.0)
                            }
                        }
                    }
                }
            }
        }
        
        // Handle repeating phases
        val repeatingIterator = repeatingPhases.iterator()
        while (repeatingIterator.hasNext()) {
            val rep = repeatingIterator.next()
            if (currentTime >= rep.nextFireTimeMs) {
                val player = net.drachi.cdbe.battle.utility.EntityUtil.findLivingEntityByUUID(server, rep.casterUuid)
                if (player != null && player.isAlive) {
                    // Fire next hit
                    AttackExecutor.executePhaseNow(player, rep.pokemonStats, rep.move, rep.phase, rep.moveTemplate, isRepeating = true)
                    rep.remainingHits -= 1
                    if (rep.remainingHits > 0) {
                        rep.nextFireTimeMs = System.currentTimeMillis() + (rep.delayTicks * 50L)
                    } else {
                        repeatingIterator.remove()
                    }
                } else {
                    repeatingIterator.remove()
                }
            }
        }
    }
}
