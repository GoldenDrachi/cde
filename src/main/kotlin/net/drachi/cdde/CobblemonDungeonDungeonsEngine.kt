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
        net.drachi.cdde.registry.ModItemGroups.register()
        net.drachi.cdde.data.DungeonManager.init()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            net.drachi.cdde.command.DungeonCommand.register(dispatcher)
        }

        ServerTickEvents.START_SERVER_TICK.register { server ->
            val stairPos = DungeonManager.stairPosition
            if (stairPos != null) {
                for (player in server.playerList.players) {
                    val pPos = player.blockPosition()
                    val dx = pPos.x - stairPos.x
                    val dz = pPos.z - stairPos.z
                    val dy = pPos.y - stairPos.y
                    // Check if player is within the 3x3 horizontal bounds, and at or above the stairs bottom level
                    if (dx in -1..1 && dz in -1..1 && dy >= -4) {
                        player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§aYou stand on the stairs! Floor transition detected.§r"),
                            true // Displays in Action Bar
                        )
                    }
                }
            }
        }
    }
}
