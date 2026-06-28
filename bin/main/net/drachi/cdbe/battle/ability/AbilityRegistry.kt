package net.drachi.cdbe.battle.ability

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.drachi.cdbe.CobblemonDungeonBattleEngine
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.io.FileReader
import java.nio.file.Files

object AbilityRegistry {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val abilities = mutableMapOf<String, AbilityData>()

    fun load() {
        abilities.clear()

        val configDir = File(FabricLoader.getInstance().configDir.toFile(), "cdbe/abilities")
        if (!configDir.exists()) {
            configDir.mkdirs()
        }

        extractDefaultAbilities(configDir)

        val files = configDir.listFiles { _, name -> name.endsWith(".json") }
        if (files != null) {
            for (file in files) {
                try {
                    FileReader(file).use { reader ->
                        val data = gson.fromJson(reader, AbilityData::class.java)
                        abilities[data.cobblemonAbilityId] = data
                    }
                } catch (e: Exception) {
                    CobblemonDungeonBattleEngine.LOGGER.error("Failed to load ability file: $", e)
                }
            }
            CobblemonDungeonBattleEngine.LOGGER.info("Loaded $ abilities.")
        }
    }

    private fun extractDefaultAbilities(configDir: File) {
        val modContainer = FabricLoader.getInstance().getModContainer(CobblemonDungeonBattleEngine.MOD_ID).orElse(null)
        if (modContainer == null) {
            CobblemonDungeonBattleEngine.LOGGER.error("Could not find ModContainer for cdbe!")
            return
        }

        val abilitiesPath = modContainer.findPath("data/cdbe/abilities").orElse(null)
        if (abilitiesPath == null) {
            CobblemonDungeonBattleEngine.LOGGER.error("Could not find data/cdbe/abilities in mod jar!")
            return
        }

        try {
            Files.walk(abilitiesPath).use { stream ->
                stream.filter { Files.isRegularFile(it) }.forEach { path ->
                    val fileName = path.fileName.toString()
                    val targetFile = File(configDir, fileName)

                    if (!targetFile.exists()) {
                        try {
                            Files.copy(path, targetFile.toPath())
                        } catch (e: Exception) {
                            CobblemonDungeonBattleEngine.LOGGER.error("Failed to extract ability file: $fileName", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            CobblemonDungeonBattleEngine.LOGGER.error("Error walking through default abilities", e)
        }
    }

    fun getAbility(id: String): AbilityData? {
        return abilities[id]
    }
}