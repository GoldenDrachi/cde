package net.drachi.cdde.data

import kotlinx.serialization.Serializable

@Serializable
enum class EndFloorType {
    NORMAL, TREASURE, BOSS
}

@Serializable
data class PokemonSpawnEntry(
    var pokemon: String = "cobblemon:pikachu",
    var weight: Int = 10,
    var minLevel: Int = 1,
    var maxLevel: Int = 50,
    var baseRecruitment: Int = 0
)

@Serializable
data class ItemSpawnEntry(
    var item: String = "minecraft:apple",
    var weight: Int = 10,
    var minAmount: Int = 1,
    var maxAmount: Int = 1
)

@Serializable
data class FloorConfig(
    var minRooms: Int = 5,
    var maxRooms: Int = 10,
    var deadEndPrunePercent: Int = 20,
    var pokemonSpawns: MutableList<PokemonSpawnEntry> = mutableListOf(),
    var itemSpawns: MutableList<ItemSpawnEntry> = mutableListOf(),
    var paletteA: String = "minecraft:stone_bricks",
    var paletteB: String = "minecraft:cracked_stone_bricks",
    var paletteC: String = "minecraft:mossy_stone_bricks",
    var paletteD: String = "minecraft:chiseled_stone_bricks",
    var hazards: MutableList<String> = mutableListOf(),      
    var activeSets: MutableList<String> = mutableListOf(),   
)

@Serializable
data class EndFloorConfig(
    var theme: String = "",
    var treasure: MutableList<ItemSpawnEntry> = mutableListOf(),
    var boss: MutableList<PokemonSpawnEntry> = mutableListOf(),
    var minion: MutableList<PokemonSpawnEntry> = mutableListOf()
)

@Serializable
data class FloorRule(
    var range: String = "1",
    var config: FloorConfig = FloorConfig()
)

@Serializable
data class DungeonConfig(
    val id: String,
    var portalColor: Int = 0x800080,
    var amountOfFloors: Int = 5,
    var endFloorType: EndFloorType = EndFloorType.NORMAL,
    
    var floorRules: MutableList<FloorRule> = mutableListOf(),
    var endFloorConfig: EndFloorConfig = EndFloorConfig(),
    
    // Global properties
    var stairDirection: StairDirection = StairDirection.DOWN,
    var stairBaseBlock: String = "minecraft:stone_bricks",
    var stairStepBlock: String = "minecraft:stone_brick_stairs",
    var generateHazardSeas: Boolean = false,
    var biome: String = "minecraft:the_void",
) {
    fun getFloorConfig(floor: Int): FloorConfig {
        // Iterate backwards so that later rules override earlier ones
        for (rule in floorRules.reversed()) {
            if (matchesRange(rule.range, floor)) {
                return rule.config
            }
        }
        return FloorConfig()
    }

    private fun matchesRange(rangeStr: String, floor: Int): Boolean {
        if (rangeStr.trim() == "*") return true
        val parts = rangeStr.split(",")
        for (part in parts) {
            val p = part.trim()
            if (p.contains("-")) {
                val bounds = p.split("-")
                if (bounds.size == 2) {
                    val min = bounds[0].toIntOrNull() ?: continue
                    val max = bounds[1].toIntOrNull() ?: continue
                    if (floor in min..max) return true
                }
            } else {
                val exact = p.toIntOrNull()
                if (exact != null && exact == floor) return true
            }
        }
        return false
    }
}

@Serializable
enum class StairDirection {
    UP, DOWN
}
