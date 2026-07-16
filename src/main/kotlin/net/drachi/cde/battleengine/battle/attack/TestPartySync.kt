package net.drachi.cde.battleengine.battle.attack

import net.minecraft.server.level.ServerPlayer
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore

object TestPartySync {
    fun test(party: PlayerPartyStore) {
        val methods = party.javaClass.methods
        for (m in methods) {
            println(m.name)
        }
    }
}
