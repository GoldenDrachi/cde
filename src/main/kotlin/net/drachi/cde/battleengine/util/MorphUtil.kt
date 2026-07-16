package net.drachi.cde.battleengine.util

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader

object MorphUtil {
    @JvmStatic
    fun getSelectedSlot(): Int {
        return com.cobblemon.mod.common.client.CobblemonClient.storage.selectedSlot
    }

        @JvmStatic
    fun getMorphDimensions(player: Player): net.minecraft.world.entity.EntityDimensions? {
        val activeMon = getActivePokemon(player) ?: return null
        if (activeMon.entity != null) return null
        
        val scale = activeMon.form.baseScale * activeMon.scaleModifier
        var pokemonDimensions = activeMon.form.hitbox.scale(scale)
        
        val dimLoc = player.level().dimension().location()
        if ((dimLoc.namespace == "cdde" || dimLoc.namespace == "cde") && dimLoc.path == "dungeon") {
            pokemonDimensions = net.drachi.cde.battleengine.battle.utility.EntityUtil.constrainDimensions(pokemonDimensions, 3.0f, 4.0f)
        }
        
        return pokemonDimensions
    }

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
