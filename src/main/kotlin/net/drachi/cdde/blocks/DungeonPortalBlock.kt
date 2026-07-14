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
    companion object {
        val AXIS = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS
        protected val X_AABB = net.minecraft.world.level.block.Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0)
        protected val Z_AABB = net.minecraft.world.level.block.Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0)
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(AXIS, net.minecraft.core.Direction.Axis.X))
    }

    override fun createBlockStateDefinition(builder: net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState>) {
        builder.add(AXIS)
    }

    override fun getStateForPlacement(context: net.minecraft.world.item.context.BlockPlaceContext): BlockState? {
        return defaultBlockState().setValue(AXIS, context.horizontalDirection.axis)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return when (state.getValue(AXIS)) {
            net.minecraft.core.Direction.Axis.Z -> Z_AABB
            else -> X_AABB
        }
    }

    // The portal block should not have collision so players can walk into it
    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return net.minecraft.world.phys.shapes.Shapes.empty()
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
            
            val config = DungeonManager.configs[configId]
            if (config == null) {
                entity.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cThis dungeon portal is linked to an invalid or missing config: $configId"))
                entity.portalCooldown = 20
                return
            }

            // Group API check
            val partyMembers = net.drachi.cdde.api.GroupAPI.getPartyMembers(entity.uuid)
            if (partyMembers != null) {
                val leader = partyMembers.first() // first is always the leader
                if (entity.uuid != leader) {
                    entity.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cOnly the party leader can initiate a dungeon run."))
                    entity.portalCooldown = 40
                    return
                }
            }

            // Create instance
            val instance = DungeonManager.allocateInstance(config)
            
            val dungeonLevel = level.server!!.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cdde", "dungeon")))!!

            // Generate floor 1
            val generator = DungeonGenerator(
                dungeonLevel,
                net.minecraft.core.BlockPos(instance.originX, 64, instance.originZ),
                instance.config,
                1
            )
            generator.generate()
            
            val startPos = generator.startPosition ?: BlockPos(instance.originX, 65, instance.originZ)
            instance.floorStartPositions[1] = startPos
            if (generator.stairPosition != null) {
                instance.stairPositions[1] = generator.stairPosition!!
            }

            val playersToTeleport = partyMembers?.mapNotNull { entity.server.playerList.getPlayer(it) } ?: listOf(entity)
            
            // Array of offsets to prevent entities from clipping into each other
            val spawnOffsets = arrayOf(
                Pair(0, 0), Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1),
                Pair(1, 1), Pair(-1, 1), Pair(1, -1), Pair(-1, -1),
                Pair(2, 0), Pair(-2, 0), Pair(0, 2), Pair(0, -2)
            )
            var spawnIndex = 0

            playersToTeleport.forEach { member ->
                // Set their cooldown to prevent re-triggering upon entry
                member.portalCooldown = 100
                instance.returnLocations[member.uuid] = member.blockPosition()
                
                val pOffset = spawnOffsets[spawnIndex % spawnOffsets.size]
                spawnIndex++
                val pPosRaw = startPos.offset(pOffset.first, 0, pOffset.second)
                val pPos = DungeonManager.findSafeSpawn(dungeonLevel, pPosRaw)
                
                member.teleportTo(dungeonLevel, pPos.x.toDouble() + 0.5, pPos.y.toDouble(), pPos.z.toDouble() + 0.5, member.yRot, member.xRot)

                // Teleport out-of-ball party Pokemon
                val memberParty = com.cobblemon.mod.common.Cobblemon.storage.getParty(member)
                for (i in 0 until memberParty.size()) {
                    val pokemon = memberParty.get(i)
                    if (pokemon != null && pokemon.entity != null) {
                        val pEntity = pokemon.entity!!
                        val pokeOffset = spawnOffsets[spawnIndex % spawnOffsets.size]
                        spawnIndex++
                        val pokePosRaw = startPos.offset(pokeOffset.first, 0, pokeOffset.second)
                        val pokePos = DungeonManager.findSafeSpawn(dungeonLevel, pokePosRaw)
                        pEntity.teleportTo(pokePos.x.toDouble() + 0.5, pokePos.y.toDouble(), pokePos.z.toDouble() + 0.5)
                    }
                }
                
                member.server.commands.performPrefixedCommand(
                    member.createCommandSourceStack().withPermission(2).withSuppressedOutput(),
                    "title @s title {\"translate\":\"message.cdde.floor_eg\", \"color\":\"yellow\"}"
                )
            }
        }
    }
}
