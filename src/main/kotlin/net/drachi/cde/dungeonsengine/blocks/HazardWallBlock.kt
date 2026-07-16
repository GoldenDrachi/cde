package net.drachi.cde.dungeonsengine.blocks

import net.drachi.cde.dungeonsengine.mechanics.HazardType
import net.drachi.cde.dungeonsengine.mechanics.PlayerHazardStateManager
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.EntityCollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.RenderShape

class HazardWallBlock(val type: HazardType, properties: Properties) : Block(properties) {

    init {
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.WATERLOGGED, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(BlockStateProperties.WATERLOGGED)
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        return Shapes.empty()
    }

    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.INVISIBLE
    }
    
    override fun getVisualShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        return Shapes.empty()
    }
    
    override fun getInteractionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos
    ): VoxelShape {
        return Shapes.empty()
    }

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        if (context is EntityCollisionContext) {
            val entity: Entity? = context.entity

            // A wall that extends 1 block downwards to cover the hazard block, and 10 blocks upwards to prevent jumping over
            val wallShape = Shapes.block()

            if (entity is Player || entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                if (entity is Player && (entity.isCreative || entity.isSpectator)) return Shapes.empty()

                if (entity is ServerPlayer) {
                    val selectedSlot = PlayerHazardStateManager.getSelectedSlot(entity.uuid)
                    if (selectedSlot >= 0) {
                        val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(entity)
                        val pokemon = party.get(selectedSlot)
                        if (pokemon != null && net.drachi.cde.dungeonsengine.mechanics.HazardMechanic.canTraverse(pokemon, type)) {
                            return Shapes.empty()
                        }
                    }
                    return wallShape
                } else if (entity is Player) {
                    // Client-side player
                    if (net.drachi.cde.dungeonsengine.mechanics.HazardMechanicClient.canTraverse(type)) {
                        return Shapes.empty()
                    }
                    return wallShape
                } else if (entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                    if (net.drachi.cde.dungeonsengine.mechanics.HazardMechanic.canTraverse(entity.pokemon, type)) {
                        return Shapes.empty()
                    }
                    return wallShape
                }
            } else if (entity is net.minecraft.world.entity.item.ItemEntity) {
                return Shapes.empty()
            }
            
            // Default behavior for other entities (like standard mobs), or if entity is null (e.g. physics queries during jump)
            return wallShape
        }
        return Shapes.block()
    }

    override fun propagatesSkylightDown(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean {
        return true
    }

    override fun getShadeBrightness(state: BlockState, level: BlockGetter, pos: BlockPos): Float {
        return 1.0f
    }


}
