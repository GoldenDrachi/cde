package net.drachi.cde.battleengine.battle.attack

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import net.drachi.cde.battleengine.config.GameRulesManager
import net.drachi.cde.battleengine.config.BattleEngineConfigManager
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import java.util.UUID

object PlayerCombatManager {
    
    data class CombatState(
        var activePartySlot: Int = -1,
        val cooldowns: MutableMap<String, Long> = mutableMapOf(), // Move ID -> expiration time in MS
        var lockedTargetId: Int = -1,
        var showCombatLog: Boolean = true,
        var showDetailedDamage: Boolean = true
    )

    private val playerStates = mutableMapOf<UUID, CombatState>()

    fun canCast(player: ServerPlayer, requestedSlot: Int, moveId: String): Boolean {
        val state = playerStates.getOrPut(player.uuid) { CombatState() }
        val currentTime = System.currentTimeMillis()

        // Check Global Cooldown
        val globalGcd = state.cooldowns["GLOBAL_GCD"]
        if (globalGcd != null && globalGcd > currentTime) {
            return false // Silently drop to prevent spam messages
        }

        // Handle Swapping Rules
        if (requestedSlot != -1 && state.activePartySlot != -1 && state.activePartySlot != requestedSlot) {
            val disableCompletely = player.serverLevel().gameRules.getBoolean(GameRulesManager.DISABLE_SWAPPING_COMPLETELY)
            val disableOnCooldown = player.serverLevel().gameRules.getBoolean(GameRulesManager.DISABLE_SWAPPING_ON_COOLDOWN)

            if (disableCompletely) {
                player.displayClientMessage(Component.translatable("cdbe.message.swap_disabled").withStyle(net.minecraft.ChatFormatting.RED), true)
                return false
            }

            if (BattleEngineConfigManager.config.enablePlayerMorph) {
                val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
                val targetMon = party.get(requestedSlot)
                if (targetMon != null) {
                    val w = targetMon.form.hitbox.width
                    val h = targetMon.form.hitbox.height
                    val currentW = player.bbWidth
                    val currentH = player.bbHeight
                    // We only need to check if the new hitbox is LARGER
                    if (w > currentW || h > currentH) {
                        val dx = (w - currentW) / 2.0
                        val dy = (h - currentH).toDouble()
                        val newBox = player.boundingBox.inflate(dx.toDouble(), 0.0, dx.toDouble()).expandTowards(0.0, dy, 0.0)
                        if (!player.level().noCollision(player, newBox)) {
                            player.displayClientMessage(Component.translatable("cdbe.message.not_enough_space", targetMon.species.name).withStyle(net.minecraft.ChatFormatting.RED), true)
                            return false
                        }
                    }
                }
            }

            if (disableOnCooldown) {
                val hasActiveCooldown = state.cooldowns.values.any { it > currentTime }
                if (hasActiveCooldown) {
                    player.displayClientMessage(Component.translatable("cdbe.message.swap_cooldown").withStyle(net.minecraft.ChatFormatting.RED), true)
                    return false
                }
            }

            // Only clear switch-out states AFTER confirming the swap is allowed
            val oldMon = getActivePokemon(player)
            if (oldMon != null) {
                PokemonItemManager.clearSwitchOutStates(oldMon)
            }
        }

        // Handle Move Cooldown
        val cooldownExpiration = state.cooldowns[moveId]
        if (cooldownExpiration != null && cooldownExpiration > currentTime) {
            val remainingSecs = (cooldownExpiration - currentTime) / 1000.0
            player.displayClientMessage(Component.translatable("cdbe.message.move_cooldown", String.format("%.1f", remainingSecs)).withStyle(net.minecraft.ChatFormatting.RED), true)
            return false
        }

        val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
        val targetMon = party.get(requestedSlot)
        if (targetMon != null) {
            val heldItem = PokemonItemManager.getActiveHeldItem(targetMon)
            
            // Assault Vest check
            if (heldItem == "cobblemon:assault_vest") {
                val rtMove = net.drachi.cde.battleengine.battle.attack.MoveRegistry.getMove(moveId)
                if (rtMove != null && rtMove.phases.all { it.basePower == 0 && it.hazardData == null }) {
                    player.displayClientMessage(Component.translatable("cdbe.message.assault_vest").withStyle(net.minecraft.ChatFormatting.RED), true)
                    return false
                }
            }
            
            // Choice Lock check
            val isChoiceItem = heldItem == "cobblemon:choice_band" || heldItem == "cobblemon:choice_specs" || heldItem == "cobblemon:choice_scarf"
            if (isChoiceItem && moveId != "cobblemon:neutral_attack") {
                val lockedMove = PokemonItemManager.getChoiceLock(targetMon)
                if (lockedMove != null && lockedMove != moveId) {
                    val moveName = net.drachi.cde.battleengine.battle.attack.MoveRegistry.getMove(lockedMove)?.cobblemonMoveId ?: lockedMove
                    player.displayClientMessage(Component.translatable("cdbe.message.choice_locked", moveName).withStyle(net.minecraft.ChatFormatting.RED), true)
                    return false
                } else if (lockedMove == null) {
                    // Note: We don't SET the choice lock here, we set it in markCast!
                    // Wait, original code sets it here: PokemonItemManager.setChoiceLock(activeMon, moveId)
                    // It's okay, but if markCast fails somehow, it's prematurely locked.
                    PokemonItemManager.setChoiceLock(targetMon, moveId)
                }
            }
        }

        return true
    }

