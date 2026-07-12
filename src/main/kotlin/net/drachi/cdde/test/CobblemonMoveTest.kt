package net.drachi.cdde.test

import com.cobblemon.mod.common.pokemon.Pokemon

object CobblemonMoveTest {
    fun test(pokemon: Pokemon) {
        val knowsSurf = pokemon.moveSet.getMoves().any { it.template.name.lowercase() == "surf" }
        val knowsSurf2 = pokemon.moveSet.any { it.template.name.lowercase() == "surf" }
    }
}
