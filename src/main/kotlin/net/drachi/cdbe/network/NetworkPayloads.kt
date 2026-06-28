package net.drachi.cdbe.network

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*


import net.drachi.cdbe.CobblemonDungeonBattleEngine
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data class CastMovePayload(val partySlotIndex: Int, val moveSlotIndex: Int) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<CastMovePayload>(ResourceLocation.parse(CobblemonDungeonBattleEngine.MOD_ID + ":cast_move"))
        
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, CastMovePayload> = StreamCodec.composite(
            ByteBufCodecs.INT, CastMovePayload::partySlotIndex,
            ByteBufCodecs.INT, CastMovePayload::moveSlotIndex,
            ::CastMovePayload
        )
    }
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID
}

data class ToggleTargetPayload(val isTargeting: Boolean) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<ToggleTargetPayload>(ResourceLocation.parse(CobblemonDungeonBattleEngine.MOD_ID + ":toggle_target"))
        
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ToggleTargetPayload> = StreamCodec.composite(
            ByteBufCodecs.BOOL, ToggleTargetPayload::isTargeting,
            ::ToggleTargetPayload
        )
    }
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID
}

data class CooldownSyncPayload(val moveId: String, val expirationMs: Long) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<CooldownSyncPayload>(ResourceLocation.parse(CobblemonDungeonBattleEngine.MOD_ID + ":cooldown_sync"))
        
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, CooldownSyncPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CooldownSyncPayload::moveId,
            ByteBufCodecs.VAR_LONG, CooldownSyncPayload::expirationMs,
            ::CooldownSyncPayload
        )
    }
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID
}

data class SyncSelectedSlotPayload(val slotIndex: Int) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SyncSelectedSlotPayload>(ResourceLocation.parse(CobblemonDungeonBattleEngine.MOD_ID + ":sync_selected_slot"))
        
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncSelectedSlotPayload> = StreamCodec.composite(
            ByteBufCodecs.INT, SyncSelectedSlotPayload::slotIndex,
            ::SyncSelectedSlotPayload
        )
    }
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID
}

data class ForceSelectedSlotPayload(val slotIndex: Int) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<ForceSelectedSlotPayload>(ResourceLocation.parse(CobblemonDungeonBattleEngine.MOD_ID + ":force_selected_slot"))
        
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ForceSelectedSlotPayload> = StreamCodec.composite(
            ByteBufCodecs.INT, ForceSelectedSlotPayload::slotIndex,
            ::ForceSelectedSlotPayload
        )
    }
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID
}

data class SyncLockedTargetPayload(val entityId: Int) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SyncLockedTargetPayload>(ResourceLocation.parse(CobblemonDungeonBattleEngine.MOD_ID + ":sync_locked_target"))
        
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncLockedTargetPayload> = StreamCodec.composite(
            ByteBufCodecs.INT, SyncLockedTargetPayload::entityId,
            ::SyncLockedTargetPayload
        )
    }
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID
}

data class SyncEntityStatsPayload(val entityId: Int, val stats: Map<String, Int>) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SyncEntityStatsPayload>(ResourceLocation.parse(CobblemonDungeonBattleEngine.MOD_ID + ":sync_entity_stats"))
        
        // Codec for Map<String, Int>
        private val STATS_CODEC: StreamCodec<ByteBuf, Map<String, Int>> = net.minecraft.network.codec.ByteBufCodecs.map(
            { java.util.HashMap(it) }, 
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, 
            net.minecraft.network.codec.ByteBufCodecs.INT
        )

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncEntityStatsPayload> = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncEntityStatsPayload::entityId,
            STATS_CODEC.cast(), SyncEntityStatsPayload::stats,
            ::SyncEntityStatsPayload
        )
    }
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID
}

data class SyncWeatherPayload(val weatherCondition: String) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SyncWeatherPayload>(ResourceLocation.parse(CobblemonDungeonBattleEngine.MOD_ID + ":sync_weather"))
        
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncWeatherPayload> = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, SyncWeatherPayload::weatherCondition,
            ::SyncWeatherPayload
        )
    }
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID
}

object NetworkHandler {
    fun registerPayloads() {
        PayloadTypeRegistry.playC2S().register(CastMovePayload.ID, CastMovePayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ToggleTargetPayload.ID, ToggleTargetPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(SyncSelectedSlotPayload.ID, SyncSelectedSlotPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ForceSelectedSlotPayload.ID, ForceSelectedSlotPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(CooldownSyncPayload.ID, CooldownSyncPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SyncLockedTargetPayload.ID, SyncLockedTargetPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SyncEntityStatsPayload.ID, SyncEntityStatsPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SyncWeatherPayload.ID, SyncWeatherPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SyncPlayerStatePayload.ID, SyncPlayerStatePayload.CODEC)
        
        ServerPlayNetworking.registerGlobalReceiver(CastMovePayload.ID) { payload, context ->
            val player = context.player()
            val server = context.server()
            server.execute {
                net.drachi.cdbe.battle.attack.AttackExecutor.handleCastRequest(player, payload.partySlotIndex, payload.moveSlotIndex)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(SyncSelectedSlotPayload.ID) { payload, context ->
            val player = context.player()
            val server = context.server()
            server.execute {
                net.drachi.cdbe.battle.attack.PlayerCombatManager.setActivePokemon(player, payload.slotIndex)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ToggleTargetPayload.ID) { payload, context ->
            val player = context.player()
            val server = context.server()
            server.execute {
                if (payload.isTargeting) {
                    net.drachi.cdbe.battle.attack.PlayerCombatManager.toggleTargetLock(player)
                }
            }
        }
    }
    
    fun sendCastMove(partySlotIndex: Int, moveSlotIndex: Int) {
        ClientPlayNetworking.send(CastMovePayload(partySlotIndex, moveSlotIndex))
    }

    fun sendSyncSelectedSlot(slotIndex: Int) {
        ClientPlayNetworking.send(SyncSelectedSlotPayload(slotIndex))
    }

    fun sendForceSelectedSlot(player: net.minecraft.server.level.ServerPlayer, slotIndex: Int) {
        ServerPlayNetworking.send(player, ForceSelectedSlotPayload(slotIndex))
    }

    fun sendToggleTarget() {
        ClientPlayNetworking.send(ToggleTargetPayload(true))
    }

    fun sendCooldownSync(player: net.minecraft.server.level.ServerPlayer, moveId: String, expirationMs: Long) {
        ServerPlayNetworking.send(player, CooldownSyncPayload(moveId, expirationMs))
    }

    fun sendSyncLockedTarget(player: net.minecraft.server.level.ServerPlayer, entityId: Int) {
        ServerPlayNetworking.send(player, SyncLockedTargetPayload(entityId))
    }

    fun sendSyncEntityStats(player: net.minecraft.server.level.ServerPlayer, entityId: Int, stats: Map<String, Int>) {
        ServerPlayNetworking.send(player, SyncEntityStatsPayload(entityId, stats))
    }

    fun sendSyncWeather(player: net.minecraft.server.level.ServerPlayer, weatherCondition: String) {
        ServerPlayNetworking.send(player, SyncWeatherPayload(weatherCondition))
    }
    
    fun sendPlayerStateSync(player: net.minecraft.server.level.ServerPlayer, activeItem: String, lockedMoveId: String) {
        ServerPlayNetworking.send(player, SyncPlayerStatePayload(activeItem, lockedMoveId))
    }
}