    fun markCast(player: ServerPlayer, slotIndex: Int, moveId: String, cooldownTurns: Float, gcdMs: Long) {
        val state = playerStates.getOrPut(player.uuid) { CombatState() }
        if (slotIndex != -1 && state.activePartySlot != slotIndex) {
            setActivePokemon(player, slotIndex)
        }
        
        val baseCooldownMs = (cooldownTurns * BattleEngineConfigManager.config.turnToSecondsRatio * 1000).toLong()
        
        // Apply Global Cooldown
        val now = System.currentTimeMillis()
        state.cooldowns["GLOBAL_GCD"] = now + gcdMs
        
        val activeMon = getActivePokemon(player)
        var speed = activeMon?.speed ?: 25
        
        val speedStage = CombatStateManager.getStatStage(player.uuid, "speed")
        if (speedStage > 0) {
            speed = (speed * (2.0f + speedStage) / 2.0f).toInt()
        } else if (speedStage < 0) {
            speed = (speed * 2.0f / (2.0f - speedStage)).toInt()
        }
        
        val finalCooldownMs = calculateFinalCooldownMs(baseCooldownMs, speed)
        
        val expirationMs = System.currentTimeMillis() + finalCooldownMs
        state.cooldowns[moveId] = expirationMs
        
        net.drachi.cde.network.NetworkHandler.sendCooldownSync(player, moveId, expirationMs)
    }

    fun calculateFinalCooldownMs(baseCooldownMs: Long, speed: Int): Long {
        if (speed <= 25 || baseCooldownMs <= 0) return baseCooldownMs
        
        val reductionMs = (speed - 25) * 30L
        val minCooldownMs = if (baseCooldownMs >= 3000L) 3000L else baseCooldownMs
        return Math.max(minCooldownMs, baseCooldownMs - reductionMs)
    }

    fun getActivePokemon(player: ServerPlayer): com.cobblemon.mod.common.pokemon.Pokemon? {
        val state = playerStates[player.uuid]
        val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
        
        if (state != null && state.activePartySlot != -1) {
            val p = party.get(state.activePartySlot)
            if (p != null && p.currentHealth > 0) return p
        }
        
        // Fallback: First alive pokemon
        for (i in 0 until party.size()) {
            val p = party.get(i)
            if (p != null && p.currentHealth > 0) return p
        }
        return null
    }

    fun toggleTargetLock(player: ServerPlayer) {
        val state = playerStates.getOrPut(player.uuid) { CombatState() }
        
        if (state.lockedTargetId != -1) {
            // Unlock
            state.lockedTargetId = -1
            player.displayClientMessage(Component.translatable("cdbe.message.target_cleared").withStyle(net.minecraft.ChatFormatting.GRAY), true)
            net.drachi.cde.network.NetworkHandler.sendSyncLockedTarget(player, -1)
            return
        }
        
        // Raycast to find target
        val startPos = player.eyePosition
        val lookVec = player.lookAngle
        val range = 20.0
        val endPos = startPos.add(lookVec.scale(range))
        val box = player.boundingBox.expandTowards(lookVec.scale(range)).inflate(1.0)
        
        var closestTarget: net.minecraft.world.entity.LivingEntity? = null
        var closestDistance = range * range
        
        for (entity in player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity::class.java, box) { it != player && it.isAlive }) {
            val entityBox = entity.boundingBox.inflate(0.3)
            val hitResult = entityBox.clip(startPos, endPos)
            if (hitResult.isPresent) {
                val dist = startPos.distanceToSqr(hitResult.get())
                if (dist < closestDistance) {
                    closestDistance = dist
                    closestTarget = entity
                }
            }
        }
        
