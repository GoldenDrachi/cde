package net.drachi.cde.battleengine.battle.attack

import net.minecraft.server.level.ServerPlayer
import net.drachi.cde.battleengine.battle.attack.PlayerCombatManager

object TestRefreshDimensions {
    fun test(player: ServerPlayer) {
        player.refreshDimensions()
    }
}
