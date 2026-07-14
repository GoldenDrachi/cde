package net.drachi.cdde.network

import net.drachi.cdde.CobblemonDungeonDungeonsEngine
import net.drachi.cdde.mechanics.PlayerHazardStateManager
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data class SyncSelectedSlotPayload(val slotIndex: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

    companion object {
        val ID = CustomPacketPayload.Type<SyncSelectedSlotPayload>(ResourceLocation.fromNamespaceAndPath("cdde", "sync_selected_slot"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncSelectedSlotPayload> = StreamCodec.of(
            { buf, payload -> buf.writeInt(payload.slotIndex) },
            { buf -> SyncSelectedSlotPayload(buf.readInt()) }
        )
    }
}

object NetworkHandler {
    fun registerPayloads() {
        PayloadTypeRegistry.playC2S().register(SyncSelectedSlotPayload.ID, SyncSelectedSlotPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(OpenConfigScreenPacket.ID, OpenConfigScreenPacket.CODEC)
        PayloadTypeRegistry.playC2S().register(SaveConfigPacket.ID, SaveConfigPacket.CODEC)
        
        ServerPlayNetworking.registerGlobalReceiver(SyncSelectedSlotPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                PlayerHazardStateManager.setSelectedSlot(player.uuid, payload.slotIndex)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(SaveConfigPacket.ID) { payload, context ->
            val player = context.player()
            if (!player.hasPermissions(2)) return@registerGlobalReceiver
            context.server().execute {
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
        ClientPlayNetworking.send(SyncSelectedSlotPayload(slotIndex))
    }
}
