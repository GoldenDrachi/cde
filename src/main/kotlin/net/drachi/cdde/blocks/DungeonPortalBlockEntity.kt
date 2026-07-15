package net.drachi.cdde.blocks

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.drachi.cdde.registry.ModBlockEntities

class DungeonPortalBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlockEntities.DUNGEON_PORTAL, pos, state) {
    var configId: String = ""
    var colorHex: Int = 0x800080

    override fun saveAdditional(tag: CompoundTag, registries: net.minecraft.core.HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putString("ConfigId", configId)
        tag.putInt("ColorHex", colorHex)
    }

    override fun loadAdditional(tag: CompoundTag, registries: net.minecraft.core.HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        var changed = false
        if (tag.contains("ConfigId")) {
            val newId = tag.getString("ConfigId")
            if (configId != newId) {
                configId = newId
                changed = true
            }
        }
        if (tag.contains("ColorHex")) {
            val newColor = tag.getInt("ColorHex")
            if (colorHex != newColor) {
                colorHex = newColor
                changed = true
            }
        }
        if (changed && level?.isClientSide == true) {
            level?.sendBlockUpdated(blockPos, blockState, blockState, 8)
        }
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registries: net.minecraft.core.HolderLookup.Provider): CompoundTag {
        val tag = super.getUpdateTag(registries)
        tag.putString("ConfigId", configId)
        tag.putInt("ColorHex", colorHex)
        return tag
    }
}
