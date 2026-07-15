package net.drachi.cdde.blocks

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraft.world.level.BlockGetter
import net.drachi.cdde.data.ConfigManager
import net.drachi.cdde.data.DungeonManager
import net.drachi.cdde.generation.DungeonGenerator

class DungeonPortalBlock(properties: Properties) : Block(properties), EntityBlock {
    init {
        // No custom default state needed
    }


    // The portal block should not have collision so players can walk into it
    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return net.minecraft.world.phys.shapes.Shapes.empty()
    }

    @Deprecated("Deprecated in Java")
    override fun skipRendering(state: BlockState, adjacentBlockState: BlockState, direction: net.minecraft.core.Direction): Boolean {
        return adjacentBlockState.`is`(this) || super.skipRendering(state, adjacentBlockState, direction)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return DungeonPortalBlockEntity(pos, state)
    }

    override fun entityInside(state: BlockState, level: Level, pos: BlockPos, entity: Entity) {
        if (!level.isClientSide && entity is ServerPlayer) {
            // Prevent teleporting every tick
            if (entity.portalCooldown > 0) {
                entity.portalCooldown = 10
                return
            }

            val be = level.getBlockEntity(pos) as? DungeonPortalBlockEntity ?: return
            val configId = be.configId
            
            if (configId.isBlank()) {
                // Generic portal -> open UI
                val unlocked = net.drachi.cdde.database.DatabaseManager.getUnlockedDungeons(entity.uuid)
                net.drachi.cdde.network.NetworkHandler.CHANNEL.serverHandle(entity).send(
                    net.drachi.cdde.network.OpenDungeonJoinUIPayload(unlocked)
                )
                // Short cooldown so it doesn't spam the UI open packet
                entity.portalCooldown = 20
                return
            } else {
                // Specific portal -> instant join & bypass unlock check
                net.drachi.cdde.data.DungeonManager.joinDungeon(entity, configId, bypassUnlockCheck = true)
            }
        }
    }
}
