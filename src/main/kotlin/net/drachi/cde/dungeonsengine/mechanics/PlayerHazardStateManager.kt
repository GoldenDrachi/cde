package net.drachi.cde.dungeonsengine.mechanics

import java.util.UUID

object PlayerHazardStateManager {
    // Maps Player UUID to their currently selected party slot index
    private val selectedSlots = mutableMapOf<UUID, Int>()
    
    // Maps Player UUID to their swapping state
    private val swappingState = mutableMapOf<UUID, Boolean>()

    fun setSwapping(playerUuid: UUID, isSwapping: Boolean) {
        if (isSwapping) {
            swappingState[playerUuid] = true
        } else {
            swappingState.remove(playerUuid)
        }
    }

    fun isSwapping(playerUuid: UUID): Boolean {
        return swappingState[playerUuid] ?: false
    }

    fun setSelectedSlot(playerUuid: UUID, slot: Int) {
        selectedSlots[playerUuid] = slot
    }

    fun getSelectedSlot(playerUuid: UUID): Int {
        return selectedSlots[playerUuid] ?: -1
    }

    fun clear(playerUuid: UUID) {
        selectedSlots.remove(playerUuid)
        swappingState.remove(playerUuid)
    }
}
