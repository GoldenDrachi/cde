package net.drachi.cdde.data

import kotlinx.serialization.Serializable

@Serializable
data class DungeonConfig(
    val id: String,
    val minRoomsPerFloor: Int = 5,
    val maxRoomsPerFloor: Int = 10,
    val finalFloor: Int = 5,
    /** 0 = keep all dead ends, 100 = prune all dead ends. Default 20 for Mystery Dungeon-style layouts. */
    val deadEndPrunePercent: Int = 20,
    val spawnTables: Map<Int, String> = emptyMap(),
    val lootTables: Map<Int, String> = emptyMap(),
    val basePalettes: List<String> = emptyList(), // block ids like "minecraft:stone_bricks"
    val hazards: List<String> = emptyList(),      // block ids like "minecraft:water"
    val activeSets: List<String> = emptyList(),    // e.g. ["water", "fire"] to load water_room_*, fire_room_*
    val stairDirection: StairDirection = StairDirection.DOWN,
    val stairBaseBlock: String = "minecraft:stone_bricks",
    val stairStepBlock: String = "minecraft:stone_brick_stairs",
    val generateHazardSeas: Boolean = false,
    val biome: String = "minecraft:the_void",

    // --- Lifecycle & Persistence Settings ---
    val _comment_serverId: String = "Unique identifier for this server instance (used for cross-server database tracking).",
    val serverId: String = "server-1",
    val _comment_abandonTimeoutMinutes: String = "How long (in minutes) a dungeon remains active after all players leave or disconnect.",
    val abandonTimeoutMinutes: Int = 15,

    // --- Database Configuration ---
    val _comment_database: String = "Database type: 'sqlite' or 'mariadb'. SQLite creates a local file. MariaDB requires host credentials.",
    val databaseType: String = "sqlite",
    val databaseHost: String = "127.0.0.1",
    val databasePort: Int = 3306,
    val databaseName: String = "cdde",
    val databaseUser: String = "root",
    val databasePassword: String = ""
)

@Serializable
enum class StairDirection {
    UP, DOWN
}
