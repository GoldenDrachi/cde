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

class TreasureDoorBlock(properties: Properties) : Block(properties) {

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
            if (stack.`is`(ModItems.TREASURE_KEY)) {
                // Consume the key
                if (!player.isCreative) {
                    stack.shrink(1)
                }

                // Recursively remove up to 60 connected TREASURE_DOOR blocks
                breakConnectedDoors(level, pos)
                
                return ItemInteractionResult.SUCCESS
            }
        } else {
            // Client side returns success to trigger the swing animation if holding the right item
            if (stack.`is`(ModItems.TREASURE_KEY)) {
                return ItemInteractionResult.SUCCESS
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
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
