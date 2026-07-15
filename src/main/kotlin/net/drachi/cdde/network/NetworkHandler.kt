package net.drachi.cdde.network

import io.wispforest.owo.network.OwoNetChannel
import net.drachi.cdde.mechanics.PlayerHazardStateManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

@JvmRecord
data class SyncSelectedSlotPayload(val slotIndex: Int)

@JvmRecord
data class DungeonResultPayload(val dungeonName: String, val partyName: String, val messageKey: String)

object NetworkHandler {
    val CHANNEL: OwoNetChannel = OwoNetChannel.create(ResourceLocation.fromNamespaceAndPath("cdde", "main"))

    fun registerPayloads() {
        CHANNEL.registerServerbound(SyncSelectedSlotPayload::class.java) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@registerServerbound
            player.server.execute {
                PlayerHazardStateManager.setSelectedSlot(player.uuid, payload.slotIndex)
            }
        }

        CHANNEL.registerClientbound(DungeonResultPayload::class.java) { payload, context ->
            net.minecraft.client.Minecraft.getInstance().execute {
                net.minecraft.client.Minecraft.getInstance().setScreen(net.drachi.cdde.client.DungeonResultScreen(payload))
            }
        }

        CHANNEL.registerServerbound(SaveConfigPacket::class.java) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@registerServerbound
            if (!player.hasPermissions(2)) return@registerServerbound
            player.server.execute {
                try {
                    val config = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<net.drachi.cdde.data.DungeonConfig>(payload.configJson)
                    net.drachi.cdde.data.ConfigManager.saveDungeonConfig(config)
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Successfully saved configuration: ${config.id}"))
                } catch (e: Exception) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Failed to save configuration. Invalid JSON."))
                }
            }
        }
    }

    fun sendSyncSelectedSlot(slotIndex: Int) {
        CHANNEL.clientHandle().send(SyncSelectedSlotPayload(slotIndex))
    }

    fun sendDungeonResult(player: ServerPlayer, dungeonName: String, partyName: String, messageKey: String) {
        CHANNEL.serverHandle(player).send(DungeonResultPayload(dungeonName, partyName, messageKey))
    }
}
