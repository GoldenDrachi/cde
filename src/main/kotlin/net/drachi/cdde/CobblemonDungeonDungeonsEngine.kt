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
            }
        }
    }
}
