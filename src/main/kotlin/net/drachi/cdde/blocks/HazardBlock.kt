package net.drachi.cdde.blocks

import net.drachi.cdde.mechanics.HazardMechanic
import net.drachi.cdde.mechanics.HazardType
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.EntityCollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.drachi.cdde.mechanics.HazardMechanicClient
import net.drachi.cdde.mechanics.PlayerHazardStateManager
import net.minecraft.server.level.ServerPlayer

class HazardBlock(val hazardType: HazardType, properties: Properties) : Block(properties) {
    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        if (context is EntityCollisionContext) {
            val entity: Entity? = context.entity

            val wallShape = Shapes.create(0.0, 0.0, 0.0, 1.0, 10.0, 1.0)
            
            if (entity is ServerPlayer) {
                val selectedSlot = PlayerHazardStateManager.getSelectedSlot(entity.uuid)
                if (selectedSlot >= 0) {
                    val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(entity)
                    val pokemon = party.get(selectedSlot)
                    if (pokemon != null && HazardMechanic.canTraverse(pokemon, hazardType)) {
                        return Shapes.block()
                    }
                }
                return wallShape
            } else if (entity is Player) {
                // Client-side player
                if (HazardMechanicClient.canTraverse(hazardType)) {
                    return Shapes.block()
                }
                return wallShape
            } else if (entity is PokemonEntity) {
                if (HazardMechanic.canTraverse(entity.pokemon, hazardType)) {
                    return Shapes.block()
                }
                return wallShape
            }

            // Default behavior for other entities (like items or standard mobs)
            // returning Shapes.empty() so items fall in, etc.
            return Shapes.empty()
        }

        return Shapes.block()
    }

    override fun getRenderShape(state: BlockState): net.minecraft.world.level.block.RenderShape {
        if (hazardType == HazardType.VOID) {
            return net.minecraft.world.level.block.RenderShape.INVISIBLE
        }
        return super.getRenderShape(state)
    }
}
