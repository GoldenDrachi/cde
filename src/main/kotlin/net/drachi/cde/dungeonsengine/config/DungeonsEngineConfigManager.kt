package net.drachi.cde.dungeonsengine.config

import net.drachi.cde.CDE
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.io.FileReader
import java.io.FileWriter

object DungeonsEngineConfigManager {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val configDir: File = File(FabricLoader.getInstance().configDir.toFile(), "cde")
    private val configFile: File = File(configDir, "dungeonsengine_config.json")
    
    var config: DungeonsEngineConfig = DungeonsEngineConfig()
        private set

    fun loadConfig() {
        if (configFile.exists()) {
            try {
                java.io.FileReader(configFile).use { reader ->
                    config = gson.fromJson(reader, DungeonsEngineConfig::class.java)
                }
                saveConfig()
                CDE.logger.info("Loaded dungeons engine config.")
            } catch (e: Exception) {
                CDE.logger.error("Failed to load config, using defaults.", e)
            }
        } else {
            saveConfig()
        }
    }

    fun saveConfig() {
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        try {
            FileWriter(configFile).use { writer ->
                gson.toJson(config, writer)
            }
            CDE.logger.info("Saved dungeons engine config.")
        } catch (e: Exception) {
            CDE.logger.error("Failed to save config.", e)
        }
    }
}