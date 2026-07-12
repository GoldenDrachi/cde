package net.drachi.cdde.test

import com.cobblemon.mod.common.Cobblemon
import net.minecraft.server.level.ServerPlayer

object CobblemonTest {
    fun test(player: ServerPlayer) {
        val party = Cobblemon.storage.getParty(player)
        val firstPokemon = party.firstOrNull()
        if (firstPokemon != null) {
            val types = firstPokemon.types
            for (type in types) {
                println(type.name)
            }
            val isLevitating = firstPokemon.ability.name.lowercase() == "levitate"
        }
    }
}
