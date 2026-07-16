package net.drachi.cde.battleengine.battle.attack

import net.drachi.cde.CDE

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonDeserializationContext
import java.lang.reflect.Type
import net.drachi.cde.battleengine.BattleEngineModule
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStreamReader
import java.nio.file.Files
import net.minecraft.resources.ResourceLocation

class RealTimeMoveDeserializer : JsonDeserializer<RealTimeMove> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): RealTimeMove {
        val obj = json.asJsonObject
        
        val cobblemonMoveId = obj.get("cobblemon_move_id").asString
        val cooldownTurns = if (obj.has("cooldown_turns")) obj.get("cooldown_turns").asFloat else 0f
        val isSoundMove = if (obj.has("is_sound_move")) obj.get("is_sound_move").asBoolean else false
        val hpCostPercent = if (obj.has("hp_cost_percent")) obj.get("hp_cost_percent").asFloat else 0f
        val hpCostFlat = if (obj.has("hp_cost_flat")) obj.get("hp_cost_flat").asInt else 0
        
        // If phases array exists, parse it directly
        if (obj.has("phases")) {
            val phasesJson = obj.getAsJsonArray("phases")
            val phasesList = mutableListOf<MovePhase>()
            for (phaseJson in phasesJson) {
                phasesList.add(context.deserialize(phaseJson, MovePhase::class.java))
            }
            return RealTimeMove(cobblemonMoveId, cooldownTurns, isSoundMove, hpCostPercent, hpCostFlat, phasesList)
        }
        
        // Backward Compatibility: Map root fields into a single MovePhase
        val phase = context.deserialize<MovePhase>(json, MovePhase::class.java)
        return RealTimeMove(cobblemonMoveId, cooldownTurns, isSoundMove, hpCostPercent, hpCostFlat, listOf(phase))
    }
}

object MoveRegistry {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(RealTimeMove::class.java, RealTimeMoveDeserializer())
        .setPrettyPrinting()
        .create()
    
    // Maps the native Cobblemon move ID (e.g. "cobblemon:scratch") to our real-time move data
    private val moves = mutableMapOf<ResourceLocation, RealTimeMove>()

    fun load() {
        moves.clear()
        
        val configDir = File(FabricLoader.getInstance().configDir.toFile(), "cde/moves")
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        
        // Always check and extract missing default moves
        extractDefaultMoves(configDir)

        val files = configDir.listFiles { _, name -> name.endsWith(".json") }
        if (files != null) {
            for (file in files) {
                try {
                    FileReader(file).use { reader ->
                        val move = gson.fromJson(reader, RealTimeMove::class.java)
                        moves[move.cobblemonMoveIdentifier] = move
                    }
                } catch (e: Exception) {
                    CDE.logger.error("Failed to parse real-time move JSON: ${file.name}", e)
                }
            }
        }
        CDE.logger.info("Loaded ${moves.size} real-time moves from config/cde/moves.")
    }

    private fun extractDefaultMoves(configDir: File) {
        val modContainer = FabricLoader.getInstance().getModContainer("cde").orElse(null)
        if (modContainer == null) {
            CDE.logger.error("Could not find ModContainer for cde!")
            return
        }

        val movesPath = modContainer.findPath("data/cde/moves").orElse(null)
        if (movesPath == null) {
            CDE.logger.error("Could not find data/cde/moves in mod jar!")
            return
        }

        try {
            Files.walk(movesPath).use { stream ->
                stream.filter { Files.isRegularFile(it) }.forEach { path ->
                    val fileName = path.fileName.toString()
                    val targetFile = if (fileName.endsWith(".md")) {
                        File(configDir.parentFile, fileName) // Put guide in config/cde/
                    } else {
                        File(configDir, fileName) // Put json in config/cde/moves/
                    }

                    if (!targetFile.exists()) {
                        try {
                            Files.copy(path, targetFile.toPath())
                        } catch (e: Exception) {
                            CDE.logger.error("Failed to extract file: $fileName", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            CDE.logger.error("Error walking through default moves", e)
        }
    }

    fun getMove(cobblemonId: String): RealTimeMove? {
        return moves[ResourceLocation.parse(cobblemonId)]
    }
}
