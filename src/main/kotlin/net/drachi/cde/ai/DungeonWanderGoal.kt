package net.drachi.cde.ai

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.util.DefaultRandomPos
import net.minecraft.world.phys.Vec3
import java.util.*

class DungeonWanderGoal(private val pokemon: PokemonEntity, private val speedModifier: Double = 1.0) : Goal() {

    private var targetPos: Vec3? = null
    private var waitTicks = 0

    init {
        flags = EnumSet.of(Flag.MOVE)
    }

    override fun canUse(): Boolean {
        if (pokemon.isVehicle) return false
        
        if (waitTicks > 0) {
            waitTicks--
            return false
        }
        
        // Randomly decide to wander - much more frequent in dungeons (1 in 10 ticks vs 1 in 60)
        if (pokemon.random.nextInt(10) != 0) {
            return false
        }

        // Use DefaultRandomPos to find a far position (horizontal range 15, vertical 3)
        // This encourages wider wandering than the standard 10x7
        val pos = DefaultRandomPos.getPos(pokemon, 15, 3) ?: return false
        targetPos = pos
        return true
    }

    override fun canContinueToUse(): Boolean {
        return !pokemon.navigation.isDone && pokemon.isAlive
    }

    private fun calculateSpeed(): Double {
        val speedStat = pokemon.pokemon.speed.toDouble()
        // Base 50 speed -> 1.0 modifier
        val ratio = 0.5 + (speedStat / 100.0)
        return speedModifier * ratio.coerceIn(0.6, 2.0)
    }

    override fun start() {
        if (targetPos != null) {
            pokemon.navigation.moveTo(targetPos!!.x, targetPos!!.y, targetPos!!.z, calculateSpeed())
        }
    }

    override fun stop() {
        pokemon.navigation.stop()
        // Wait a few seconds before wandering again
        waitTicks = pokemon.random.nextInt(20) + 10
    }
}
