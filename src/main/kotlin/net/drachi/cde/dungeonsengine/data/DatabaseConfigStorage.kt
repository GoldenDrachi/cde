package net.drachi.cde.dungeonsengine.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.drachi.cde.CDE
import net.drachi.cde.dungeonsengine.database.DatabaseManager

class DatabaseConfigStorage : ConfigStorage {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    override fun loadAll(): Map<String, DungeonConfig> {
        val map = mutableMapOf<String, DungeonConfig>()
        try {
            val rawMap = DatabaseManager.loadAllDungeonConfigs()
            
            if (rawMap.isEmpty()) {
                val defaultCfg = DungeonConfig(id = "default")
                save(defaultCfg)
                map["default"] = defaultCfg
            } else {
                for ((id, data) in rawMap) {
                    try {
                        val config = json.decodeFromString<DungeonConfig>(data)
                        map[id] = config
                        CDE.logger.info("Loaded DB dungeon config: $id")
                    } catch (e: Exception) {
                        CDE.logger.error("Failed to parse DB config: $id", e)
                    }
                }
            }
        } catch (e: Exception) {
            CDE.logger.error("Failed to load configs from DB", e)
        }
        return map
    }

    override fun save(config: DungeonConfig) {
        val data = json.encodeToString(DungeonConfig.serializer(), config)
        DatabaseManager.saveDungeonConfig(config.id, data)
    }

    override fun delete(id: String) {
        DatabaseManager.deleteDungeonConfig(id)
    }
}
