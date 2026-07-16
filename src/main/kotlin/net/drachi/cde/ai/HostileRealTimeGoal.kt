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

        if (target != null && target!!.isAlive) return true
        
        target = pokemonEntity.target ?: pokemonEntity.brain.getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET).orElse(null)
        
        if (target == null || !target!!.isAlive) {
            // Check lastHurtByMob as a fallback (for neutral/pet Pokemon that got attacked)
            val attacker = pokemonEntity.lastHurtByMob
            if (attacker != null && attacker.isAlive) {
                target = attacker
            }
        }
        
        val runtimeState = net.drachi.cde.battleengine.battle.utility.SpawnManager.getEntityHostility(pokemonEntity)
        val savedHostility = pokemonEntity.pokemon.persistentData.getString("cdbe_hostility")
        val isHostile = runtimeState == net.drachi.cde.battleengine.battle.utility.HostilityState.HOSTILE || 
                        savedHostility == "hostile" || 
                        (runtimeState == null && savedHostility.isEmpty() && 
                         net.drachi.cde.battleengine.battle.utility.SpawnManager.getDimensionHostility(pokemonEntity.level().dimension().location().toString()) == net.drachi.cde.battleengine.battle.utility.HostilityState.HOSTILE)
        
        val ownerId = pokemonEntity.pokemon.getOwnerUUID()
        if (ownerId != null && (target == null || !target!!.isAlive)) {
            val owner = pokemonEntity.level().server?.playerList?.getPlayer(ownerId)
            if (owner != null) {
                val ownerTarget = owner.lastHurtMob ?: owner.lastHurtByMob
                if (ownerTarget != null && ownerTarget.isAlive) {
                    target = ownerTarget
                }
            }
        }
        
        if ((target == null || !target!!.isAlive) && isHostile) {
            // Proactive scan for hostile Pokemon — look for players and owned pokemon
            val searchBox = pokemonEntity.boundingBox.inflate(16.0)
            val players = pokemonEntity.level().getEntitiesOfClass(net.minecraft.world.entity.player.Player::class.java, searchBox)
            val ownedPokemon = pokemonEntity.level().getEntitiesOfClass(PokemonEntity::class.java, searchBox) { it.pokemon.getOwnerUUID() != null }
            
            val allValid = (players + ownedPokemon).filter { 
                it.isAlive && it != pokemonEntity && 
                !(it is Player && (it.isCreative || it.isSpectator))
            }
            target = allValid.minByOrNull { it.distanceToSqr(pokemonEntity) }
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
            val dummyTemplate = moveSet.firstOrNull()?.template ?: com.cobblemon.mod.common.api.moves.Moves.getByName("cobblemon:tackle")
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

        if (pokemonEntity.tickCount % 20 == 0) println("HostileRealTimeGoal ticking! target: ${currentTarget.name.string}, cooldown: $cooldownTicks, selected move: ${selectedRtMove?.cobblemonMoveId}")

        if (selectedRtMove == null || selectedCobblemonMove == null) {
            pickNewMove()
            if (selectedRtMove == null) {
                // Fallback to basic follow if no moves
                pokemonEntity.navigation.moveTo(currentTarget, 1.0)
                return
            }
        }
        
        val rtMove = selectedRtMove!!
        val firstPhase = rtMove.phases.firstOrNull() ?: return
        val range = firstPhase.range
        val minRange = (pokemonEntity.bbWidth / 2.0f + currentTarget.bbWidth / 2.0f + range)
        val rangeSq = minRange * minRange
        
        val distSq = pokemonEntity.distanceToSqr(currentTarget)
        
        // Disable Cobblemon brain's walk target so it doesn't fight our navigation
        pokemonEntity.brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
        
        // Must be in range to fire, but if on cooldown, don't move into melee range if it's a ranged attack
        if (cooldownTicks > 0) {
            cooldownTicks--
            // If we are already in range for the *current* queued move, stop so we don't pathfind into melee
            if (distSq <= rangeSq) {
                pokemonEntity.navigation.stop()
            } else {
                pokemonEntity.navigation.moveTo(currentTarget, 1.3)
            }
            return
        }

        // Move into range
        if (distSq > rangeSq) {
            pokemonEntity.navigation.moveTo(currentTarget, 1.3)
            if (pokemonEntity.tickCount % 20 == 0) println("HostileRealTimeGoal out of range: distSq=$distSq, rangeSq=$rangeSq")
            return
        } else {
            pokemonEntity.navigation.stop()
        }

        // Must have line of sight for ranged attacks
        if (firstPhase.attackType == AttackTypeEnum.PROJECTILE || firstPhase.attackType == AttackTypeEnum.BEAM) {
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
            if (pokemonEntity.tickCount % 20 == 0) println("HostileRealTimeGoal charging/recharging")
            return
        }

        // Execute it!
        println("HostileRealTimeGoal FIRING MOVE ${rtMove.cobblemonMoveId} AT ${currentTarget.name.string}")
        var currentDelayTicks = 0L
        val executionId = java.util.UUID.randomUUID()
        
        for (phase in rtMove.phases) {
            currentDelayTicks += phase.chargeupTicks
            DelayedActionManager.queuePhase(executionId, pokemonEntity, pokemonEntity.pokemon, rtMove, selectedCobblemonMove!!.template, phase, currentDelayTicks)
            val durationTicks = (phase.attackDurationTurns * net.drachi.cde.battleengine.config.BattleEngineConfigManager.config.turnToSecondsRatio * 20).toLong()
            currentDelayTicks += durationTicks
        }

        // Set cooldown and pick next move
        cooldownTicks = (rtMove.cooldownTurns * net.drachi.cde.battleengine.config.BattleEngineConfigManager.config.turnToSecondsRatio * 20).toInt()
        if (cooldownTicks <= 0) cooldownTicks = 40
        pickNewMove()
    }
}
