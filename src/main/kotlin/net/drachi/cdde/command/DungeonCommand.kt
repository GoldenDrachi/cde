package net.drachi.cdde.command

import com.mojang.brigadier.CommandDispatcher
import net.drachi.cdde.data.DungeonConfig
import net.drachi.cdde.generation.DungeonGenerator
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

object DungeonCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("cdde")
                .requires { it.hasPermission(2) }
                .then(
                    Commands.literal("generate")
                        .executes { context ->
                            val source = context.source
                            val level = source.level
                            val pos = source.playerOrException.blockPosition()

                            source.sendSuccess({ Component.literal("Generating dungeon POC at $pos...") }, true)

                            try {
                                val config = DungeonConfig("test_run")
                                val generator = DungeonGenerator(level, pos, config)
                                generator.generate()
                            } catch (e: Throwable) {
                                net.drachi.cdde.CobblemonDungeonDungeonsEngine.logger.error("Dungeon generation failed!", e)
                                throw e
                            }

                            1
                        }
                )
        )
    }
}
