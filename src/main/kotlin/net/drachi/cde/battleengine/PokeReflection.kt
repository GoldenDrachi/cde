package net.drachi.cde.battleengine
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity

object PokeReflection {
    @JvmStatic
    fun main(args: Array<String>) {
        println("=== PokemonEntity ===")
        PokemonEntity::class.java.declaredMethods.filter { it.name.contains("tick") || it.name.contains("elegate") }.forEach { println(it.name) }
    }
}
