package net.drachi.cde.ai

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.player.Player
import com.cobblemon.mod.common.api.moves.Move
import java.util.EnumSet

class HostileRealTimeGoal(private val pokemonEntity: PokemonEntity) : Goal() {
    private var cooldownTicks = 0
    private var target: LivingEntity? = null
    
    private var selectedCobblemonMove: Move? = null
    private var selectedRtMove: RealTimeMove? = null

    init {
        this.flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean {
        // CDDE Freeze Check
        if (pokemonEntity.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN)) {
            val effect = pokemonEntity.getEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN)
            if (effect != null && effect.amplifier >= 200) {
                return false
            }
        }

        if (target != null && (!target!!.isAlive || net.drachi.cde.battleengine.api.BattleEngineApi.isFriendly(pokemonEntity, target!!) || target!!.distanceToSqr(pokemonEntity) > 1024.0)) {
            target = null
            pokemonEntity.target = null
        }
        
        if (target == null) {
            var potentialTarget = pokemonEntity.target ?: pokemonEntity.brain.getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET).orElse(null)
            
            if (potentialTarget == null || !potentialTarget.isAlive || net.drachi.cde.battleengine.api.BattleEngineApi.isFriendly(pokemonEntity, potentialTarget)) {
                val attacker = pokemonEntity.lastHurtByMob
                if (attacker != null && attacker.isAlive && !net.drachi.cde.battleengine.api.BattleEngineApi.isFriendly(pokemonEntity, attacker)) {
                    potentialTarget = attacker
                } else {
                    potentialTarget = null
                }
            }
            target = potentialTarget
        }
        
        val runtimeState = net.drachi.cde.battleengine.battle.utility.SpawnManager.getEntityHostility(pokemonEntity)
        val savedHostility = pokemonEntity.pokemon.persistentData.getString("cde_hostility")
        val isHostile = runtimeState == net.drachi.cde.battleengine.battle.utility.HostilityState.HOSTILE || 
                        savedHostility == "hostile" || 
                        (runtimeState == null && savedHostility.isEmpty() && 
                         net.drachi.cde.battleengine.battle.utility.SpawnManager.getDimensionHostility(pokemonEntity.level().dimension().location().toString()) == net.drachi.cde.battleengine.battle.utility.HostilityState.HOSTILE)
        
        val ownerId = pokemonEntity.pokemon.getOwnerUUID()
        if (ownerId != null && target == null) {
            val owner = pokemonEntity.level().server?.playerList?.getPlayer(ownerId)
            if (owner != null) {
                val ownerTarget = owner.lastHurtMob ?: owner.lastHurtByMob
                if (ownerTarget != null && ownerTarget.isAlive && !net.drachi.cde.battleengine.api.BattleEngineApi.isFriendly(pokemonEntity, ownerTarget)) {
                    target = ownerTarget
                }
            }
        }
        
        if (target == null) {
            val dimId = pokemonEntity.level().dimension().location().toString()
            val dimState = net.drachi.cde.battleengine.battle.utility.SpawnManager.getDimensionHostility(dimId)
            val isHostileDimension = dimState == net.drachi.cde.battleengine.battle.utility.HostilityState.HOSTILE
            
            if (isHostile) {
                // Wild Hostile Pokemon: scan for players and owned pokemon
                val searchBox = pokemonEntity.boundingBox.inflate(16.0)
                val players = pokemonEntity.level().getEntitiesOfClass(net.minecraft.world.entity.player.Player::class.java, searchBox)
                val ownedPokemon = pokemonEntity.level().getEntitiesOfClass(PokemonEntity::class.java, searchBox) { it.pokemon.getOwnerUUID() != null }
                
                val allValid = (players + ownedPokemon).filter { 
                    it.isAlive && it != pokemonEntity && 
                    !(it is Player && (it.isCreative || it.isSpectator)) &&
                    !net.drachi.cde.battleengine.api.BattleEngineApi.isFriendly(pokemonEntity, it)
                }
                target = allValid.minByOrNull { it.distanceToSqr(pokemonEntity) }
            } else if (ownerId != null && isHostileDimension) {
                // Owned Pokemon in Hostile Dimension: scan for wild pokemon
                val searchBox = pokemonEntity.boundingBox.inflate(16.0)
                val wildPokemon = pokemonEntity.level().getEntitiesOfClass(PokemonEntity::class.java, searchBox) { 
                    it.pokemon.getOwnerUUID() == null && it.isAlive && !net.drachi.cde.battleengine.api.BattleEngineApi.isFriendly(pokemonEntity, it)
                }
                target = wildPokemon.minByOrNull { it.distanceToSqr(pokemonEntity) }
            }
        }
        
        val canUse = target != null && target!!.isAlive
        // We use a rate-limited print instead of tickCount % 20 because canUse is not called every tick
        if (Math.random() < 0.05) {
            println("HostileRealTimeGoal canUse: $canUse. target=${target?.name?.string}, isHostile=$isHostile, tags=${pokemonEntity.tags.joinToString(",")}")
        }
        return canUse
    }

    override fun start() {
        cooldownTicks = 20 // 1 second initial delay
        pickNewMove()
    }

    private fun pickNewMove() {
        val moveSet = pokemonEntity.pokemon.moveSet.getMoves().filterNotNull()
        
        var validRtMoves = moveSet.mapNotNull { cobblemonMove ->
            val moveId = cobblemonMove.template.name.lowercase().replace(" ", "").replace("_", "")
            val fullId = "cobblemon:$moveId"
            val rtMove = MoveRegistry.getMove(fullId)
            if (rtMove != null) {
                Pair(cobblemonMove, rtMove)
            } else null
        }
        
        if (validRtMoves.isEmpty()) {
            val fallbackMove = MoveRegistry.getMove("cobblemon:neutral_attack")
            val dummyTemplate = com.cobblemon.mod.common.api.moves.Moves.getByName("tackle")
            
            if (fallbackMove != null && dummyTemplate != null) {
                // Create a dummy move to pass the template to the AttackExecutor
                val dummyCobblemonMove = com.cobblemon.mod.common.api.moves.Move(dummyTemplate, 0, 0)
                validRtMoves = listOf(Pair(dummyCobblemonMove, fallbackMove))
                println("HostileRealTimeGoal fallback to neutral_attack success")
            } else {
                println("HostileRealTimeGoal fallback FAILED! fallbackMove=$fallbackMove, dummyTemplate=$dummyTemplate")
                return
            }
        }
        
        val currentTarget = target
        val distSq = if (currentTarget != null) pokemonEntity.distanceToSqr(currentTarget) else 0.0
        
        // Filter out moves that would be out of range if possible
        val inRangeMoves = validRtMoves.filter { pair ->
            val firstPhase = pair.second.phases.firstOrNull()
            if (firstPhase != null) {
                val minRange = (pokemonEntity.bbWidth / 2.0f + (currentTarget?.bbWidth ?: 1.0f) / 2.0f + firstPhase.range)
                val rangeSq = minRange * minRange
                distSq <= rangeSq
            } else false
        }
        
        val pickedPair = if (inRangeMoves.isNotEmpty()) {
            inRangeMoves.random()
        } else {
            // Pick a move with the longest range as fallback
            validRtMoves.maxByOrNull { it.second.phases.firstOrNull()?.range ?: 0f } ?: validRtMoves.random()
        }
        
        selectedCobblemonMove = pickedPair.first
        selectedRtMove = pickedPair.second
    }

    override fun tick() {
        // CDDE Freeze Check
        if (pokemonEntity.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN)) {
            val effect = pokemonEntity.getEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN)
            if (effect != null && effect.amplifier >= 200) {
                return
            }
        }

        val currentTarget = target ?: return
        if (!currentTarget.isAlive) {
            target = null
            return
        }
        pokemonEntity.lookControl.setLookAt(currentTarget, 30.0f, 30.0f)

        if (selectedRtMove == null || selectedCobblemonMove == null) {
            pickNewMove()
        }
        
        // Disable Cobblemon brain's walk target so it doesn't fight our navigation
        pokemonEntity.brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
        
        if (cooldownTicks > 0) cooldownTicks--
        
        val rtMove = selectedRtMove
        val firstPhase = rtMove?.phases?.firstOrNull()
        val range = firstPhase?.range ?: 2.0f
        val minRange = (pokemonEntity.bbWidth / 2.0f + currentTarget.bbWidth / 2.0f + range)
        val rangeSq = minRange * minRange
        
        val distSq = pokemonEntity.distanceToSqr(currentTarget)
        
        if (distSq > rangeSq) {
            pokemonEntity.navigation.moveTo(currentTarget, 1.3)
            return
        } else {
            pokemonEntity.navigation.stop()
        }

        if (cooldownTicks <= 0) {
            if (rtMove != null && selectedCobblemonMove != null) {
                // Must have line of sight for ranged attacks
                if (firstPhase != null && (firstPhase.attackType == AttackTypeEnum.PROJECTILE || firstPhase.attackType == AttackTypeEnum.BEAM)) {
                    if (!pokemonEntity.hasLineOfSight(currentTarget)) return
                    
                    // Snap perfectly to target eyes for precise projectiles
                    val eyePos = currentTarget.eyePosition
                    pokemonEntity.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, eyePos)
                    pokemonEntity.xRotO = pokemonEntity.xRot
                    pokemonEntity.yRotO = pokemonEntity.yRot
                    pokemonEntity.yHeadRot = pokemonEntity.yRot
                    pokemonEntity.yHeadRotO = pokemonEntity.yRot
                }

                // Check charging/recharging
                if (DelayedActionManager.isCharging(pokemonEntity.uuid) || DelayedActionManager.isRecharging(pokemonEntity.uuid)) {
                    return
                }

                // Execute it!
                var currentDelayTicks = 0L
                val executionId = java.util.UUID.randomUUID()
                
                for (phase in rtMove.phases) {
                    currentDelayTicks += phase.chargeupTicks
                    DelayedActionManager.queuePhase(executionId, pokemonEntity, pokemonEntity.pokemon, rtMove, selectedCobblemonMove!!.template, phase, currentDelayTicks)
                    val durationTicks = (phase.attackDurationTurns * net.drachi.cde.battleengine.config.BattleEngineConfigManager.config.turnToSecondsRatio * 20).toLong()
                    currentDelayTicks += durationTicks
                }

                cooldownTicks = (rtMove.cooldownTurns * net.drachi.cde.battleengine.config.BattleEngineConfigManager.config.turnToSecondsRatio * 20).toInt()
                if (cooldownTicks <= 0) cooldownTicks = 40
                pickNewMove()
            } else {
                // Fallback basic attack
                pokemonEntity.doHurtTarget(currentTarget)
                cooldownTicks = 20
            }
        }
    }
}
