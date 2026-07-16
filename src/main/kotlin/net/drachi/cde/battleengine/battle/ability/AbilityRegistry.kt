package net.drachi.cde.battleengine.battle.ability

import net.drachi.cde.CDE
import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.io.FileReader
import java.nio.file.Files

object AbilityRegistry {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val abilities = mutableMapOf<String, AbilityData>()

    fun load() {
        abilities.clear()

        val configDir = File(FabricLoader.getInstance().configDir.toFile(), "cde/abilities")
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
                    CDE.logger.error("Failed to load ability file: ${file.name}", e)
                }
            }
            CDE.logger.info("Loaded ${abilities.size} abilities.")
        }
    }

    private fun extractDefaultAbilities(configDir: File) {
        val modContainer = FabricLoader.getInstance().getModContainer("cde").orElse(null)
        if (modContainer == null) {
            CDE.logger.error("Could not find ModContainer for cde!")
            return
        }

        val abilitiesPath = modContainer.findPath("data/cde/abilities").orElse(null)
        if (abilitiesPath == null) {
            CDE.logger.error("Could not find data/cde/abilities in mod jar!")
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
                            CDE.logger.error("Failed to extract ability file: $fileName", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            CDE.logger.error("Error walking through default abilities", e)
        }
    }

    fun getAbility(id: String): AbilityData? {
        return abilities[id]
    }
}