        if (closestTarget != null) {
            state.lockedTargetId = closestTarget.id
            
            player.displayClientMessage(Component.translatable("cdbe.message.target_locked", closestTarget.name.string).withStyle(net.minecraft.ChatFormatting.RED), true)
            net.drachi.cde.network.NetworkHandler.sendSyncLockedTarget(player, closestTarget.id)
        } else {
            player.displayClientMessage(Component.translatable("cdbe.message.no_target").withStyle(net.minecraft.ChatFormatting.GRAY), true)
        }
    }

    fun getLockedTarget(player: ServerPlayer): Int {
        val state = playerStates[player.uuid] ?: return -1
        return state.lockedTargetId
    }

    fun setActivePokemon(player: ServerPlayer, partySlot: Int) {
        val oldMon = getActivePokemon(player)
        if (oldMon != null) {
            PokemonItemManager.clearSwitchOutStates(oldMon)
        }
        
        CombatStateManager.clearStatStages(player.uuid)
        CombatStateManager.clearVolatileStatuses(player.uuid)
        
        val state = playerStates.getOrPut(player.uuid) { CombatState() }
        state.activePartySlot = partySlot
        
        val newMon = getActivePokemon(player)
        if (newMon != null) {
            val healthAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
            if (healthAttr != null) {
                healthAttr.baseValue = newMon.maxHealth.toDouble()
            }
            player.health = newMon.currentHealth.toFloat()
        }
        
        player.refreshDimensions()
        syncPlayerState(player)
    }

    fun syncHealthFromPlayer(player: ServerPlayer) {
        val activeMon = getActivePokemon(player)
        if (activeMon != null) {
            val playerHealthInt = kotlin.math.ceil(player.health.toDouble()).toInt()
            if (activeMon.currentHealth != playerHealthInt) {
                activeMon.currentHealth = playerHealthInt
                if (activeMon.currentHealth <= 0) {
                    handleAutoSwitch(player)
                }
            }
        }
    }

    fun handleAutoSwitch(player: ServerPlayer): Boolean {
        val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
        for (i in 0 until party.size()) {
            val p = party.get(i)
            if (p != null && p.currentHealth > 0) {
                // Client UI needs to be updated too, but the player is alive!
                setActivePokemon(player, i)
                net.drachi.cde.network.NetworkHandler.sendForceSelectedSlot(player, i)
                player.displayClientMessage(Component.translatable("cdbe.message.fainted_sent_out", p.species.name).withStyle(net.minecraft.ChatFormatting.YELLOW), false)
                return true
            }
        }
        player.displayClientMessage(Component.translatable("cdbe.message.no_usable_pokemon").withStyle(net.minecraft.ChatFormatting.RED), false)
        return false
    }
    
    fun syncPlayerState(player: ServerPlayer) {
        val activeMon = getActivePokemon(player) ?: return
        val activeItem = PokemonItemManager.getHeldItemString(activeMon)
        val lockedMoveId = PokemonItemManager.getChoiceLockedMove(activeMon) ?: "none"
        
        net.drachi.cde.network.NetworkHandler.sendPlayerStateSync(player, activeItem, lockedMoveId)
    }

    fun toggleCombatLog(player: ServerPlayer): Boolean {
        val state = playerStates.getOrPut(player.uuid) { CombatState() }
        state.showCombatLog = !state.showCombatLog
        return state.showCombatLog
    }

    fun toggleDetailedDamage(player: ServerPlayer): Boolean {
        val state = playerStates.getOrPut(player.uuid) { CombatState() }
        state.showDetailedDamage = !state.showDetailedDamage
        return state.showDetailedDamage
    }

    fun isCombatLogEnabled(player: ServerPlayer): Boolean {
        return playerStates[player.uuid]?.showCombatLog ?: true
    }

    fun isDetailedDamageEnabled(player: ServerPlayer): Boolean {
        return playerStates[player.uuid]?.showDetailedDamage ?: true
    }
}
