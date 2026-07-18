package net.drachi.cde.ai

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.level.pathfinder.PathType
import java.util.EnumSet
import kotlin.math.abs

class DungeonFollowPlayerGoal(private val mob: PokemonEntity) : Goal() {
    private var owner: net.minecraft.world.entity.player.Player? = null
    private val navigation: PathNavigation = mob.navigation
    private var timeToRecalcPath = 0
    private var oldWaterCost = 0f

    private var failedPathTimeout: Long = 0
    private var isStuck: Boolean = false

    init {
        this.flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean {
        if (mob.level().gameTime < failedPathTimeout) return false

        val ownerUuid = mob.pokemon.getOwnerUUID() ?: return false
        val potentialOwner = mob.level().getPlayerByUUID(ownerUuid) ?: return false
        
        // Ensure we are in a dungeon
        val dim = mob.level().dimension().location()
        if (dim.namespace != "cde" || dim.path != "dungeon") return false

        // Check distance - if > 2 blocks away, start following
        if (mob.distanceToSqr(potentialOwner) < 4.0) return false

        this.owner = potentialOwner
        return true
    }

    override fun canContinueToUse(): Boolean {
        if (isStuck) return false
        if (navigation.isDone) return false
        if (mob.distanceToSqr(owner!!) <= 2.25) return false
        return true
    }

    override fun start() {
        timeToRecalcPath = 0
        isStuck = false
        oldWaterCost = mob.getPathfindingMalus(PathType.WATER)
        mob.setPathfindingMalus(PathType.WATER, 0.0f)
    }

    override fun stop() {
        owner = null
        navigation.stop()
        mob.setPathfindingMalus(PathType.WATER, oldWaterCost)
    }

    override fun tick() {
        mob.lookControl.setLookAt(owner!!, 10.0f, mob.maxHeadXRot.toFloat())
        
        if (--timeToRecalcPath <= 0) {
            timeToRecalcPath = this.adjustedTickDelay(5)
            
            // Adjust speed to match player's speed or sprint speed
            var speedModifier = 2.0
            if (owner!!.isSprinting) {
                speedModifier = 2.5
            }
            
            // If very far, move faster to catch up
            if (mob.distanceToSqr(owner!!) > 64.0) {
                speedModifier = 3.0
            }

            if (!mob.isLeashed && !mob.isPassenger) {
                val path = navigation.createPath(owner!!, 0)
                val moveToSuccess = path != null && navigation.moveTo(path, speedModifier)
                
                // DEBUG PRINT
                if (mob.level().gameTime % 20L == 0L) {
                    val speedAttr = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                    println("[CDE-AI] tick: path=${path != null}, moveTo=$moveToSuccess, speedAttr=$speedAttr, isDone=${navigation.isDone}")
                }
                
                if (moveToSuccess) {
                    isStuck = false
                } else {
                    // Failed to find a path to the player!
                    isStuck = true
                    // Yield control to lower priority goals (e.g., DungeonWanderGoal) for 3 seconds
                    failedPathTimeout = mob.level().gameTime + 60 
                    navigation.stop()
                }
            }
        }
    }
}
