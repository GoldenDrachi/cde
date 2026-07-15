package net.drachi.cdde.data

import kotlinx.serialization.Serializable

@Serializable
enum class StorageMode {
    JSON,
    DATABASE
}

@Serializable
data class GlobalConfig(
    // --- Storage Settings ---
    val _comment_storageMode: String = "How to store active dungeon instances and player unlocks. Options: 'JSON' (local files) or 'DATABASE' (SQL).",
    val storageMode: StorageMode = StorageMode.JSON,
    
    // --- Database Configuration ---
    val _comment_database: String = "Database configuration. Active dungeons, player unlocks and player eviction are always handled in the database. Dungeon configs are stored here only if storageMode is DATABASE. Database type: 'sqlite' (local file) or 'mariadb' (requires host credentials).",
    val databaseType: String = "sqlite",
    val databaseHost: String = "127.0.0.1",
    val databasePort: Int = 3306,
    val databaseName: String = "cdde",
    val databaseUser: String = "root",
    val databasePassword: String = "",

    // --- Lifecycle & Persistence Settings ---
    val _comment_serverId: String = "Unique identifier for this server instance (used for cross-server database tracking).",
    val serverId: String = "server-1",
    val _comment_abandonTimeoutMinutes: String = "How long (in minutes) a dungeon remains active after all players leave or disconnect.",
    val abandonTimeoutMinutes: Int = 15,
    
    // --- Combat & Gameplay Settings ---
    val _comment_floorStartFreezeTicks: String = "How long (in ticks, 20 ticks = 1s) to freeze all entities when a player enters a new floor.",
    val floorStartFreezeTicks: Int = 30
)
