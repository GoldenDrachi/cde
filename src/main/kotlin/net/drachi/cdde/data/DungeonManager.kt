package net.drachi.cdde.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import net.drachi.cdde.CobblemonDungeonDungeonsEngine
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import java.io.File

object DungeonManager {
    var stairPosition: net.minecraft.core.BlockPos? = null
    
    val pokemonSpawns = mutableListOf<net.minecraft.core.BlockPos>()
    val itemSpawns = mutableListOf<net.minecraft.core.BlockPos>()
    
    fun clearSpawns() {
        pokemonSpawns.clear()
        itemSpawns.clear()
    }
    
    val configs = mutableMapOf<String, DungeonConfig>()

    /** Theme → list of ResourceLocations for that theme's rooms. */
    val availableRooms = mutableMapOf<String, MutableList<ResourceLocation>>()
    /** Theme → list of ResourceLocations for that theme's hallways (auto-categorized corridor pieces). */
    val availableHallways = mutableMapOf<String, MutableList<ResourceLocation>>()
    /** Theme → list of ResourceLocations for that theme's dead-end caps. */
    val availableEnds = mutableMapOf<String, MutableList<ResourceLocation>>()
    /** Theme → list of ResourceLocations for that theme's staircases. */
    val availableStairs = mutableMapOf<String, MutableList<ResourceLocation>>()

    /**
     * All loaded structure templates keyed by ResourceLocation.
     * Populated during [scanNbtStructures] by reading .nbt files directly from disk —
     * bypasses StructureTemplateManager which only resolves from datapacks.
     */
    val loadedTemplates = mutableMapOf<ResourceLocation, StructureTemplate>()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun init() {
        loadConfigs()
        scanNbtStructures()
    }

    private fun loadConfigs() {
        configs.clear()
        val configDir = FabricLoader.getInstance().configDir.resolve("cdde").toFile()
        if (!configDir.exists()) {
            configDir.mkdirs()

            // Generate default config
            val defaultFile = File(configDir, "default_dungeon.json")
            val defaultCfg = DungeonConfig(id = "default")
            defaultFile.writeText(json.encodeToString(DungeonConfig.serializer(), defaultCfg))
        }

        configDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
            try {
                val content = file.readText()
                val config = json.decodeFromString<DungeonConfig>(content)
                configs[config.id] = config
                CobblemonDungeonDungeonsEngine.logger.info("Loaded dungeon config: ${config.id}")
            } catch (e: Exception) {
                CobblemonDungeonDungeonsEngine.logger.error("Failed to load config file: ${file.name}", e)
            }
        }
    }

    private fun scanNbtStructures() {
        availableRooms.clear()
        availableHallways.clear()
        availableEnds.clear()
        availableStairs.clear()
        loadedTemplates.clear()

        val structuresDir = FabricLoader.getInstance().configDir.resolve("cdde/structures").toFile()
        if (!structuresDir.exists()) structuresDir.mkdirs()

        structuresDir.listFiles { file -> file.extension == "nbt" }?.forEach { file ->
            val name = file.nameWithoutExtension
            val parts = name.split("_")
            if (parts.size >= 3) {
                val theme = parts[0]
                val resourceId = ResourceLocation.fromNamespaceAndPath("cdde", name)

                // Load the .nbt file directly from disk into a StructureTemplate
                val template = loadNbtFile(file)
                if (template == null) return@forEach

                loadedTemplates[resourceId] = template

                // Categorize by naming convention: [theme]_room_*, [theme]_hallway_*, [theme]_end_*, [theme]_stairs_*
                when {
                    name.contains("_room_") ->
                        availableRooms.getOrPut(theme) { mutableListOf() }.add(resourceId)
                    name.contains("_hallway_") ->
                        availableHallways.getOrPut(theme) { mutableListOf() }.add(resourceId)
                    name.contains("_end_") ->
                        availableEnds.getOrPut(theme) { mutableListOf() }.add(resourceId)
                    name.contains("_stairs_") || name.contains("_stair_") ->
                        availableStairs.getOrPut(theme) { mutableListOf() }.add(resourceId)
                }
            }
        }

        CobblemonDungeonDungeonsEngine.logger.info(
            "Loaded ${loadedTemplates.size} templates: " +
            "${availableRooms.values.flatten().size} rooms, " +
            "${availableHallways.values.flatten().size} hallways, " +
            "${availableEnds.values.flatten().size} ends, " +
            "${availableStairs.values.flatten().size} stairs."
        )
    }

    /**
     * Reads a .nbt file from disk and inflates it into a [StructureTemplate].
     * Uses [NbtIo.readCompressed] + [StructureTemplate.load] with the block registry lookup.
     *
     * @return The loaded template, or null if loading failed.
     */
    private fun loadNbtFile(file: File): StructureTemplate? {
        return try {
            val nbt = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap())
            val template = StructureTemplate()
            template.load(BuiltInRegistries.BLOCK.asLookup(), nbt)
            template
        } catch (e: Exception) {
            CobblemonDungeonDungeonsEngine.logger.error("Failed to load NBT structure: ${file.name}", e)
            null
        }
    }
}
