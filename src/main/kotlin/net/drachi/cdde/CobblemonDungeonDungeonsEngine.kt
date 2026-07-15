package net.drachi.cdde

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.drachi.cdde.data.DungeonManager

object CobblemonDungeonDungeonsEngine : ModInitializer {
    val logger = LoggerFactory.getLogger("cdde")

    override fun onInitialize() {
        logger.info("Initializing Cobblemon Dungeon Dungeons Engine!")
        
        net.drachi.cdde.registry.ModBlocks.register()
        net.drachi.cdde.registry.ModBlockEntities.register()
        net.drachi.cdde.registry.ModItems.register()
        net.drachi.cdde.registry.ModItemGroups.register()
        net.drachi.cdde.data.DungeonManager.init()
        net.drachi.cdde.network.NetworkHandler.registerPayloads()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            net.drachi.cdde.command.DungeonCommand.register(dispatcher)
        }

        ServerTickEvents.START_SERVER_TICK.register { server ->
            net.drachi.cdde.data.DungeonManager.tick(server)
            
            for (player in server.playerList.players) {
                val instance = net.drachi.cdde.data.DungeonManager.getActiveDungeon(player)
                if (instance != null) {
                    val pPos = player.blockPosition()
                    
                    // Check if player stands on any known stair position for their floor
                    // Check if player stands on the stair position for the CURRENT floor
                    val stairPos = instance.stairPositions[instance.currentFloor]
                    if (stairPos != null) {
                        // stairPos is the center of the 3x3 stairs area
                        val dx = pPos.x - stairPos.x
                        val dz = pPos.z - stairPos.z
                        val dy = pPos.y - stairPos.y
                        
                        // Check if player is on the stairs
                        if (dx in -2..2 && dz in -2..2 && dy >= -4 && dy <= 4) {
                            if (player is net.minecraft.server.level.ServerPlayer) {
                                net.drachi.cdde.data.DungeonManager.onPlayerInteractStairs(player, stairPos)
                            }
                        }
                    }
                }
            }
        }

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            val dim = world.dimension().location()
            if (dim.namespace == "cdde" && dim.path == "dungeon") {
                if (entity is net.minecraft.world.entity.Mob) {
                    if (!entity.tags.contains("cdde_spawned")) {
                        var isPlayerOwned = false
                        if (entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                            if (entity.pokemon.isPlayerOwned()) {
                                isPlayerOwned = true
                            }
                        } else if (entity is net.minecraft.world.entity.TamableAnimal && entity.isTame) {
                            isPlayerOwned = true
                        }
                        
                        if (!isPlayerOwned) {
                            entity.discard()
                        }
                    }
                }

                if (entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                    val w = entity.bbWidth
                    val h = entity.bbHeight
                    if (w > 3.0f || h > 4.0f) {
                        val widthScale = 3.0f / w
                        val heightScale = 4.0f / h
                        val scale = kotlin.math.min(widthScale, heightScale)
                        entity.pokemon.scaleModifier *= scale
                        entity.refreshDimensions() // Ensure bounding box updates
                    }
                }
            }
        }

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register { handler, sender, server ->
            val player = handler.player
            val evictedPos = net.drachi.cdde.database.DatabaseManager.getAndRemoveEvictedPlayer(player.uuid)
            
            if (evictedPos != null) {
                val overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD)
                if (overworld != null) {
                    player.portalCooldown = 100
                    player.teleportTo(overworld, evictedPos.x.toDouble() + 0.5, evictedPos.y.toDouble(), evictedPos.z.toDouble() + 0.5, player.yRot, player.xRot)
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Your dungeon ran out of time or was abandoned. You were returned to safety.").withStyle(net.minecraft.ChatFormatting.RED))
                }
            } else {
                val dim = player.level().dimension().location()
                if (dim.namespace == "cdde" && dim.path == "dungeon") {
                    val instance = DungeonManager.getActiveDungeon(player)
                    if (instance != null) {
                        val playerX = player.blockPosition().x
                        val expectedMinX = instance.originX + ((instance.currentFloor - 1) * 1000)
                        val expectedMaxX = expectedMinX + 1000
                        
                        if (playerX < expectedMinX || playerX > expectedMaxX) {
                            val startPos = instance.floorStartPositions[instance.currentFloor]
                            if (startPos != null) {
                                val pPos = DungeonManager.findSafeSpawn(player.serverLevel(), startPos)
                                player.teleportTo(pPos.x.toDouble() + 0.5, pPos.y.toDouble(), pPos.z.toDouble() + 0.5)
                                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Your team advanced to floor ${instance.currentFloor} while you were offline! You have been moved to their floor.").withStyle(net.minecraft.ChatFormatting.YELLOW))
                            }
                        }
                    } else {
                        // Safety fallback for orphaned players
                        val overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD)
                        if (overworld != null) {
                            val spawn = overworld.sharedSpawnPos
                            player.teleportTo(overworld, spawn.x.toDouble(), spawn.y.toDouble(), spawn.z.toDouble(), player.yRot, player.xRot)
                        }
                    }
                }
            }
        }

        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.ALLOW_DEATH.register { player, damageSource, damageAmount ->
            val dim = player.level().dimension().location()
            if (dim.namespace == "cdde" && dim.path == "dungeon") {
                val instance = DungeonManager.getActiveDungeon(player)
                
                // Heal player and remove effects
                player.health = player.maxHealth
                player.removeAllEffects()

                // Teleport to overworld
                val overworld = player.server.getLevel(net.minecraft.world.level.Level.OVERWORLD)
                val returnPos = instance?.returnLocations?.get(player.uuid)
                
                player.portalCooldown = 100
                if (returnPos != null && overworld != null) {
                    player.teleportTo(overworld, returnPos.x.toDouble() + 0.5, returnPos.y.toDouble(), returnPos.z.toDouble() + 0.5, player.yRot, player.xRot)
                } else if (overworld != null) {
                    val spawn = overworld.sharedSpawnPos
                    player.teleportTo(overworld, spawn.x.toDouble() + 0.5, spawn.y.toDouble(), spawn.z.toDouble() + 0.5, player.yRot, player.xRot)
                }

                // Remove from dungeon
                if (instance != null) {
                    instance.returnLocations.remove(player.uuid)
                    net.drachi.cdde.database.DatabaseManager.removeDungeonPlayer(instance.instanceId, player.uuid)
                }

                // Heal Pokemon and show failure screen
                com.cobblemon.mod.common.Cobblemon.storage.getParty(player).heal()
                val partyName = DungeonManager.getPartyName(player)
                val configId = instance?.config?.id ?: "Unknown"
                net.drachi.cdde.network.NetworkHandler.sendDungeonResult(player, configId, partyName, "message.cdde.result.failed")

                return@register false
            }
            return@register true
        }
    }
}
