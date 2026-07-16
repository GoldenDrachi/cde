package net.drachi.cde.battleengine.config

import net.drachi.cde.CDE

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.drachi.cde.battleengine.BattleEngineModule
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.io.FileReader
import java.io.FileWriter

object BattleEngineConfigManager {
    // We use pretty printing so the JSON is human-readable for server admins
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    
    private val configDir: File = File(FabricLoader.getInstance().configDir.toFile(), "cde")
    private val configFile: File = File(configDir, "battleengine_config.json")
    
    var config: BattleEngineConfig = BattleEngineConfig()
        private set

    fun loadConfig() {
        if (configFile.exists()) {
            try {
                java.io.FileReader(configFile).use { reader ->
                    config = gson.fromJson(reader, BattleEngineConfig::class.java)
                }
                // Save config immediately after loading to ensure any new fields
                // that were added in updates get written to the user's config file.
                saveConfig()
                CDE.logger.info("Loaded realtime battle config.")
            } catch (e: Exception) {
                CDE.logger.error("Failed to load config, using defaults.", e)
            }
        } else {
            // If it doesn't exist, generate one with the default values
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
            CDE.logger.info("Saved realtime battle config.")
        } catch (e: Exception) {
            CDE.logger.error("Failed to save config.", e)
        }
    }
}
