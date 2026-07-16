package net.drachi.cde.battleengine
import net.minecraft.world.entity.WalkAnimationState

object WalkAnimReflection {
    @JvmStatic
    fun main(args: Array<String>) {
        WalkAnimationState::class.java.declaredMethods.forEach { println(it) }
        WalkAnimationState::class.java.declaredFields.forEach { println(it) }
    }
}
