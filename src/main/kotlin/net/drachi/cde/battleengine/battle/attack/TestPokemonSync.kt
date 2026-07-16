package net.drachi.cde.battleengine.battle.attack

import net.minecraft.server.level.ServerPlayer
import com.cobblemon.mod.common.pokemon.Pokemon

object TestPokemonSync {
    fun test(pokemon: Pokemon) {
        // Find sync methods
        val methods = pokemon.javaClass.methods
        for (m in methods) {
            if (m.name.contains("sync", ignoreCase = true) || m.name.contains("update", ignoreCase = true) || m.name.contains("save", ignoreCase = true)) {
                println(m.name)
            }
        }
    }
}
