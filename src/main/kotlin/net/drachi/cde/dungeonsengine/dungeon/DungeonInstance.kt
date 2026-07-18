package net.drachi.cde.dungeonsengine.dungeon

import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Tracks the state of an active dungeon run.
 */
class DungeonInstance(
    val id: UUID,
    val partyId: UUID,
    val configId: String
) {
    var currentFloor: Int = 1
    var isCompleted: Boolean = false
    val activeModifiers: MutableList<String> = mutableListOf()
    val collectedLoot: MutableList<net.minecraft.world.item.ItemStack> = mutableListOf()
    
    fun getPlayersInDungeon(server: net.minecraft.server.MinecraftServer): List<ServerPlayer> {
        // Logic to fetch all online players that belong to this partyId
        val partyMembers = net.drachi.cde.dungeonsengine.api.GroupAPI.getPartyMembersById(partyId) ?: return emptyList()
        return partyMembers.mapNotNull { server.playerList.getPlayer(it) }
    }
    
    fun advanceFloor() {
        currentFloor++
    }
    
    fun finishDungeon() {
        isCompleted = true
    }
}
