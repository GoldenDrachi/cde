package net.drachi.cdde.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.drachi.cdde.CobblemonDungeonDungeonsEngine
import net.drachi.cdde.data.DungeonConfig
import net.drachi.cdde.data.DungeonManager
import net.drachi.cdde.generation.DungeonGenerator
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer

object DungeonCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = Commands.literal("cdde").requires { it.hasPermission(2) }

        val helpCmd = Commands.literal("help")
            .executes { context -> executeHelp(context.source) }

        val leaveCmd = Commands.literal("leave")
            .executes { context -> executeLeave(context.source) }

        val portalCmd = Commands.literal("portal")
            .then(
                Commands.argument("config_id", StringArgumentType.word())
                    .suggests { _, builder -> net.minecraft.commands.SharedSuggestionProvider.suggest(DungeonManager.configs.keys, builder) }
                    .executes { context -> executePortal(context.source, StringArgumentType.getString(context, "config_id")) }
            )

        val configCmd = Commands.literal("config")
            .then(
                Commands.literal("create")
                    .then(
                        Commands.argument("id", StringArgumentType.word())
                            .executes { context -> executeConfigCreate(context.source, StringArgumentType.getString(context, "id")) }
                    )
            )
            .then(
                Commands.literal("edit")
                    .then(
                        Commands.argument("id", StringArgumentType.word())
                            .suggests { _, builder -> net.minecraft.commands.SharedSuggestionProvider.suggest(DungeonManager.configs.keys, builder) }
                            .executes { context -> executeConfigEdit(context.source, StringArgumentType.getString(context, "id")) }
                    )
            )
            .then(
                Commands.literal("export")
                    .then(
                        Commands.argument("id", StringArgumentType.word())
                            .suggests { _, builder -> net.minecraft.commands.SharedSuggestionProvider.suggest(DungeonManager.configs.keys, builder) }
                            .executes { context -> executeConfigExport(context.source, StringArgumentType.getString(context, "id")) }
                    )
            )
            .then(
                Commands.literal("import")
                    .then(
                        Commands.argument("id", StringArgumentType.word())
                            .executes { context -> executeConfigImport(context.source, StringArgumentType.getString(context, "id")) }
                    )
            )

        val cleanupCmd = Commands.literal("cleanup")
            .then(
                Commands.literal("broken")
                    .executes { context -> executeCleanupBroken(context.source) }
            )
            .then(
                Commands.literal("all")
                    .executes { context -> executeCleanupAll(context.source) }
            )
            .then(
                Commands.literal("confirm")
                    .executes { context -> executeCleanupConfirm(context.source) }
            )
            .then(
                Commands.literal("list")
                    .executes { context -> executeCleanupList(context.source, 1) }
                    .then(
                        Commands.argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                            .executes { context -> executeCleanupList(context.source, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "page")) }
                    )
            )
            .then(
                Commands.literal("delete")
                    .then(
                        Commands.argument("uuid", StringArgumentType.word())
                            .suggests { context, builder -> 
                                val player = context.source.playerOrException
                                val pending = pendingCleanups[player.uuid] ?: emptyList()
                                val active = DungeonManager.activeDungeons.keys.map { it.toString() }
                                val allSuggestions = (pending + active).distinct()
                                net.minecraft.commands.SharedSuggestionProvider.suggest(allSuggestions, builder)
                            }
                            .executes { context -> executeCleanupDelete(context.source, StringArgumentType.getString(context, "uuid")) }
                    )
            )

        root.then(helpCmd).then(leaveCmd).then(portalCmd).then(configCmd).then(cleanupCmd)
        dispatcher.register(root)
    }

    private val pendingCleanups = mutableMapOf<java.util.UUID, List<String>>()

    private fun executeCleanupBroken(source: CommandSourceStack): Int {
        val player = source.playerOrException
        val broken = DungeonManager.getBrokenDungeons()
        if (broken.isEmpty()) {
            source.sendSuccess({ Component.literal("No broken dungeons found!") }, false)
            return 1
        }
        pendingCleanups[player.uuid] = broken
        source.sendSuccess({ Component.literal("Found ${broken.size} broken dungeons. Run '/cdde cleanup confirm' to delete them.") }, false)
        val limit = if (broken.size > 5) 5 else broken.size
        source.sendSuccess({ Component.literal("Showing first $limit: ${broken.take(limit).joinToString(", ")}") }, false)
        return 1
    }

    private fun executeCleanupAll(source: CommandSourceStack): Int {
        val player = source.playerOrException
        val all = DungeonManager.getAllDungeons()
        if (all.isEmpty()) {
            source.sendSuccess({ Component.literal("No dungeons found in the database!") }, false)
            return 1
        }
        pendingCleanups[player.uuid] = all
        source.sendSuccess({ Component.literal("Found ${all.size} total dungeons. Run '/cdde cleanup confirm' to completely wipe them.") }, false)
        return 1
    }

    private fun executeCleanupConfirm(source: CommandSourceStack): Int {
        val player = source.playerOrException
        val pending = pendingCleanups.remove(player.uuid)
        if (pending == null || pending.isEmpty()) {
            source.sendFailure(Component.literal("No pending cleanup tasks. Run '/cdde cleanup broken' first."))
            return 0
        }
        
        Thread {
            source.server.execute {
                source.sendSuccess({ Component.literal("Starting cleanup of ${pending.size} dungeons...") }, true)
            }
            val count = DungeonManager.performCleanup(pending)
            source.server.execute {
                source.sendSuccess({ Component.literal("Cleanup complete. Wiped $count dungeons.") }, true)
            }
        }.start()
        
        return 1
    }

    private fun executeCleanupList(source: CommandSourceStack, page: Int): Int {
        val player = source.playerOrException
        val pending = pendingCleanups[player.uuid]
        if (pending == null || pending.isEmpty()) {
            source.sendFailure(Component.literal("No pending cleanup tasks. Run '/cdde cleanup broken' first."))
            return 0
        }
        
        val pageSize = 10
        val totalPages = (pending.size + pageSize - 1) / pageSize
        if (page > totalPages) {
            source.sendFailure(Component.literal("Page $page does not exist (Max: $totalPages)."))
            return 0
        }
        
        source.sendSuccess({ Component.literal("--- Pending Cleanup (Page $page of $totalPages) ---") }, false)
        val start = (page - 1) * pageSize
        val end = minOf(start + pageSize, pending.size)
        
        for (i in start until end) {
            source.sendSuccess({ Component.literal("${i + 1}. ${pending[i]}") }, false)
        }
        source.sendSuccess({ Component.literal("Use '/cdde cleanup delete <uuid>' to remove a specific one.") }, false)
        return 1
    }

    private fun executeCleanupDelete(source: CommandSourceStack, uuidStr: String): Int {
        val player = source.playerOrException
        val uuid = try {
            java.util.UUID.fromString(uuidStr)
        } catch (e: Exception) {
            source.sendFailure(Component.literal("Invalid UUID format."))
            return 0
        }
        
        DungeonManager.performCleanup(listOf(uuid.toString()))
        source.sendSuccess({ Component.literal("Successfully wiped dungeon $uuid.") }, true)
        
        pendingCleanups[player.uuid]?.let { list ->
            pendingCleanups[player.uuid] = list.filter { it != uuid.toString() }
        }
        return 1
    }

    private fun executeHelp(source: CommandSourceStack): Int {
        source.sendSuccess({ Component.translatable("command.cdde.help.title").withStyle(net.minecraft.ChatFormatting.AQUA) }, false)
        source.sendSuccess({ Component.translatable("command.cdde.help.help") }, false)
        source.sendSuccess({ Component.translatable("command.cdde.help.portal") }, false)
        source.sendSuccess({ Component.translatable("command.cdde.help.leave") }, false)
        source.sendSuccess({ Component.translatable("command.cdde.help.config_create") }, false)
        source.sendSuccess({ Component.translatable("command.cdde.help.config_edit") }, false)
        source.sendSuccess({ Component.translatable("command.cdde.help.config_export") }, false)
        source.sendSuccess({ Component.translatable("command.cdde.help.config_import") }, false)
        source.sendSuccess({ Component.translatable("command.cdde.help.cleanup_broken") }, false)
        source.sendSuccess({ Component.translatable("command.cdde.help.cleanup_all") }, false)
        source.sendSuccess({ Component.translatable("command.cdde.help.cleanup_list") }, false)
        source.sendSuccess({ Component.translatable("command.cdde.help.cleanup_confirm") }, false)
        source.sendSuccess({ Component.translatable("command.cdde.help.cleanup_delete") }, false)
        return 1
    }

    private fun executeLeave(source: CommandSourceStack): Int {
        val player = source.playerOrException
        val level = source.level
        val dim = level.dimension().location()

        if (dim.namespace != "cdde" || dim.path != "dungeon") {
            source.sendFailure(Component.translatable("message.cdde.error.not_in_dungeon"))
            return 0
        }

        val z = player.blockPosition().z
        val instanceIndex = z / 10000
        val expectedOriginZ = instanceIndex * 10000

        val instance = DungeonManager.activeDungeons.values.find { it.originZ == expectedOriginZ }
        if (instance == null) {
            source.sendFailure(Component.translatable("message.cdde.error.no_active_dungeon"))
            // Fallback teleport to overworld
            val overworld = source.server.getLevel(net.minecraft.world.level.Level.OVERWORLD)
            if (overworld != null) {
                val spawn = overworld.sharedSpawnPos
                player.teleportTo(overworld, spawn.x.toDouble(), spawn.y.toDouble(), spawn.z.toDouble(), player.yRot, player.xRot)
            }
            return 0
        }

        // Return player
        val returnPos = instance.returnLocations[player.uuid]
        val overworld = source.server.getLevel(net.minecraft.world.level.Level.OVERWORLD)
        
        player.portalCooldown = 100 // Prevent immediate re-entry if they return onto a portal block
        
        if (returnPos != null && overworld != null) {
            player.teleportTo(overworld, returnPos.x.toDouble() + 0.5, returnPos.y.toDouble(), returnPos.z.toDouble() + 0.5, player.yRot, player.xRot)
        } else if (overworld != null) {
            val spawn = overworld.sharedSpawnPos
            player.teleportTo(overworld, spawn.x.toDouble() + 0.5, spawn.y.toDouble(), spawn.z.toDouble() + 0.5, player.yRot, player.xRot)
        }

        // Remove player from the dungeon tracking
        instance.returnLocations.remove(player.uuid)
        net.drachi.cdde.database.DatabaseManager.removeDungeonPlayer(instance.instanceId, player.uuid)
        
        source.sendSuccess({ Component.translatable("message.cdde.left_dungeon") }, true)

        com.cobblemon.mod.common.Cobblemon.storage.getParty(player).heal()
        val partyName = DungeonManager.getPartyName(player)
        net.drachi.cdde.network.NetworkHandler.sendDungeonResult(player, instance.config.id, partyName, "message.cdde.result.abandoned")
        
        return 1
    }

    private fun executePortal(source: CommandSourceStack, configId: String): Int {
        val player = source.playerOrException
        
        if (!DungeonManager.configs.containsKey(configId)) {
            source.sendFailure(Component.literal("Config ID '$configId' not found."))
            return 0
        }

        // Give the player a portal block that is pre-configured
        val itemStack = net.minecraft.world.item.ItemStack(net.drachi.cdde.registry.ModBlocks.DUNGEON_PORTAL)
        
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA, itemStack) { tag ->
            tag.putString("ConfigId", configId)
            tag.putString("id", "cdde:dungeon_portal") // Ensure block entity ID is present
        }
        
        itemStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Dungeon Portal ($configId)").withStyle(net.minecraft.ChatFormatting.AQUA))
        
        if (!player.inventory.add(itemStack)) {
            player.drop(itemStack, false)
        }
        
        source.sendSuccess({ Component.literal("Gave 1 Dungeon Portal for config '$configId'") }, true)
        return 1
    }

    private fun executeConfigExport(source: CommandSourceStack, id: String): Int {
        val success = net.drachi.cdde.data.ConfigManager.exportToJSON(id)
        if (success) {
            source.sendSuccess({ Component.literal("Successfully exported config '$id' to JSON.") }, true)
            return 1
        } else {
            source.sendFailure(Component.literal("Failed to export: Config '$id' not found in current storage."))
            return 0
        }
    }

    private fun executeConfigImport(source: CommandSourceStack, id: String): Int {
        val success = net.drachi.cdde.data.ConfigManager.importFromJSON(id)
        if (success) {
            source.sendSuccess({ Component.literal("Successfully imported config '$id' from JSON.") }, true)
            return 1
        } else {
            source.sendFailure(Component.literal("Failed to import: Config '$id' not found in JSON storage."))
            return 0
        }
    }

    private fun executeConfigCreate(source: CommandSourceStack, id: String): Int {
        val player = source.playerOrException
        if (DungeonManager.configs.containsKey(id)) {
            source.sendFailure(Component.literal("Config '$id' already exists. Use '/cdde config edit $id' instead."))
            return 0
        }
        val config = DungeonConfig(id = id)
        val jsonStr = kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(DungeonConfig.serializer(), config)
        net.drachi.cdde.network.NetworkHandler.CHANNEL.serverHandle(player).send(net.drachi.cdde.network.OpenConfigScreenPacket(id, jsonStr))
        return 1
    }

    private fun executeConfigEdit(source: CommandSourceStack, id: String): Int {
        val player = source.playerOrException
        val config = DungeonManager.configs[id]
        if (config == null) {
            source.sendFailure(Component.literal("Config '$id' not found."))
            return 0
        }
        val jsonStr = kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(DungeonConfig.serializer(), config)
        net.drachi.cdde.network.NetworkHandler.CHANNEL.serverHandle(player).send(net.drachi.cdde.network.OpenConfigScreenPacket(id, jsonStr))
        return 1
    }
}
