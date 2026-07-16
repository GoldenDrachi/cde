package net.drachi.cde.dungeonsengine.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.drachi.cde.CDE
import net.fabricmc.loader.api.FabricLoader
import java.io.File

class JsonConfigStorage : ConfigStorage {
    private val configDir = FabricLoader.getInstance().configDir.resolve("cde/dungeons").toFile()
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    init {
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
    }

    override fun loadAll(): Map<String, DungeonConfig> {
        val map = mutableMapOf<String, DungeonConfig>()
        
        // Generate default config if no configs exist
        val defaultFile = File(configDir, "default.json")
        if (!defaultFile.exists() && configDir.listFiles()?.isEmpty() == true) {
            val defaultCfg = DungeonConfig(id = "default")
            defaultFile.writeText(json.encodeToString(DungeonConfig.serializer(), defaultCfg))
        }

        configDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
            try {
                val content = file.readText()
                val config = json.decodeFromString<DungeonConfig>(content)
                map[config.id] = config
                // Re-save to format/add new fields
                file.writeText(json.encodeToString(DungeonConfig.serializer(), config))
                CDE.logger.info("Loaded JSON dungeon config: ${config.id}")
            } catch (e: Exception) {
                CDE.logger.error("Failed to load JSON config file: ${file.name}", e)
            }
        }
        return map
    }

    override fun save(config: DungeonConfig) {
        val file = File(configDir, "${config.id}.json")
        file.writeText(json.encodeToString(DungeonConfig.serializer(), config))
    }

    override fun delete(id: String) {
        val file = File(configDir, "$id.json")
        if (file.exists()) {
            file.delete()
        }
    }
}
