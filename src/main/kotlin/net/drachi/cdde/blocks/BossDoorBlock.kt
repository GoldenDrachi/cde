package net.drachi.cdde.blocks

import net.drachi.cdde.registry.ModItems
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import java.util.LinkedList
import java.util.Queue

class BossDoorBlock(properties: Properties) : Block(properties) {

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    ): ItemInteractionResult {
        if (!level.isClientSide) {
            if (player.isCreative && stack.isEmpty) {
                breakConnectedDoors(level, pos)
                return ItemInteractionResult.SUCCESS
            }
            
            val dungeon = net.drachi.cdde.data.DungeonManager.getActiveDungeon(player)
            if (dungeon != null) {
                // If this is the boss floor, check if enemies are alive
                if (dungeon.config.endFloorType == net.drachi.cdde.data.EndFloorType.BOSS && dungeon.currentFloor == dungeon.config.amountOfFloors) {
                    val floorOriginZ = dungeon.originZ
                    val floorOriginX = dungeon.originX + ((dungeon.currentFloor - 1) * 1000)
                    
                    val dim = net.drachi.cdde.generation.DungeonGrid.gridSizeForRooms(1) // Boss floor has 1 maxRooms
                    val maxBlocks = dim * net.drachi.cdde.generation.DungeonGrid.CELL_SIZE
                    
                    val bounds = net.minecraft.world.phys.AABB(
                        floorOriginX.toDouble() - 50.0, -64.0, floorOriginZ.toDouble() - 50.0,
                        floorOriginX.toDouble() + maxBlocks.toDouble() + 50.0, 319.0, floorOriginZ.toDouble() + maxBlocks.toDouble() + 50.0
                    )
                    
                    val remainingEnemies = level.getEntitiesOfClass(net.minecraft.world.entity.Mob::class.java, bounds) {
                        it.tags.contains("cdde_spawned") && it.isAlive
                    }
                    
                    if (remainingEnemies.isEmpty()) {
                        breakConnectedDoors(level, pos)
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.cdde.door_opens").withStyle(net.minecraft.ChatFormatting.GREEN), true)
                        return ItemInteractionResult.SUCCESS
                    } else {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("The door is sealed. Defeat ${remainingEnemies.size} more enemies!").withStyle(net.minecraft.ChatFormatting.RED), true)
                        return ItemInteractionResult.SUCCESS
                    }
                } else if (stack.`is`(ModItems.TREASURE_KEY)) {
                    // For Treasure room, you might still need a key, or it's just open?
                    // "door block that only opens when the boss and minions are defeated" (for Boss).
                    // If it's used in Treasure room, maybe it opens with the key?
                    if (!player.isCreative) {
                        stack.shrink(1)
                    }
                    breakConnectedDoors(level, pos)
                    return ItemInteractionResult.SUCCESS
                } else {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.cdde.door_locked").withStyle(net.minecraft.ChatFormatting.RED), true)
                    return ItemInteractionResult.SUCCESS
                }
            } else if (stack.`is`(ModItems.TREASURE_KEY)) {
                if (!player.isCreative) stack.shrink(1)
                breakConnectedDoors(level, pos)
                return ItemInteractionResult.SUCCESS
            }
        }
        return ItemInteractionResult.SUCCESS
    }

    private fun breakConnectedDoors(level: Level, startPos: BlockPos) {
        val maxBlocks = 60
        var brokenCount = 0
        
        val queue: Queue<BlockPos> = LinkedList()
        val visited = mutableSetOf<BlockPos>()
        
        queue.add(startPos)
        visited.add(startPos)
        
        while (queue.isNotEmpty() && brokenCount < maxBlocks) {
            val currentPos = queue.poll()
            
            // Check if it's our door
            if (level.getBlockState(currentPos).block == this) {
                level.setBlock(currentPos, Blocks.AIR.defaultBlockState(), 3)
                brokenCount++
                
                // Add adjacent blocks
                for (facing in net.minecraft.core.Direction.entries) {
                    val adjacentPos = currentPos.relative(facing)
                    if (visited.add(adjacentPos)) {
                        queue.add(adjacentPos)
                    }
                }
            }
        }
    }
}
