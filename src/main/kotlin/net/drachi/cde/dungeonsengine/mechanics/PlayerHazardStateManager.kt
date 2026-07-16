package net.drachi.cde.dungeonsengine.mechanics

import java.util.UUID

object PlayerHazardStateManager {
    // Maps Player UUID to their currently selected party slot index
    private val selectedSlots = mutableMapOf<UUID, Int>()

    fun setSelectedSlot(playerUuid: UUID, slot: Int) {
        selectedSlots[playerUuid] = slot
    }

    fun getSelectedSlot(playerUuid: UUID): Int {
        return selectedSlots[playerUuid] ?: -1
    }

    fun clear(playerUuid: UUID) {
        selectedSlots.remove(playerUuid)
    }
}
