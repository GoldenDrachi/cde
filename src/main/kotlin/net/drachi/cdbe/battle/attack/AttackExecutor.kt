package net.drachi.cdbe.battle.attack

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*


import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.drachi.cdbe.CobblemonDungeonBattleEngine
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundSource
import net.minecraft.sounds.SoundEvents
import net.drachi.cdbe.util.ParticleUtil

object AttackExecutor {

    fun handleCastRequest(player: net.minecraft.server.level.ServerPlayer, partySlotIndex: Int, moveSlotIndex: Int) {
        // 1. Coexistence Check: Is the PLAYER in a turn-based battle?
        val battle: PokemonBattle? = Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player)
        if (battle != null) {
            (player as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.turn_based_combat").withStyle(net.minecraft.ChatFormatting.RED), true)
            return
        }

        if (CombatStateManager.hasVolatileStatus(player.uuid, "stun")) {
            (player as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.stunned").withStyle(net.minecraft.ChatFormatting.RED), true)
            return
        }

        // CDDE Freeze Check
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN)) {
            val effect = player.getEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN)
            if (effect != null && effect.amplifier >= 200) {
                return
            }
        }
        
        // 2. Find the selected Pokemon in the player's party
        val party = Cobblemon.storage.getParty(player)
        val selectedPokemon = party.get(partySlotIndex)
        
        if (selectedPokemon == null || selectedPokemon.currentHealth <= 0) {
            (player as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.pokemon_unable_to_battle").withStyle(net.minecraft.ChatFormatting.RED), true)
            return
        }

        // 3. New Check: Is the Pokemon already sent out in the world?
        if (selectedPokemon.entity != null) {
            (player as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.pokemon_already_sent_out", selectedPokemon.species.name).withStyle(net.minecraft.ChatFormatting.RED), true)
            return
        }

        // 4. Get the requested move from the lead Pokemon's moveset
        val moveId: String
        val fullId: String
        var cobblemonMove: com.cobblemon.mod.common.api.moves.Move? = null

        if (moveSlotIndex == -1) {
            moveId = "neutral_attack"
            fullId = "cobblemon:neutral_attack"
        } else {
            val moveSet = selectedPokemon.moveSet
            val moves = moveSet.getMoves()
            
            if (moveSlotIndex >= moves.size) {
                return // Slot is empty
            }
            
            cobblemonMove = moves[moveSlotIndex]
            if (cobblemonMove == null) return
            
            moveId = cobblemonMove.template.name.lowercase().replace(" ", "").replace("_", "")
            fullId = "cobblemon:$moveId"
        }

        // 5. Look up our Real-Time Move definition
        val rtMove = MoveRegistry.getMove(fullId)
        if (rtMove == null) {
            // Unconfigured Move logic
            val displayName = cobblemonMove?.template?.displayName?.string ?: "Neutral Attack"
            (player as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.move_not_configured", displayName, fullId).withColor(0xFFCC00), false)
            return
        }

        // 6. Check if currently charging or recharging
        if (DelayedActionManager.isCharging(player.uuid) || DelayedActionManager.isRecharging(player.uuid)) {
            return
        }

        // 7. Check Gamerules and Cooldowns
        if (!PlayerCombatManager.canCast(player, partySlotIndex, fullId)) {
            return
        }

        // 8. PP Check
        if (cobblemonMove != null && cobblemonMove.currentPp <= 0) {
            (player as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.no_pp", cobblemonMove.template.displayName.string).withStyle(net.minecraft.ChatFormatting.RED), true)
            return
        }

        // 9. Decrement PP
        if (cobblemonMove != null) {
            val ppMult = net.drachi.cdbe.config.ConfigManager.config.ppMultiplier
            if (ppMult > 0) {
                var cost = 1.0 / ppMult
                while (cost >= 1.0) {
                    cobblemonMove.currentPp -= 1
                    cost -= 1.0
                }
                if (kotlin.random.Random.nextDouble() < cost) {
                    cobblemonMove.currentPp -= 1
                }
            }
        }

        // 10. Process HP Cost
        var totalHpCost = rtMove.hpCostFlat
        if (rtMove.hpCostPercent > 0) {
            totalHpCost += (selectedPokemon.maxHealth * (rtMove.hpCostPercent / 100.0)).toInt()
        }
        
        val createsSubstitute = rtMove.phases.any { phase -> phase.selfStatusEffects?.any { it.type == "substitute" } == true }
        
        if (createsSubstitute && CombatStateManager.hasVolatileStatus(player.uuid, "substitute")) {
            (player as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.substitute_already_active").withStyle(net.minecraft.ChatFormatting.RED), true)
            return
        }
        
        if (totalHpCost > 0) {
            if (selectedPokemon.currentHealth <= totalHpCost) {
                (player as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.too_weak", selectedPokemon.species.name).withStyle(net.minecraft.ChatFormatting.RED), true)
                return
            }
            selectedPokemon.currentHealth -= totalHpCost
            player.health = selectedPokemon.currentHealth.toFloat()
            
            // If the move creates a substitute, automatically set the substitute HP to the cost spent
            if (createsSubstitute) {
                CombatStateManager.setSubstituteHp(player.uuid, totalHpCost)
                (player as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.substitute_created", selectedPokemon.species.name, totalHpCost).withStyle(net.minecraft.ChatFormatting.YELLOW), false)
            }
        }

        // 11. Execute the Phases
        var currentDelayTicks = 0L
        val heldItem = PokemonItemManager.getActiveHeldItem(selectedPokemon)
        var consumedPowerHerb = false
        val executionId = java.util.UUID.randomUUID()

        for (phase in rtMove.phases) {
            val isPowerHerbSkip = phase.skipWithPowerHerb && heldItem == "cobblemon:power_herb" && !consumedPowerHerb && phase.chargeupTicks > 0
            if (isPowerHerbSkip) {
                PokemonItemManager.consumeItem(selectedPokemon, "cobblemon:power_herb")
                (player as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.power_herb", selectedPokemon.species.name).withStyle(net.minecraft.ChatFormatting.YELLOW), false)
                consumedPowerHerb = true
            } else {
                currentDelayTicks += phase.chargeupTicks
            }
            
            val moveTemplate = cobblemonMove?.template ?: com.cobblemon.mod.common.api.moves.Moves.getByName("tackle")
            if (moveTemplate == null) return
            
            val queuedPhase = if (isPowerHerbSkip) phase.copy(chargeupTicks = 0) else phase
            DelayedActionManager.queuePhase(executionId, player, selectedPokemon, rtMove, moveTemplate, queuedPhase, currentDelayTicks)
            
            val durationTicks = (phase.attackDurationTurns * net.drachi.cdbe.config.ConfigManager.config.turnToSecondsRatio * 20).toLong()
            currentDelayTicks += durationTicks
        }
        // 11. Mark Cast (Apply Cooldowns and Lock Active Slot)
        val gcdMs = maxOf(500L, currentDelayTicks * 50L)
        PlayerCombatManager.markCast(player, partySlotIndex, fullId, rtMove.cooldownTurns, gcdMs)
        
        // Sync item locks and status
        PlayerCombatManager.syncPlayerState(player)
    }

    // ——— Tracking state passed between sub-functions within a single move execution ———
    fun executePhaseNow(
        caster: net.minecraft.world.entity.LivingEntity,
        pokemonStats: Pokemon,
        move: RealTimeMove,
        phase: MovePhase,
        moveTemplate: com.cobblemon.mod.common.api.moves.MoveTemplate,
        isRepeating: Boolean = false,
        executionId: java.util.UUID? = null
    ) {
        val world = caster.level() as? net.minecraft.server.level.ServerLevel ?: return
        val heldItem = PokemonItemManager.getActiveHeldItem(pokemonStats)
        val ctx = MoveContext(
            caster = caster,
            pokemonStats = pokemonStats,
            move = move,
            phase = phase,
            moveTemplate = moveTemplate,
            world = world,
            showLog = (caster as? net.minecraft.server.level.ServerPlayer)?.let { net.drachi.cdbe.battle.attack.PlayerCombatManager.isCombatLogEnabled(it) } ?: false,
            showDetail = (caster as? net.minecraft.server.level.ServerPlayer)?.let { net.drachi.cdbe.battle.attack.PlayerCombatManager.isDetailedDamageEnabled(it) } ?: false,
            heldItem = heldItem,
            isRepeating = isRepeating,
            throatSprayTriggered = move.isSoundMove && heldItem == "cobblemon:throat_spray"
        )

        // 0. Play move sounds and cries
        playMoveSounds(ctx)

        if (!isRepeating) {
            caster.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true)
            if (caster is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                try {
                    val cat = moveTemplate.damageCategory.name.lowercase()
                    val anim = if (cat == "physical") "physical_attack" else "special_attack"
                    (caster as com.cobblemon.mod.common.entity.PosableEntity).playAnimation(anim)
                    (caster as com.cobblemon.mod.common.entity.PosableEntity).playAnimation("attack")
                } catch (e: Exception) {}
            }
        }

        try {
            // Check for phase-specific cooldowns (like Hyper Beam recharge)
            if (phase.attackDurationTurns > 0) {
                // Not applying recharge volatile status here since it's an attack duration lock
            } 
            
            // 1. Resolve + Filter Targets
            val strategy = AttackStrategyRegistry.getStrategy(phase.attackType)
            if (strategy == null) {
                applySelfStatusEffects(ctx)
                return // Empty phase (e.g. pure chargeup/delay)
            }
            
            val rawTargets = strategy.resolveTargets(ctx)
            if (rawTargets == null) {
                applySelfStatusEffects(ctx)
                return // SELF, DOMAIN, HAZARD handled internally
            }
            val finalTargets = filterAndLimitTargets(rawTargets, ctx)
            if (finalTargets.isEmpty()) {
                if (phase.abortMoveOnMiss && executionId != null) {
                    DelayedActionManager.cancelExecution(executionId)
                }
                return
            }
            
            applySelfStatusEffects(ctx)
            
            if (phase.unmountTarget) {
                caster.passengers.toList().forEach { p ->
                    if (finalTargets.contains(p)) p.stopRiding()
                }
            }

            // 2. Process each target
            for (targetEntity in finalTargets) {
                processTarget(ctx, targetEntity, finalTargets.size)
            }

            // 3. Post-attack effects
            processPostAttackItems(ctx)
            processPostAttackEffects(ctx, finalTargets)
        } finally {
            // 4. Sequential usage logic
            if (ctx.phase.sequentialData != null) {
                if (ctx.anyTargetHit) {
                    CombatStateManager.recordSequentialUse(ctx.caster.uuid, ctx.move.cobblemonMoveId, ctx.phase.sequentialData.maxStacks, ctx.phase.sequentialData.resetOnMax, ctx.phase.sequentialData.startWithStackIfStatus)
                } else {
                    CombatStateManager.resetSequentialUse(ctx.caster.uuid)
                }
            } else {
                // Only reset if a DIFFERENT move was used (not the same move's other phases)
                val storedMoveId = CombatStateManager.getSequentialMoveId(ctx.caster.uuid)
                if (storedMoveId != null && storedMoveId != ctx.move.cobblemonMoveId) {
                    CombatStateManager.resetSequentialUse(ctx.caster.uuid)
                }
            }
        }
    }

    // ————————————————————————————————————————————————————————————————————————————————

    private fun playMoveSounds(ctx: MoveContext) {
        if (ctx.isRepeating) return // Only play sounds on initial cast

        val world = ctx.world
        val pos = ctx.caster.blockPosition()

        // 1. Play Cry
        var playedCry = false
        val activeEntity = net.drachi.cdbe.battle.utility.EntityUtil.findLivingEntityByUUID(world.server, ctx.pokemonStats.uuid)
        if (activeEntity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
            try {
                activeEntity.cry()
                playedCry = true
            } catch (e: Exception) {
                // Ignore and fallback
            }
        }
        
        if (!playedCry) {
            val speciesName = ctx.pokemonStats.species.name.lowercase()
            val formName = ctx.pokemonStats.form.name.lowercase()
            val cryPath = if (formName != "normal") "pokemon.$speciesName.$formName.cry" else "pokemon.$speciesName.cry"
            
            val cryRes = net.minecraft.resources.ResourceLocation.tryParse("cobblemon:$cryPath")
            if (cryRes != null) {
                val soundEvent = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(cryRes)
                world.playSound(null, pos, soundEvent, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f)
            }
        }

        // 2. Play Move Sound
        var moveSoundEvent: net.minecraft.sounds.SoundEvent? = null

        // Priority 1: JSON Custom Sound
        if (ctx.phase.soundEffect != null) {
            val res = net.minecraft.resources.ResourceLocation.tryParse(ctx.phase.soundEffect)
            if (res != null) {
                moveSoundEvent = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(res)
            }
        }

        // Priority 2: Cobblemon Move Sound (cobblemon:move.<move_id>)
        if (moveSoundEvent == null) {
            val moveId = ctx.move.cobblemonMoveId.replace("cobblemon:", "")
            val res = net.minecraft.resources.ResourceLocation.tryParse("cobblemon:move.$moveId")
            if (res != null) {

                // Try playing the Cobblemon sound event directly
                moveSoundEvent = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(res)
            }
        }

        // Priority 3: Fallbacks
        if (moveSoundEvent == null) {
            val categoryName = ctx.moveTemplate.damageCategory.name
            moveSoundEvent = if (categoryName.equals("physical", ignoreCase = true)) {
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG
            } else if (categoryName.equals("special", ignoreCase = true)) {
                net.minecraft.sounds.SoundEvents.ILLUSIONER_CAST_SPELL
            } else {
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP
            }
        }

        if (moveSoundEvent != null) {
            world.playSound(null, pos, moveSoundEvent, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f)
        }
    }

    // ————————————————————————————————————————————————————————————————————————————————

    private fun filterAndLimitTargets(targets: Set<net.minecraft.world.entity.LivingEntity>, ctx: MoveContext): List<net.minecraft.world.entity.LivingEntity> {
        val filtered = targets.filter { entity ->
            if (entity == ctx.caster) return@filter false
            true
        }.filter { entity ->
            ctx.phase.hitsFriendlies || !net.drachi.cdbe.api.CDBEApi.isFriendly(ctx.caster, entity)
        }
        return if (ctx.phase.multipleTargets) filtered.toList()
        else filtered.sortedBy { it.distanceToSqr(ctx.caster) }.take(1)
    }

    // ————————————————————————————————————————————————————————————————————————————————

    /**
     * Single unified accuracy check. Returns true if the attack hits.
     */
    private fun rollAccuracy(ctx: MoveContext, targetUuid: java.util.UUID, defenderLevel: Int): Boolean {
        val baseAccuracy = ctx.phase.accuracy?.toInt() ?: ctx.moveTemplate.accuracy.toInt()
        if (baseAccuracy !in 1..100) return true

        val accStage = CombatStateManager.getStatStage(ctx.caster.uuid, "accuracy")
        val evaStage = CombatStateManager.getStatStage(targetUuid, "evasion")
        var stageDiff = (accStage - evaStage).coerceIn(-6, 6)

        val multiplier = if (stageDiff >= 0) (3.0f + stageDiff) / 3.0f else 3.0f / (3.0f - stageDiff)

        var finalAccuracy = baseAccuracy.toFloat() * multiplier
        var forceMiss = false

        if (ctx.phase.bypasses?.isOhko == true) {
            if (defenderLevel > ctx.pokemonStats.level) {
                forceMiss = true
            } else {
                finalAccuracy = (ctx.pokemonStats.level - defenderLevel + 30).toFloat()
            }
        }

        val roll = kotlin.random.Random.nextInt(100) + 1
        return roll <= finalAccuracy && !forceMiss
    }

    private fun processTarget(ctx: MoveContext, targetEntity: net.minecraft.world.entity.LivingEntity, targetCount: Int) {
        if (targetEntity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
            applyDamageToPokemon(ctx, targetEntity, targetCount)
        } else if (targetEntity is net.minecraft.server.level.ServerPlayer) {
            if (PlayerCombatManager.getActivePokemon(targetEntity) != null) {
                applyDamageToPlayer(ctx, targetEntity, targetCount)
            } else {
                applyDamageToGenericEntity(ctx, targetEntity)
            }
        } else {
            applyDamageToGenericEntity(ctx, targetEntity)
        }
    }
    private fun applyDamageToPokemon(ctx: MoveContext, targetEntity: com.cobblemon.mod.common.entity.pokemon.PokemonEntity, targetCount: Int) {
        val defenderStats = targetEntity.pokemon

        if (!net.drachi.cdbe.config.ConfigManager.config.isPvpEnabled && defenderStats.getOwnerUUID() != null) {
            val attackerOwner = (ctx.caster as? net.minecraft.world.entity.player.Player)?.uuid ?: (ctx.caster as? com.cobblemon.mod.common.entity.pokemon.PokemonEntity)?.pokemon?.getOwnerUUID()
            if (attackerOwner != null && attackerOwner != defenderStats.getOwnerUUID()) {
                return // Block attack if from another player's team and PvP is off
            }
        }

        if (!rollAccuracy(ctx, targetEntity.uuid, defenderStats.level)) {
            if (ctx.showLog) (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.attack_missed", ctx.pokemonStats.species.name, targetEntity.name.string).withStyle(net.minecraft.ChatFormatting.RED), false)
            if (ctx.heldItem == "cobblemon:blunder_policy") ctx.blunderPolicyTriggered = true
            return
        }

        ctx.anyTargetHit = true

        val result = DamageCalculator.calculateDamage(ctx.pokemonStats, defenderStats, ctx.move, ctx.phase, ctx.moveTemplate, targetCount, ctx.caster, targetEntity)
        val damage = result.finalDamage

        if (result.breakdown.movePower > 0.0) {
            if (damage > 0) {
                ctx.totalDamageDealt += damage
                if (ctx.heldItem == "cobblemon:life_orb") ctx.lifeOrbTriggered = true
            }

            // Substitute check (Bug #4 fix — was missing for PokemonEntity)
            if (CombatStateManager.hasVolatileStatus(targetEntity.uuid, "substitute") && ctx.phase.bypasses?.ignoresSubstitute != true) {
                val excess = CombatStateManager.damageSubstitute(targetEntity.uuid, damage)
                if (ctx.showLog) {
                    val msg = if (excess >= 0) "§eThe Substitute took the damage and broke!" else "§eThe Substitute took the damage!"
                    (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable(msg), false)
                }
            } else {
                val newHealth = defenderStats.currentHealth - damage
                defenderStats.currentHealth = if (newHealth < 0) 0 else newHealth
                targetEntity.hurt(if (ctx.caster is net.minecraft.world.entity.player.Player) ctx.caster.damageSources().playerAttack(ctx.caster) else ctx.caster.damageSources().mobAttack(ctx.caster), 0.1f)
                targetEntity.lastHurtByMob = ctx.caster
            }

            ctx.world.playSound(null, targetEntity.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.0f)
            ItemEffectHandler.onHitReceived(defenderStats, targetEntity, ctx.caster, damage, result.breakdown.typeEffectiveness > 1.0f, ctx.moveTemplate.elementalType.name.lowercase())
            net.drachi.cdbe.battle.ability.AbilityExecutor.executeOnHitReceived(defenderStats, targetEntity, ctx.caster, damage)
            net.drachi.cdbe.battle.ability.AbilityExecutor.executeOnDamageDealt(ctx.pokemonStats, ctx.caster, targetEntity, damage)

            if (net.drachi.cdbe.config.ConfigManager.config.debugLogging) {
                CobblemonDungeonBattleEngine.LOGGER.info("Hit ${defenderStats.species.name} for $damage damage! HP: ${defenderStats.currentHealth}/${defenderStats.maxHealth}")
            }
            if (ctx.showLog) {
                (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.dealt_damage_pokemon", damage, targetEntity.name.string, defenderStats.currentHealth, defenderStats.maxHealth), false)
                if (ctx.showDetail) (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.let { sendDamageBreakdown(it, result.breakdown) }
            }
            
            // Log damage received to target's owner
            val ownerUUID = targetEntity.pokemon.getOwnerUUID()
            if (ownerUUID != null) {
                val owner = targetEntity.level().server?.playerList?.getPlayer(ownerUUID)
                if (owner != null && net.drachi.cdbe.battle.attack.PlayerCombatManager.isCombatLogEnabled(owner)) {
                    owner.displayClientMessage(Component.translatable("cdbe.message.received_damage_pokemon", defenderStats.species.name, damage, ctx.caster.name.string), false)
                }
            }
        }

        if (defenderStats.currentHealth == 0) handleFaint(ctx, defenderStats, targetEntity)
        applyMoveStatusEffects(ctx, targetEntity)
    }

    private fun applyDamageToPlayer(ctx: MoveContext, targetEntity: ServerPlayer, targetCount: Int) {
        if (!net.drachi.cdbe.config.ConfigManager.config.isPvpEnabled) {
            val attackerOwner = (ctx.caster as? net.minecraft.world.entity.player.Player)?.uuid ?: (ctx.caster as? com.cobblemon.mod.common.entity.pokemon.PokemonEntity)?.pokemon?.getOwnerUUID()
            if (attackerOwner != null && attackerOwner != targetEntity.uuid) {
                return // Block attack if from another player's team and PvP is off
            }
        }

        val defenderStats = PlayerCombatManager.getActivePokemon(targetEntity)
        if (defenderStats != null) {
            if (!rollAccuracy(ctx, targetEntity.uuid, defenderStats.level)) {
                if (ctx.showLog) (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.attack_missed", ctx.pokemonStats.species.name, targetEntity.name.string).withStyle(net.minecraft.ChatFormatting.RED), false)
                if (ctx.heldItem == "cobblemon:blunder_policy") ctx.blunderPolicyTriggered = true
                return
            }

            val result = DamageCalculator.calculateDamage(ctx.pokemonStats, defenderStats, ctx.move, ctx.phase, ctx.moveTemplate, targetCount, ctx.caster, targetEntity)
            val damage = result.finalDamage

            if (result.breakdown.movePower > 0.0) {
                if (damage > 0) {
                    ctx.totalDamageDealt += damage
                    if (ctx.heldItem == "cobblemon:life_orb") ctx.lifeOrbTriggered = true
                }

                if (CombatStateManager.hasVolatileStatus(targetEntity.uuid, "substitute") && ctx.phase.bypasses?.ignoresSubstitute != true) {
                    val excess = CombatStateManager.damageSubstitute(targetEntity.uuid, damage)
                    if (ctx.showLog) {
                        val msg = if (excess >= 0) "§eThe Substitute took the damage and broke!" else "§eThe Substitute took the damage!"
                        (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable(msg), false)
                    }
                } else {
                    val newHealth = defenderStats.currentHealth - damage
                    defenderStats.currentHealth = if (newHealth < 0) 0 else newHealth
                    
                    // Sync with Minecraft visual health
                    val healthRatio = defenderStats.currentHealth.toFloat() / defenderStats.maxHealth.toFloat()
                    val newMcHealth = targetEntity.maxHealth * healthRatio
                    val mcDamage = targetEntity.health - newMcHealth
                    if (mcDamage > 0) {
                        targetEntity.hurt(if (ctx.caster is net.minecraft.world.entity.player.Player) ctx.caster.damageSources().playerAttack(ctx.caster) else ctx.caster.damageSources().mobAttack(ctx.caster), mcDamage)
                    }
                    ctx.caster.setLastHurtMob(targetEntity)
                    targetEntity.setLastHurtByMob(ctx.caster)
                    
                    // FORCE SET AI TARGETS
                    if (targetEntity is net.minecraft.world.entity.Mob) {
                        val state = net.drachi.cdbe.battle.utility.SpawnManager.getEntityHostility(targetEntity)
                        if (state != net.drachi.cdbe.battle.utility.HostilityState.PEACEFUL) {
                            targetEntity.target = ctx.caster
                            if (targetEntity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                                targetEntity.brain.setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, ctx.caster)
                            }
                        }
                    }
                    if (ctx.caster is net.minecraft.server.level.ServerPlayer) {
                        val world = ctx.caster.level() as net.minecraft.server.level.ServerLevel
                        val pets = world.getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java, ctx.caster.boundingBox.inflate(30.0)) { it.pokemon.getOwnerUUID() == ctx.caster.uuid }
                        for (pet in pets) {
                            pet.target = targetEntity
                            pet.brain.setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, targetEntity)
                        }
                    }
                    if (targetEntity is net.minecraft.server.level.ServerPlayer) {
                        targetEntity.displayClientMessage(Component.literal("§c⚔ §fTook §c${damage.toInt()} §fdamage from §e${ctx.caster.name.string}"), false)
                    }
                }

                ctx.world.playSound(null, targetEntity.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.0f)
                ItemEffectHandler.onHitReceived(defenderStats, targetEntity, ctx.caster, damage, result.breakdown.typeEffectiveness > 1.0f, ctx.moveTemplate.elementalType.name.lowercase())
                net.drachi.cdbe.battle.ability.AbilityExecutor.executeOnHitReceived(defenderStats, targetEntity, ctx.caster, damage)
                net.drachi.cdbe.battle.ability.AbilityExecutor.executeOnDamageDealt(ctx.pokemonStats, ctx.caster, targetEntity, damage)

                if (net.drachi.cdbe.config.ConfigManager.config.debugLogging) {
                    CobblemonDungeonBattleEngine.LOGGER.info("Hit Player ${targetEntity.name.string}'s ${defenderStats.species.name} for $damage damage! HP: ${defenderStats.currentHealth}/${defenderStats.maxHealth}")
                }
                if (ctx.showLog) {
                    (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.dealt_damage_pokemon_owner", damage, targetEntity.name.string, defenderStats.species.name, defenderStats.currentHealth, defenderStats.maxHealth), false)
                    if (ctx.showDetail) (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.let { sendDamageBreakdown(it, result.breakdown) }
                }
                
                // Log damage received to target player
                if (net.drachi.cdbe.battle.attack.PlayerCombatManager.isCombatLogEnabled(targetEntity)) {
                    targetEntity.displayClientMessage(Component.translatable("cdbe.message.received_damage_pokemon", defenderStats.species.name, damage, ctx.caster.name.string), false)
                }
            }

            applyMoveStatusEffects(ctx, targetEntity)
        } else {
            // Player has no alive pokemon — generic vanilla damage
            val actualPower = if (ctx.phase.basePower > 0) ctx.phase.basePower.toDouble() else ctx.moveTemplate.power
            if (actualPower > 0.0) {
                val genericDamage = (actualPower.toFloat() / 10.0f).coerceAtLeast(1.0f)
                targetEntity.hurt(if (ctx.caster is net.minecraft.world.entity.player.Player) ctx.caster.damageSources().playerAttack(ctx.caster) else ctx.caster.damageSources().mobAttack(ctx.caster), genericDamage)
                ctx.caster.setLastHurtMob(targetEntity)
                targetEntity.setLastHurtByMob(ctx.caster)
                
                // FORCE SET AI TARGETS
                if (targetEntity is net.minecraft.world.entity.Mob) {
                    targetEntity.target = ctx.caster
                    if (targetEntity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                        targetEntity.brain.setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, ctx.caster)
                    }
                }
                if (ctx.caster is net.minecraft.server.level.ServerPlayer) {
                    val world = ctx.caster.level() as net.minecraft.server.level.ServerLevel
                    val pets = world.getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java, ctx.caster.boundingBox.inflate(30.0)) { it.pokemon.getOwnerUUID() == ctx.caster.uuid }
                    for (pet in pets) {
                        pet.target = targetEntity
                        pet.brain.setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, targetEntity)
                    }
                }
                ctx.caster.setLastHurtMob(targetEntity)
                targetEntity.setLastHurtByMob(ctx.caster)
                ctx.world.playSound(null, targetEntity.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.0f)
            }
        }
    }

    private fun applyDamageToGenericEntity(ctx: MoveContext, targetEntity: net.minecraft.world.entity.LivingEntity) {
        val actualPower = if (ctx.phase.basePower > 0) ctx.phase.basePower.toDouble() else ctx.moveTemplate.power
        if (actualPower <= 0.0) return

        val genericDamage = (actualPower.toFloat() / 10.0f).coerceAtLeast(1.0f)
        val dmgInt = genericDamage.toInt()
        if (dmgInt > 0) {
            ctx.totalDamageDealt += dmgInt
            if (ctx.heldItem == "cobblemon:life_orb") ctx.lifeOrbTriggered = true
        }
        targetEntity.hurt(if (ctx.caster is net.minecraft.world.entity.player.Player) ctx.caster.damageSources().playerAttack(ctx.caster) else ctx.caster.damageSources().mobAttack(ctx.caster), genericDamage)
        ctx.world.playSound(null, targetEntity.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.0f)

        if (net.drachi.cdbe.config.ConfigManager.config.debugLogging) {
            CobblemonDungeonBattleEngine.LOGGER.info("Hit Generic Entity ${targetEntity.name.string} for $genericDamage vanilla damage!")
        }
        if (ctx.showLog) {
            (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.dealt_vanilla_damage", genericDamage, targetEntity.name.string), false)
        }
    }

    // ——— Faint + EXP ————————————————————————————————————————————————————————————————

    private fun handleFaint(ctx: MoveContext, defenderStats: Pokemon, targetEntity: PokemonEntity) {
        val expYield = defenderStats.form.baseExperienceYield
        val level = defenderStats.level
        val expGained = (expYield * level) / 7

        if (expGained > 0) {
            if (net.drachi.cdbe.config.ConfigManager.config.debugLogging) {
                CobblemonDungeonBattleEngine.LOGGER.info("EXP Calc: ($expYield Base Yield * $level Level) / 7 = $expGained EXP gained by ${ctx.pokemonStats.species.name}")
            }
            ctx.pokemonStats.addExperienceWithPlayer((ctx.caster as? net.minecraft.server.level.ServerPlayer) ?: return, object : com.cobblemon.mod.common.api.pokemon.experience.ExperienceSource {}, expGained)
            if (ctx.showLog) {
                (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.exp_gained", ctx.pokemonStats.species.name, expGained, expYield, level), false)
            }
        }

        targetEntity.kill()
    }
    
    // ——— Self Status Effects ————————————————————————————————————————————————————————
    
    private fun applySelfStatusEffects(ctx: MoveContext) {
        val effects = ctx.phase.selfStatusEffects ?: return
        for (effect in effects) {
            if (kotlin.random.Random.nextInt(100) < effect.chance) {
                StatusEffectHandler.applyToTarget(ctx.caster, effect, ctx.caster as? net.minecraft.server.level.ServerPlayer, ctx.showLog)
            }
        }
    }

    // ——— Status Effect Application ——————————————————————————————————————————————————

    private fun applyMoveStatusEffects(ctx: MoveContext, target: net.minecraft.world.entity.LivingEntity) {
        val effects = ctx.phase.statusEffects ?: return
        for (effect in effects) {
            if (kotlin.random.Random.nextInt(100) < effect.chance) {
                StatusEffectHandler.applyToTarget(target, effect, ctx.caster as? net.minecraft.server.level.ServerPlayer, ctx.showLog)
            }
        }
    }

    // ——— Post-Attack Items ——————————————————————————————————————————————————————————

    private fun processPostAttackItems(ctx: MoveContext) {
        if (ctx.heldItem == null) return
        val itemStrategy = net.drachi.cdbe.battle.item.ItemRegistry.getItem(ctx.heldItem)
        itemStrategy?.onPostAttack(ctx)
        
        // Also handle triggered items that were consumed but their flags are set
        if (ctx.blunderPolicyTriggered && ctx.heldItem != "cobblemon:blunder_policy") {
            net.drachi.cdbe.battle.item.ItemRegistry.getItem("cobblemon:blunder_policy")?.onPostAttack(ctx)
        }
        if (ctx.throatSprayTriggered && ctx.heldItem != "cobblemon:throat_spray") {
            net.drachi.cdbe.battle.item.ItemRegistry.getItem("cobblemon:throat_spray")?.onPostAttack(ctx)
        }
        if (ctx.lifeOrbTriggered && ctx.heldItem != "cobblemon:life_orb") {
             net.drachi.cdbe.battle.item.ItemRegistry.getItem("cobblemon:life_orb")?.onPostAttack(ctx)
        }
    }

    // ——— Post-Attack Effects (HP, Special States, Mobility, Multi-Hit, Recharge) —

    private fun processPostAttackEffects(ctx: MoveContext, finalTargets: List<net.minecraft.world.entity.LivingEntity>) {
        processHealthManipulation(ctx)
        processSpecialStates(ctx, finalTargets)
        processRetreat(ctx)
        processMultiHit(ctx)
        processRecharge(ctx)
    }

    private fun processHealthManipulation(ctx: MoveContext) {
        val hpMod = ctx.phase.healthManipulationData ?: return
        var hpChange = 0

        if (hpMod.recoilPercent > 0.0) hpChange -= (ctx.totalDamageDealt * (hpMod.recoilPercent / 100.0)).toInt()
        if (hpMod.drainPercent > 0.0) hpChange += (ctx.totalDamageDealt * (hpMod.drainPercent / 100.0)).toInt()
        if (hpMod.healSelfPercent > 0.0) hpChange += (ctx.pokemonStats.maxHealth * (hpMod.healSelfPercent / 100.0)).toInt()

        if (hpChange != 0) {
            val newHp = (ctx.pokemonStats.currentHealth + hpChange).coerceIn(0, ctx.pokemonStats.maxHealth)
            ctx.pokemonStats.currentHealth = newHp
            val healthRatio = newHp.toFloat() / ctx.pokemonStats.maxHealth.toFloat()
            ctx.caster.health = ctx.caster.maxHealth * healthRatio

            if (hpChange < 0 && ctx.showLog) {
                (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.recoil_damage", ctx.pokemonStats.species.name, -hpChange), false)
            } else if (hpChange > 0 && ctx.showLog) {
                (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.recovered_hp", ctx.pokemonStats.species.name, hpChange), false)
            }
        }

        if (hpMod.cleansesStatus != null) {
            if (hpMod.cleansesStatus.contains("all")) {
                CombatStateManager.clearVolatileStatuses(ctx.caster.uuid)
            } else {
                hpMod.cleansesStatus.forEach { CombatStateManager.removeVolatileStatus(ctx.caster.uuid, it) }
            }
        }
    }

    private fun processSpecialStates(ctx: MoveContext, finalTargets: List<net.minecraft.world.entity.LivingEntity>) {
        val special = ctx.phase.specialStateData ?: return

        if (special.statReset) {
            for (targetEntity in finalTargets) {
                CombatStateManager.clearStatStages(targetEntity.uuid)
            }
            CombatStateManager.clearStatStages(ctx.caster.uuid)
            if (ctx.showLog) (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.stat_changes_eliminated").withStyle(net.minecraft.ChatFormatting.AQUA), false)
        }
    }

    private fun processRetreat(ctx: MoveContext) {
        val retreat = ctx.phase.mobilityData?.retreatForce ?: return
        if (retreat <= 0.0f) return
        val lookVec = ctx.caster.lookAngle
        val speed = retreat.toDouble()
        ctx.caster.deltaMovement = ctx.caster.deltaMovement.add(lookVec.multiply(-speed, -speed, -speed))
        ctx.caster.hurtMarked = true
    }

    private fun processMultiHit(ctx: MoveContext) {
        if (ctx.isRepeating || ctx.phase.multiHitData == null) return
        val hits = kotlin.random.Random.nextInt(ctx.phase.multiHitData.minHits, ctx.phase.multiHitData.maxHits + 1)
        if (hits > 1) {
            DelayedActionManager.queueRepeatingPhase(ctx.caster, ctx.pokemonStats, ctx.move, ctx.moveTemplate, ctx.phase, hits - 1, ctx.phase.multiHitData.delayTicks)
        }
    }

    private fun processRecharge(ctx: MoveContext) {
        if (ctx.isRepeating) return
        if (ctx.phase.attackType == net.drachi.cdbe.battle.attack.AttackTypeEnum.SELF && ctx.phase.attackDurationTurns > 0) { DelayedActionManager.applyRecharge(ctx.caster.uuid, ctx.phase.attackDurationTurns, ctx.caster, ctx.phase.attackMessage) }
    }

    // ——— Helpers ———————————————————————————————————————————————————————————————————

    private fun sendDamageBreakdown(player: net.minecraft.server.level.ServerPlayer, b: DamageBreakdown) {
        val category = if (b.isSpecial) "SpA/SpD" else "Atk/Def"
        val stageInfo = if (b.attackStage != 0 || b.defenseStage != 0) {
            " §7(stages: ${formatStage(b.attackStage)}/${formatStage(b.defenseStage)})"
        } else ""

        (player as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.combat_log_base", b.moveName, b.movePower.toInt(), b.attackerLevel, category, b.rawAttackStat, b.rawDefenseStat, stageInfo), false)

        val modifiers = mutableListOf<String>()
        if (b.isCrit) modifiers.add("§6CRIT")
        if (b.hasStab) modifiers.add("§bSTAB")
        if (b.typeEffectiveness > 1.0f) modifiers.add("§aSE ×${b.typeEffectiveness}")
        else if (b.typeEffectiveness < 1.0f && b.typeEffectiveness > 0.0f) modifiers.add("§cNVE ×${b.typeEffectiveness}")
        else if (b.typeEffectiveness == 0.0f) modifiers.add("§8IMMUNE")
        if (b.multiTargetMod) modifiers.add("§dMulti ×0.5")
        if (b.isBurned) modifiers.add("§4Burn ×0.5")
        if (b.weatherMultiplier != 1.0f) modifiers.add("§3Weather ×${b.weatherMultiplier}")
        modifiers.add("§7Roll: §f${b.randomRoll}/100")

        (player as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.translatable("cdbe.message.combat_log_mods", modifiers.joinToString(" §8| ")), false)
    }

    private fun formatStage(stage: Int): String {
        return if (stage >= 0) "+$stage" else "$stage"
    }
    
    // Status effect application is now handled by StatusEffectHandler
}


