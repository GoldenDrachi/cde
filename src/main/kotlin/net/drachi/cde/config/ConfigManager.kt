package net.drachi.cde.config

import net.drachi.cde.dungeonsengine.data.*
import net.drachi.cde.dungeonsengine.database.DatabaseManager

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.drachi.cde.CDE
import net.fabricmc.loader.api.FabricLoader
import java.io.File

object ConfigManager {
    lateinit var globalConfig: GlobalConfig
        private set

    lateinit var storage: ConfigStorage
        private set

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun init() {
        val configFile = File(FabricLoader.getInstance().configDir.resolve("cde").toFile(), "config.json")
        
        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            globalConfig = GlobalConfig()
            configFile.writeText(json.encodeToString(GlobalConfig.serializer(), globalConfig))
        } else {
            try {
                globalConfig = json.decodeFromString<GlobalConfig>(configFile.readText())
            } catch (e: Exception) {
                CDE.logger.error("Failed to parse config.json, using defaults.", e)
                globalConfig = GlobalConfig()
            }
        }

        // Initialize DatabaseManager with global config early, so DB storage can use it
        DatabaseManager.initialize(globalConfig)

        storage = if (globalConfig.storageMode == StorageMode.DATABASE) {
            DatabaseConfigStorage()
        } else {
            JsonConfigStorage()
        }
    }

    fun loadDungeonConfigs() {
        DungeonManager.configs.clear()
        DungeonManager.configs.putAll(storage.loadAll())
    }

    fun saveDungeonConfig(config: DungeonConfig) {
        DungeonManager.configs[config.id] = config
        storage.save(config)
    }

    fun deleteDungeonConfig(id: String) {
        DungeonManager.configs.remove(id)
        storage.delete(id)
    }

    fun exportToJSON(id: String): Boolean {
        val config = DungeonManager.configs[id] ?: return false
        val exportStorage = JsonConfigStorage()
        exportStorage.save(config)
        return true
    }

    fun importFromJSON(id: String): Boolean {
        val importStorage = JsonConfigStorage()
        val configs = importStorage.loadAll()
        val config = configs[id] ?: return false
        
        // Save to current storage
        saveDungeonConfig(config)
        return true
    }
}
