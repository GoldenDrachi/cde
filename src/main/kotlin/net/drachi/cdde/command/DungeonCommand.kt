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

        val generateCmd = Commands.literal("generate")
            .then(
                Commands.argument("hazard", StringArgumentType.word())
                    .executes { context -> executeGenerate(context.source, StringArgumentType.getString(context, "hazard")) }
            )
            .executes { context -> executeGenerate(context.source, null) }

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

        root.then(generateCmd).then(leaveCmd).then(portalCmd).then(configCmd)
        dispatcher.register(root)
    }

    private fun executeGenerate(source: CommandSourceStack, hazardArg: String?): Int {
        val player = source.playerOrException
        val server = source.server
        
        // Find dimension
        val dungeonDimKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("cdde", "dungeon"))
        val dungeonLevel = server.getLevel(dungeonDimKey)
        
        if (dungeonLevel == null) {
            source.sendFailure(Component.translatable("message.cdde.error.no_dimension"))
            return 0
        }

        var config = DungeonConfig("test_run")
        if (hazardArg != null) {
            val prefix = if (hazardArg.contains(":")) "" else "minecraft:"
            val fc = config.getFloorConfig(1)
            fc.hazards = mutableListOf(prefix + hazardArg)
            config.floorRules = mutableListOf(net.drachi.cdde.data.FloorRule("*", fc))
        }

        val instance = DungeonManager.allocateInstance(config)
        source.sendSuccess({ net.minecraft.network.chat.Component.translatable("message.cdde.generating", instance.instanceId.toString()) }, true)

        try {
            // Generate Floor 1
            val gen1 = DungeonGenerator(dungeonLevel, net.minecraft.core.BlockPos(instance.originX, 64, instance.originZ), config, 1)
            gen1.generate()
            val startPosFloor1 = gen1.startPosition ?: net.minecraft.core.BlockPos(instance.originX, 65, instance.originZ)
            instance.floorStartPositions[1] = startPosFloor1
            if (gen1.stairPosition != null) {
                instance.stairPositions[1] = gen1.stairPosition!!
            }
            
            // Generate Floor 2
            val gen2 = DungeonGenerator(dungeonLevel, net.minecraft.core.BlockPos(instance.originX + 1000, 64, instance.originZ), config, 2)
            gen2.generate()
            val startPosFloor2 = gen2.startPosition ?: net.minecraft.core.BlockPos(instance.originX + 1000, 65, instance.originZ)
            instance.floorStartPositions[2] = startPosFloor2
            if (gen2.stairPosition != null) {
                instance.stairPositions[2] = gen2.stairPosition!!
            }

            // Find all party members (fallback to just the player if not in a party)
            val partyMembers = net.drachi.cdde.api.GroupAPI.getPartyMembers(player.uuid) ?: listOf(player.uuid)
            val playersToTeleport = partyMembers.mapNotNull { server.playerList.getPlayer(it) }

            // Array of offsets to prevent entities from clipping into each other
            val spawnOffsets = arrayOf(
                Pair(0, 0), Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1),
                Pair(1, 1), Pair(-1, 1), Pair(1, -1), Pair(-1, -1),
                Pair(2, 0), Pair(-2, 0), Pair(0, 2), Pair(0, -2)
            )
            var spawnIndex = 0

            playersToTeleport.forEach { member ->
                // Pre-calculate safe return location while overworld chunk is fully loaded
                val safeReturn = net.drachi.cdde.data.DungeonManager.getSafeOverworldReturn(source.server.getLevel(net.minecraft.world.level.Level.OVERWORLD)!!, member.blockPosition())
                instance.returnLocations[member.uuid] = safeReturn
                net.drachi.cdde.database.DatabaseManager.saveDungeonPlayer(instance.instanceId, member.uuid, safeReturn)

                // Pick a spot for the player
                val pOffset = spawnOffsets[spawnIndex % spawnOffsets.size]
                spawnIndex++
                val pPosRaw = net.minecraft.core.BlockPos(startPosFloor1.x + pOffset.first, startPosFloor1.y, startPosFloor1.z + pOffset.second)
                val pPos = net.drachi.cdde.data.DungeonManager.findSafeSpawn(dungeonLevel, pPosRaw)
                
                // Teleport player (add 0.5 to center in block)
                member.teleportTo(dungeonLevel, pPos.x.toDouble() + 0.5, pPos.y.toDouble(), pPos.z.toDouble() + 0.5, member.yRot, member.xRot)

                // Teleport out-of-ball party Pokemon
                val memberParty = com.cobblemon.mod.common.Cobblemon.storage.getParty(member)
                for (i in 0 until memberParty.size()) {
                    val pokemon = memberParty.get(i)
                    if (pokemon != null && pokemon.entity != null) {
                        val pEntity = pokemon.entity!!
                        
                        val pokeOffset = spawnOffsets[spawnIndex % spawnOffsets.size]
                        spawnIndex++
                        val pokePosRaw = net.minecraft.core.BlockPos(startPosFloor1.x + pokeOffset.first, startPosFloor1.y, startPosFloor1.z + pokeOffset.second)
                        val pokePos = net.drachi.cdde.data.DungeonManager.findSafeSpawn(dungeonLevel, pokePosRaw)
                        
                        pEntity.teleportTo(pokePos.x.toDouble() + 0.5, pokePos.y.toDouble(), pokePos.z.toDouble() + 0.5)
                    }
                }

                // Show initial floor title
                member.server.commands.performPrefixedCommand(
                    member.createCommandSourceStack().withPermission(2).withSuppressedOutput(),
                    "title @s title {\"translate\":\"message.cdde.floor_eg\", \"color\":\"yellow\"}"
                )
            }
        } catch (e: Throwable) {
            CobblemonDungeonDungeonsEngine.logger.error("Dungeon generation failed!", e)
            source.sendFailure(Component.translatable("message.cdde.error.generation_failed", e.message ?: "Unknown error"))
            return 0
        }

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
