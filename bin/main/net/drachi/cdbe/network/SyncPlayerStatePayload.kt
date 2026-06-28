package net.drachi.cdbe.network

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*


import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.drachi.cdbe.CobblemonDungeonBattleEngine

data class SyncPlayerStatePayload(
    val activeItem: String,
    val lockedMoveId: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

    companion object {
        val ID = CustomPacketPayload.Type<SyncPlayerStatePayload>(ResourceLocation.parse("${CobblemonDungeonBattleEngine.MOD_ID}:sync_player_state"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncPlayerStatePayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SyncPlayerStatePayload::activeItem,
            ByteBufCodecs.STRING_UTF8, SyncPlayerStatePayload::lockedMoveId,
            ::SyncPlayerStatePayload
        )
    }
}
