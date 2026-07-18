package net.drachi.cde.config

import kotlinx.serialization.Serializable

@Serializable
enum class StorageMode {
    JSON,
    DATABASE
}

@Serializable
data class ModuleConfig(
    val dungeonsEnabled: Boolean = true,
    val battleEngineEnabled: Boolean = true,
    val aiEnabled: Boolean = true
)

@Serializable
data class GlobalConfig(
    val modules: ModuleConfig = ModuleConfig(),

    // --- Storage Settings ---
    val _comment_storageMode: String = "How to store active dungeon instances and player unlocks. Options: 'JSON' (local files) or 'DATABASE' (SQL).",
    val storageMode: StorageMode = StorageMode.JSON,
    
    // --- Database Configuration ---
    val _comment_database: String = "Database configuration. Active dungeons, player unlocks and player eviction are always handled in the database. Dungeon configs are stored here only if storageMode is DATABASE. Database type: 'sqlite' (local file) or 'mariadb' (requires host credentials).",
    val databaseType: String = "sqlite",
    val databaseHost: String = "127.0.0.1",
    val databasePort: Int = 3306,
    val databaseName: String = "cde",
    val databaseUser: String = "root",
    val databasePassword: String = "",

    // --- Lifecycle & Persistence Settings ---
    val _comment_serverId: String = "Unique identifier for this server instance (used for cross-server database tracking).",
    val serverId: String = "server-1",

    // --- Developer Settings ---
    val _comment_debugLogging: String = "Toggles whether the server logs detailed mathematical calculations, item pickups, and engine traces.",
    val debugLogging: Boolean = false
)
