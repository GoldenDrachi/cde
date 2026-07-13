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

            val wallShape = Shapes.block()
            
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

            // Default behavior for other entities (like standard mobs)
            return wallShape
        }

        return Shapes.block()
    }

    override fun getRenderShape(state: BlockState): net.minecraft.world.level.block.RenderShape {
        if (hazardType == HazardType.VOID) {
            return net.minecraft.world.level.block.RenderShape.INVISIBLE
        }
        return super.getRenderShape(state)
    }

    private fun handleHazardContact(level: net.minecraft.world.level.Level, pos: BlockPos, entity: Entity) {
        if (!level.isClientSide) {
            if (entity is net.minecraft.world.entity.item.ItemEntity) {
                if (hazardType == HazardType.LAVA || hazardType == HazardType.VOID) {
                    entity.discard()
                }
                return
            }

            var allowed = false
            if (entity is ServerPlayer) {
                val selectedSlot = PlayerHazardStateManager.getSelectedSlot(entity.uuid)
                if (selectedSlot >= 0) {
                    val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(entity)
                    val pokemon = party.get(selectedSlot)
                    if (pokemon != null && HazardMechanic.canTraverse(pokemon, hazardType)) {
                        allowed = true
                    }
                }
            } else if (entity is PokemonEntity) {
                if (HazardMechanic.canTraverse(entity.pokemon, hazardType)) {
                    allowed = true
                }
            }
            if (!allowed && (entity is ServerPlayer || entity is PokemonEntity)) {
                // Ensure the entity is actually significantly inside the block, not just touching the edge
                val dx = Math.abs(entity.x - (pos.x + 0.5))
                val dz = Math.abs(entity.z - (pos.z + 0.5))
                if (dx < 0.4 && dz < 0.4) {
                    // Teleport to nearest safe block
                    val safePos = net.drachi.cdde.data.DungeonManager.findSafeSpawn(level as net.minecraft.server.level.ServerLevel, pos)
                    entity.teleportTo(safePos.x.toDouble() + 0.5, safePos.y.toDouble(), safePos.z.toDouble() + 0.5)
                }
            }
        }
    }

    override fun entityInside(state: BlockState, level: net.minecraft.world.level.Level, pos: BlockPos, entity: Entity) {
        handleHazardContact(level, pos, entity)
    }

    override fun stepOn(level: net.minecraft.world.level.Level, pos: BlockPos, state: BlockState, entity: Entity) {
        handleHazardContact(level, pos, entity)
        super.stepOn(level, pos, state, entity)
    }
}
