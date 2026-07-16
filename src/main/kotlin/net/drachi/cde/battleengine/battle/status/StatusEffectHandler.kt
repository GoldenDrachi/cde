package net.drachi.cde.battleengine.battle.status

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.network.chat.Component
import net.drachi.cde.battleengine.config.BattleEngineConfigManager
import net.drachi.cde.battleengine.util.ParticleUtil
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import java.util.UUID

/**
 * Centralised handler for all status effect logic:
 * - Parsing effect type strings ("speed_up_2" -> stat "speed", change +2)
 * - Applying effects to self or targets
 * - Periodic status damage (poison / burn ticks)
 * - Visual particles for active statuses (substitute / protect)
 */
object StatusEffectHandler {

    // ─── Duration Helpers ────────────────────────────────────────────

    /**
     * Converts a turn-based duration into milliseconds using the config ratio.
     * Negative values (e.g. -1) are treated as "infinite / until switch or heal".
     */
    fun calculateDurationMs(durationTurns: Float): Long {
        if (durationTurns < 0) return Long.MAX_VALUE
        return (durationTurns * BattleEngineConfigManager.config.turnToSecondsRatio * 1000).toLong()
    }

    /**
     * Converts a turn-based duration into vanilla ticks (20 tps).
     */
    fun calculateDurationTicks(durationTurns: Float): Int {
        return (durationTurns * BattleEngineConfigManager.config.turnToSecondsRatio * 20).toInt()
    }

    // ─── Parsing ─────────────────────────────────────────────────────

    /**
     * Attempts to parse a status-effect type string into a stat name and stage change.
     *
     * Examples:
     *  - "speed_up_2"        -> Pair("speed", +2)
     *  - "special_attack_up" -> Pair("special_attack", +1)
     *  - "defense_down_2"    -> Pair("defense", -2)
     *  - "attack_down"       -> Pair("attack", -1)
     *
     * Returns null if the string doesn't match any stat-change pattern.
     *
     * IMPORTANT: `_up_2` / `_down_2` are checked before `_up` / `_down`
     * to avoid the substring matching bug where "speed_up_2".endsWith("_up") == true.
     */
    fun parseStatChange(effectType: String): Pair<String, Int>? {
        return when {
            effectType.endsWith("_up_2") -> Pair(effectType.removeSuffix("_up_2"), 2)
            effectType.endsWith("_down_2") -> Pair(effectType.removeSuffix("_down_2"), -2)
            effectType.endsWith("_up") -> Pair(effectType.removeSuffix("_up"), 1)
            effectType.endsWith("_down") -> Pair(effectType.removeSuffix("_down"), -1)
            else -> null
        }
    }

    // ─── Self-Targeting Application ──────────────────────────────────

    fun applyToSelf(player: net.minecraft.world.entity.LivingEntity, effects: List<StatusEffectData>?): List<String> {
        if (effects == null) return emptyList()

        val gained = mutableListOf<String>()
        for (effect in effects) {
            if (kotlin.random.Random.nextInt(100) >= effect.chance) continue

            val handler = net.drachi.cde.battleengine.battle.status.StatusEffectRegistry.getEffect(effect.type)
            if (handler != null) {
                if (handler.apply(player, effect, player as? net.minecraft.server.level.ServerPlayer, false)) {
                    val formattedName = effect.type.replace("_", " ")
                    gained.add(formattedName)
                }
            } else {
                val statChange = parseStatChange(effect.type)
                if (statChange != null) {
                    val duration = calculateDurationMs(effect.durationTurns)
                    CombatStateManager.applyStatChange(player.uuid, statChange.first, statChange.second, duration)
                    gained.add("${statChange.first.replace("_", " ")} ${if (statChange.second > 0) "up" else "down"}")
                    continue
                }

                if (effect.type == "heal") {
                    applyHealEffect(player)
                    gained.add("healing")
                } else if (effect.type.startsWith("type_override_")) {
                    CombatStateManager.applyVolatileStatus(player.uuid, effect.type, calculateDurationMs(effect.durationTurns))
                    gained.add(effect.type)
                } else {
                    CombatStateManager.applyVolatileStatus(player.uuid, effect.type, calculateDurationMs(effect.durationTurns))
                    gained.add(effect.type)
                }
            }
        }
        return gained
    }

