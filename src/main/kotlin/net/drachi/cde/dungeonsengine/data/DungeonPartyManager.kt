package net.drachi.cde.dungeonsengine.data

import com.cobblemon.mod.common.Cobblemon
import net.drachi.cde.CDE
import net.drachi.cde.dungeonsengine.api.GroupAPI
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object DungeonPartyManager {

    private val pendingJoins = mutableMapOf<UUID, PendingDungeonJoin>()

    data class PendingDungeonJoin(
        val leaderId: UUID,
        val configId: String,
        val bypassUnlockCheck: Boolean,
        val requiredConfirmations: MutableSet<UUID>
    )

    fun getActiveSlotsAllowed(partySize: Int): Int {
        return when (partySize) {
            1 -> 4
            2 -> 2
            else -> 1
        }
    }

    fun initiateDungeonJoin(leader: ServerPlayer, configId: String, bypassUnlockCheck: Boolean = false) {
        val groupMembers = GroupAPI.getPartyMembers(leader.uuid) ?: listOf(leader.uuid)
        val players = groupMembers.mapNotNull { leader.server.playerList.getPlayer(it) }
        
        val allowedSlots = getActiveSlotsAllowed(players.size)
        val playersNeedingConfirmation = mutableSetOf<UUID>()

        for (player in players) {
            val party = Cobblemon.storage.getParty(player)
            // Check if player has more Pokemon than allowed
            var activeCount = 0
            for (i in 0 until party.size()) {
                if (party.get(i) != null) activeCount++
            }

            if (activeCount > allowedSlots) {
                playersNeedingConfirmation.add(player.uuid)
            }
        }

        if (playersNeedingConfirmation.isEmpty()) {
            // Everyone is fine, proceed directly
            DungeonManager.executeJoin(leader, configId, bypassUnlockCheck)
        } else {
            // Wait for confirmations
            val pending = PendingDungeonJoin(leader.uuid, configId, bypassUnlockCheck, playersNeedingConfirmation)
            pendingJoins[leader.uuid] = pending

            val confirmComponent = Component.literal("[Click Here to Confirm]")
                .withStyle(
                    Style.EMPTY
                        .withColor(net.minecraft.ChatFormatting.GREEN)
                        .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cde dungeon confirm_join ${leader.uuid}"))
                        .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Confirm PC transfer")))
                )

            for (player in players) {
                if (playersNeedingConfirmation.contains(player.uuid)) {
                    player.sendSystemMessage(Component.literal("§eYour group size limits you to §c$allowedSlots §ePokemon."))
                    player.sendSystemMessage(Component.literal("§eAny excess Pokemon will be sent to your PC."))
                    player.sendSystemMessage(confirmComponent)
                } else {
                    player.sendSystemMessage(Component.literal("§7Waiting for other players to confirm party limits..."))
                }
            }
        }
    }

    fun confirmJoin(player: ServerPlayer, leaderId: UUID) {
        val pending = pendingJoins[leaderId] ?: return
        
        if (pending.requiredConfirmations.contains(player.uuid)) {
            pending.requiredConfirmations.remove(player.uuid)
            player.sendSystemMessage(Component.literal("§aConfirmed!"))
            
            val groupMembers = GroupAPI.getPartyMembers(leaderId) ?: listOf(leaderId)
            val players = groupMembers.mapNotNull { player.server.playerList.getPlayer(it) }

            if (pending.requiredConfirmations.isEmpty()) {
                pendingJoins.remove(leaderId)
                
                players.forEach { p ->
                    p.sendSystemMessage(Component.literal("§aAll players confirmed. Joining dungeon..."))
                }
                
                // Do the actual PC transfer
                val allowedSlots = getActiveSlotsAllowed(players.size)
                for (p in players) {
                    transferExcessToPC(p, allowedSlots)
                }

                // Proceed with join
                val leader = player.server.playerList.getPlayer(leaderId)
                if (leader != null) {
                    DungeonManager.executeJoin(leader, pending.configId, pending.bypassUnlockCheck)
                }
            } else {
                players.forEach { p ->
                    p.sendSystemMessage(Component.literal("§7Waiting for ${pending.requiredConfirmations.size} more player(s) to confirm..."))
                }
            }
        }
    }

    private fun transferExcessToPC(player: ServerPlayer, allowedSlots: Int) {
        val party = Cobblemon.storage.getParty(player)
        val pc = Cobblemon.storage.getPC(player)
        
        // We iterate backwards to safely remove from party and send to PC
        for (i in party.size() - 1 downTo allowedSlots) {
            val pokemon = party.get(i)
            if (pokemon != null) {
                party.remove(pokemon)
                pc.add(pokemon)
                player.sendSystemMessage(Component.literal("§7Sent ${pokemon.getDisplayName().string} to PC."))
            }
        }
    }

    fun enforceDungeonPartyState(
        player: ServerPlayer,
        level: net.minecraft.server.level.ServerLevel,
        startPos: net.minecraft.core.BlockPos,
        spawnOffsets: Array<Pair<Int, Int>>,
        spawnIndexRef: IntArray
    ) {
        val groupMembers = GroupAPI.getPartyMembers(player.uuid) ?: listOf(player.uuid)
        val allowedSlots = getActiveSlotsAllowed(groupMembers.size)
        val party = Cobblemon.storage.getParty(player)
        val selectedSlot = net.drachi.cde.dungeonsengine.mechanics.PlayerHazardStateManager.getSelectedSlot(player.uuid)

        for (i in 0 until allowedSlots) {
            val pokemon = party.get(i) ?: continue

            if (i == selectedSlot) {
                // The selected slot should NOT be out!
                if (pokemon.entity != null) {
                    pokemon.entity!!.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED)
                }
            } else {
                // Not the selected slot - MUST be out!
                var pEntity = pokemon.entity
                
                // Discard the existing entity so we can re-send it on the new floor safely
                if (pEntity != null) {
                    pEntity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED)
                }

                // Send out the Pokemon fresh
                pokemon.sendOut(level, player.position(), null)
                pEntity = pokemon.entity
                
                if (pEntity != null) {
                    val pokeOffset = spawnOffsets[spawnIndexRef[0] % spawnOffsets.size]
                    spawnIndexRef[0]++
                    
                    val pokePosRaw = startPos.offset(pokeOffset.first, 0, pokeOffset.second)
                    val pokePos = DungeonManager.findSafeSpawn(level, pokePosRaw)
                    
                    pEntity.teleportTo(pokePos.x.toDouble() + 0.5, pokePos.y.toDouble(), pokePos.z.toDouble() + 0.5)
                }
            }
        }
    }

    fun performSwap(player: ServerPlayer, oldSlot: Int, newSlot: Int) {
        val party = Cobblemon.storage.getParty(player)
        val oldPokemon = party.get(oldSlot)
        val newPokemon = party.get(newSlot)

        if (oldPokemon == null || newPokemon == null) return

        val newEntity = newPokemon.entity ?: return

        // Teleport the player to the new Pokemon's location
        val pPos = newEntity.position()
        
        net.drachi.cde.dungeonsengine.mechanics.PlayerHazardStateManager.setSwapping(player.uuid, true)

        // Retrieve the new Pokemon (since it is now the active "selected" slot)
        newEntity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED)

        // Send out the old Pokemon at the player's old location
        oldPokemon.sendOut(player.serverLevel(), player.position(), null)

        // Update selected slot state
        net.drachi.cde.dungeonsengine.mechanics.PlayerHazardStateManager.setSelectedSlot(player.uuid, newSlot)

        // Teleport player after
        player.teleportTo(pPos.x, pPos.y, pPos.z)

        net.drachi.cde.dungeonsengine.mechanics.PlayerHazardStateManager.setSwapping(player.uuid, false)
    }
}
