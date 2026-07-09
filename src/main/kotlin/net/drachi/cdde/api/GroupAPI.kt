package net.drachi.cdde.api

import java.util.UUID

/**
 * Mock interface for the external group management mod.
 * CDDE will consume this to determine who is in a party.
 */
object GroupAPI {
    
    // In a real implementation, this would call the external mod's API
    private val mockParties = mutableMapOf<UUID, List<UUID>>()

    fun createParty(leader: UUID, members: List<UUID>) {
        val partyId = UUID.randomUUID()
        mockParties[partyId] = members.plus(leader)
    }

    fun getPartyMembers(playerId: UUID): List<UUID>? {
        return mockParties.values.firstOrNull { it.contains(playerId) }
    }

    fun getPartyId(playerId: UUID): UUID? {
        return mockParties.entries.firstOrNull { it.value.contains(playerId) }?.key
    }

    /** Direct lookup by party UUID — used when we already have the party ID (e.g., DungeonInstance). */
    fun getPartyMembersById(partyId: UUID): List<UUID>? {
        return mockParties[partyId]
    }
    

}