    // ─── Offensive / Target Application ──────────────────────────────

    fun applyToTarget(target: LivingEntity, effect: StatusEffectData, caster: ServerPlayer? = null, showLog: Boolean = false) {
        if (CombatStateManager.hasVolatileStatus(target.uuid, "immune_${effect.type}")) return

        val handler = net.drachi.cde.battleengine.battle.status.StatusEffectRegistry.getEffect(effect.type)
        val appliedName = effect.type.replace("_", " ")

        if (handler != null) {
            if (handler.apply(target, effect, caster, showLog)) {
                if (showLog && caster != null) {
                    caster.displayClientMessage(Component.translatable("cdbe.message.status_afflicted", target.name.string, appliedName), false)
                }
                applyImmunityCooldown(target.uuid, effect)
            }
        } else {
            val statChange = parseStatChange(effect.type)
            if (statChange != null) {
                val duration = calculateDurationMs(effect.durationTurns)
                CombatStateManager.applyStatChange(target.uuid, statChange.first, statChange.second, duration)
                if (showLog && caster != null) {
                    val changeDesc = if (statChange.second > 0) "rose" else "fell"
                    val msg = Component.translatable("cdbe.message.stat_change", target.name.string, statChange.first.replace("_", " "), changeDesc)
                        .withStyle(if (statChange.second > 0) net.minecraft.ChatFormatting.GREEN else net.minecraft.ChatFormatting.RED)
                    caster.displayClientMessage(msg, false)
                }
            } else {
                CombatStateManager.applyVolatileStatus(target.uuid, effect.type, calculateDurationMs(effect.durationTurns))
                
                if (target is PokemonEntity) {
                    val cobblemonStatus = when(effect.type) {
                        "poison" -> Statuses.POISON
                        "badly_poison" -> Statuses.POISON_BADLY
                        "burn" -> Statuses.BURN
                        "paralysis" -> Statuses.PARALYSIS
                        "sleep" -> Statuses.SLEEP
                        "freeze" -> Statuses.FROZEN
                        else -> null
                    }
                    if (cobblemonStatus is com.cobblemon.mod.common.pokemon.status.PersistentStatus) {
                        val durationSeconds = (calculateDurationMs(effect.durationTurns) / 1000).toInt()
                        target.pokemon.status = PersistentStatusContainer(cobblemonStatus, if (durationSeconds < 0) 9999 else durationSeconds)
                    }
                }
                
                if (showLog && caster != null) {
                    caster.displayClientMessage(Component.translatable("cdbe.message.status_afflicted", target.name.string, appliedName), false)
                }
                applyImmunityCooldown(target.uuid, effect)
            }
        }
        
        // Trigger Ability hook
        val pokemon = if (target is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) target.pokemon else if (target is net.minecraft.server.level.ServerPlayer) PlayerCombatManager.getActivePokemon(target) else null
        if (pokemon != null) {
            net.drachi.cde.battleengine.battle.ability.AbilityExecutor.executeOnStatusApplied(pokemon, target, effect.type)
        }
    }

    // ─── Periodic Processing ─────────────────────────────────────────

    /**
     * Processes periodic poison and burn damage for all entities with active statuses.
     * Called once per "turn" from CombatTickHandler.
     */
    fun processPeriodicDamage(server: MinecraftServer) {
        val statuses = CombatStateManager.getAllStatuses()

        for ((uuid, activeStatuses) in statuses) {
            val entity = EntityUtil.findLivingEntityByUUID(server, uuid) ?: continue
            for ((statusId, _) in activeStatuses) {
                val handler = net.drachi.cde.battleengine.battle.status.StatusEffectRegistry.getEffect(statusId)
                handler?.onTick(entity, activeStatuses)
            }
        }
    }

