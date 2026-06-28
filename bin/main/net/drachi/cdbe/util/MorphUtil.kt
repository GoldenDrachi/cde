package net.drachi.cdbe.util

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*


import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader

object MorphUtil {
    @JvmStatic
    fun getActivePokemon(player: Player): Pokemon? {
        if (player is ServerPlayer) {
            return PlayerCombatManager.getActivePokemon(player)
        } else {
            return getClientActivePokemon()
        }
    }

    private fun getClientActivePokemon(): Pokemon? {
        if (FabricLoader.getInstance().environmentType == EnvType.CLIENT) {
            val selectedSlot = com.cobblemon.mod.common.client.CobblemonClient.storage.selectedSlot
            if (selectedSlot >= 0) {
                return com.cobblemon.mod.common.client.CobblemonClient.storage.party?.get(selectedSlot)
            }
        }
        return null
    }
}
