package net.drachi.cde.dungeonsengine.mechanics

import com.cobblemon.mod.common.client.CobblemonClient

object HazardMechanicClient {
    fun canTraverse(hazardType: HazardType): Boolean {
        val selectedSlot = CobblemonClient.storage.selectedSlot
        if (selectedSlot < 0) return false
        val party = CobblemonClient.storage.party
        val pokemon = party.get(selectedSlot) ?: return false
        return HazardMechanic.canTraverse(pokemon, hazardType)
    }
}