    /**
     * Spawns visual particles for substitute and protect statuses.
     * Called every 5 ticks from CombatTickHandler.
     */
    fun spawnStatusParticles(server: MinecraftServer) {
        val statuses = CombatStateManager.getAllStatuses()

        for ((uuid, activeStatuses) in statuses) {
            val entity = EntityUtil.findLivingEntityByUUID(server, uuid) ?: continue
            for ((statusId, _) in activeStatuses) {
                val handler = net.drachi.cde.battleengine.battle.status.StatusEffectRegistry.getEffect(statusId)
                handler?.spawnParticles(entity)
            }
        }
    }

    // ─── Private Helpers ─────────────────────────────────────────────

    private fun randomStat(): String {
        val stats = listOf("attack", "defense", "special_attack", "special_defense", "speed", "accuracy", "evasion")
        return stats.random()
    }

    private fun applyHealEffect(player: net.minecraft.world.entity.LivingEntity) {
        val serverPlayer = player as? net.minecraft.server.level.ServerPlayer
        val activePokemon = if (serverPlayer != null) PlayerCombatManager.getActivePokemon(serverPlayer) else null
        if (activePokemon != null && serverPlayer != null) {
            val healAmount = activePokemon.maxHealth / 2
            activePokemon.currentHealth = (activePokemon.currentHealth + healAmount).coerceIn(0, activePokemon.maxHealth)
            val healthRatio = activePokemon.currentHealth.toFloat() / activePokemon.maxHealth.toFloat()
            player.health = player.maxHealth * healthRatio
        } else {
            player.heal(player.maxHealth / 2.0f)
        }
    }

    private fun applyImmunityCooldown(targetUuid: UUID, effect: StatusEffectData) {
        if (effect.immunityCooldownTurns != null) {
            val immunityDurationMs = calculateDurationMs(effect.immunityCooldownTurns)
            CombatStateManager.applyVolatileStatus(targetUuid, "immune_${effect.type}", immunityDurationMs)
        }
    }

    internal fun resolveMaxHp(entity: LivingEntity): Int {
        return when (entity) {
            is com.cobblemon.mod.common.entity.pokemon.PokemonEntity -> entity.pokemon.maxHealth
            is ServerPlayer -> PlayerCombatManager.getActivePokemon(entity)?.maxHealth ?: entity.maxHealth.toInt()
            else -> entity.maxHealth.toInt()
        }
    }

    internal fun calculatePoisonDamage(maxHp: Int, config: net.drachi.cde.battleengine.config.BattleEngineConfig): Int {
        return if (config.useFlatStatusDamage) config.flatPoisonDamage
        else (maxHp * (config.percentPoisonDamage / 100.0f)).toInt()
    }

    internal fun calculateBurnDamage(maxHp: Int, config: net.drachi.cde.battleengine.config.BattleEngineConfig): Int {
        return if (config.useFlatStatusDamage) config.flatBurnDamage
        else (maxHp * (config.percentBurnDamage / 100.0f)).toInt()
    }

    internal fun spawnPoisonParticles(entity: LivingEntity) {
        ParticleUtil.spawnParticle(
            entity.level() as net.minecraft.server.level.ServerLevel, "composter",
            entity.x, entity.y + entity.bbHeight / 2.0, entity.z,
            3, entity.bbWidth / 1.5, entity.bbHeight / 1.5, entity.bbWidth / 1.5, 0.0
        )
    }

    internal fun spawnBurnParticles(entity: LivingEntity) {
        ParticleUtil.spawnParticle(
            entity.level() as net.minecraft.server.level.ServerLevel, "flame",
            entity.x, entity.y + entity.bbHeight / 2.0, entity.z,
            3, entity.bbWidth / 1.5, entity.bbHeight / 1.5, entity.bbWidth / 1.5, 0.01
        )
    }
}
