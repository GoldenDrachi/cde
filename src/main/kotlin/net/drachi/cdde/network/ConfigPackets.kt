package net.drachi.cdde.network

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data class OpenConfigScreenPacket(val configId: String, val configJson: String) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<OpenConfigScreenPacket>(ResourceLocation.fromNamespaceAndPath("cdde", "open_config_screen"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, OpenConfigScreenPacket> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenConfigScreenPacket::configId,
            ByteBufCodecs.STRING_UTF8, OpenConfigScreenPacket::configJson,
            ::OpenConfigScreenPacket
        )
    }

    override fun type() = ID
}

data class SaveConfigPacket(val configJson: String) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SaveConfigPacket>(ResourceLocation.fromNamespaceAndPath("cdde", "save_config"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SaveConfigPacket> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SaveConfigPacket::configJson,
            ::SaveConfigPacket
        )
    }

    override fun type() = ID
}
