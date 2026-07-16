package net.drachi.cde.battleengine.command

import net.drachi.cde.CDE

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import com.mojang.brigadier.Command
import net.drachi.cde.battleengine.BattleEngineModule
import net.drachi.cde.battleengine.config.BattleEngineConfigManager
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.network.chat.Component
import net.minecraft.commands.Commands

object CommandManager {
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            val reloadCmd = Commands.literal("reload")
                .requires { source -> source.hasPermission(2) }
                .executes { context ->
                    BattleEngineConfigManager.loadConfig()
                    MoveRegistry.load()
                    net.drachi.cde.battleengine.battle.weather.WeatherRegistry.load()
                    
                    val source = context.source
                    source.sendSuccess({ Component.translatable("cdbe.message.config_reloaded").withStyle(net.minecraft.ChatFormatting.GREEN) }, true)
                    CDE.logger.info("CDBE config and moves manually reloaded.")
                    
                    Command.SINGLE_SUCCESS
                }

            val combatLogDetailCmd = Commands.literal("detail")
                .executes { context ->
                    val player = context.source.playerOrException
                    val enabled = PlayerCombatManager.toggleDetailedDamage(player)
                    val status = if (enabled) "§aENABLED" else "§cDISABLED"
                    player.sendSystemMessage(Component.translatable("cdbe.message.damage_breakdown", status))
                    Command.SINGLE_SUCCESS
                }

            val combatLogCmd = Commands.literal("combatlog")
                .then(combatLogDetailCmd)
                .executes { context ->
                    val player = context.source.playerOrException
                    val enabled = PlayerCombatManager.toggleCombatLog(player)
                    val status = if (enabled) "§aENABLED" else "§cDISABLED"
                    player.sendSystemMessage(Component.translatable("cdbe.message.combat_log", status))
                    Command.SINGLE_SUCCESS
                }

            val spawnCmd = Commands.literal("spawn")
                .requires { source -> source.hasPermission(2) }
                .then(Commands.argument("hostility", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests { _, builder -> 
                        builder.suggest("hostile").suggest("neutral").suggest("peaceful").buildFuture()
                    }
                    .then(Commands.argument("pokemon", com.cobblemon.mod.common.command.argument.PokemonPropertiesArgumentType.properties())
                        .executes { context ->
                            val hostility = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "hostility").lowercase()
                            
                            try {
                                val pokemon = com.cobblemon.mod.common.command.argument.PokemonPropertiesArgumentType.getPokemonProperties(context, "pokemon").create()
                                val player = context.source.playerOrException
                                val level = player.level()
                                
                                if (hostility == "hostile") {
                                    pokemon.persistentData.putString("cdbe_hostility", "hostile")
                                } else if (hostility == "peaceful") {
                                    pokemon.persistentData.putString("cdbe_hostility", "peaceful")
                                } else {
                                    pokemon.persistentData.putString("cdbe_hostility", "neutral")
                                }
                                
                                val entity = com.cobblemon.mod.common.entity.pokemon.PokemonEntity(level, pokemon)
                                
                                if (hostility == "hostile") {
                                    entity.pokemon.persistentData.putString("cdbe_hostility", "hostile")
                                } else if (hostility == "peaceful") {
                                    entity.pokemon.persistentData.putString("cdbe_hostility", "peaceful")
                                } else {
                                    entity.pokemon.persistentData.putString("cdbe_hostility", "neutral")
                                }
                                
                                val hitResult = player.pick(20.0, 0.0f, false)
                                val pos = hitResult.location
                                entity.setPos(pos.x, pos.y, pos.z)
                                
                                level.addFreshEntity(entity)
                                
                                if (hostility == "hostile") {
                                    net.drachi.cde.battleengine.battle.utility.SpawnManager.setEntityHostility(entity, net.drachi.cde.battleengine.battle.utility.HostilityState.HOSTILE)
                                } else if (hostility == "peaceful") {
                                    net.drachi.cde.battleengine.battle.utility.SpawnManager.setEntityHostility(entity, net.drachi.cde.battleengine.battle.utility.HostilityState.PEACEFUL)
                                } else {
                                    net.drachi.cde.battleengine.battle.utility.SpawnManager.setEntityHostility(entity, net.drachi.cde.battleengine.battle.utility.HostilityState.NEUTRAL)
                                }
                                context.source.sendSuccess({ Component.translatable("cdbe.message.spawn_success", hostility, pokemon.species.name).withStyle(net.minecraft.ChatFormatting.GREEN) }, true)
                            } catch (e: Exception) {
                                context.source.sendFailure(Component.translatable("cdbe.message.spawn_failure", e.message))
                            }
                            
                            Command.SINGLE_SUCCESS
                        }
                    )
                )

            val battleNode = Commands.literal("battle")
            val cdeNode = Commands.literal("cde")
            val cdeBattleNode = Commands.literal("battle")
            
            battleNode.then(reloadCmd).then(combatLogCmd).then(spawnCmd)
            cdeBattleNode.then(reloadCmd).then(combatLogCmd).then(spawnCmd)
            cdeNode.then(cdeBattleNode)
            
            dispatcher.register(battleNode)
            dispatcher.register(cdeNode)
        }
    }
}
