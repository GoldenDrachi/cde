package net.drachi.cdbe.battle.attack

import net.minecraft.server.level.ServerPlayer
import net.drachi.cdbe.battle.attack.PlayerCombatManager

object TestRefreshDimensions {
    fun test(player: ServerPlayer) {
        player.refreshDimensions()
    }
}
