package net.drachi.cdde.test

import com.cobblemon.mod.common.client.CobblemonClient

object CobblemonClientTest {
    fun test() {
        val clientStorage = CobblemonClient.storage
        val party = clientStorage.party
        val firstPokemon = party.firstOrNull()
        if (firstPokemon != null) {
            val hasAbility = firstPokemon.ability.name.lowercase() == "levitate"
        }
    }
}
