package net.drachi.cde.battleengine.battle.weather

import net.drachi.cde.CDE

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import net.drachi.cde.battleengine.BattleEngineModule
import net.drachi.cde.battleengine.battle.attack.DomainData
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.io.FileReader

object WeatherRegistry {
    private val weathers = mutableMapOf<String, DomainData>()

    // Using standard gson
    private val gson: Gson = GsonBuilder().create()

    fun load() {
        weathers.clear()
        
        val configDir = FabricLoader.getInstance().configDir.resolve("cde").resolve("weathers").toFile()
        if (!configDir.exists()) {
            configDir.mkdirs()
        }

        val files = configDir.listFiles { _, name -> name.endsWith(".json") }
        if (files != null) {
            for (file in files) {
                try {
                    FileReader(file).use { reader ->
                        val obj = gson.fromJson(reader, JsonObject::class.java)
                        val domainData = gson.fromJson(obj, DomainData::class.java)
                        val id = file.nameWithoutExtension
                        weathers[id] = domainData
                        CDE.logger.info("Loaded weather template: $id")
                    }
                } catch (e: Exception) {
                    CDE.logger.error("Failed to load weather from ${file.name}", e)
                }
            }
        }
    }

    fun getWeather(id: String): DomainData? {
        return weathers[id]
    }
}
