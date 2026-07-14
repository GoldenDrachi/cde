package net.drachi.cdde.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.drachi.cdde.CobblemonDungeonDungeonsEngine
import net.drachi.cdde.data.ActiveDungeon
import net.drachi.cdde.data.DungeonManager
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import java.io.File
import java.sql.Connection
import java.util.UUID

object DatabaseManager {
    private var dataSource: HikariDataSource? = null

    fun initialize(config: net.drachi.cdde.data.GlobalConfig) {
        val hikariConfig = HikariConfig()
        
        if (config.databaseType.equals("sqlite", ignoreCase = true)) {
            val dbFile = File(FabricLoader.getInstance().configDir.resolve("cdde").toFile(), "dungeons.db")
            hikariConfig.jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
            hikariConfig.driverClassName = "org.sqlite.JDBC"
            // SQLite specific optimizations
            hikariConfig.maximumPoolSize = 1
        } else {
            hikariConfig.jdbcUrl = "jdbc:mariadb://${config.databaseHost}:${config.databasePort}/${config.databaseName}"
            hikariConfig.username = config.databaseUser
            hikariConfig.password = config.databasePassword
            hikariConfig.driverClassName = "org.mariadb.jdbc.Driver"
            hikariConfig.maximumPoolSize = 10
        }

        try {
            dataSource = HikariDataSource(hikariConfig)
            createTables()
            CobblemonDungeonDungeonsEngine.logger.info("Successfully connected to ${config.databaseType} database.")
        } catch (e: Exception) {
            CobblemonDungeonDungeonsEngine.logger.error("Failed to connect to database!", e)
        }
    }

    private fun getConnection(): Connection? = dataSource?.connection

    private fun createTables() {
        val sqlDungeons = """
            CREATE TABLE IF NOT EXISTS active_dungeons (
                instance_id VARCHAR(36) PRIMARY KEY,
                server_id VARCHAR(100),
                origin_x INT,
                origin_z INT,
                current_floor INT,
                config_id VARCHAR(100),
                last_active_time BIGINT
            )
        """.trimIndent()

        val sqlPlayers = """
            CREATE TABLE IF NOT EXISTS dungeon_players (
                instance_id VARCHAR(36),
                player_uuid VARCHAR(36),
                return_x INT,
                return_y INT,
                return_z INT,
                PRIMARY KEY (instance_id, player_uuid)
            )
        """.trimIndent()

        val sqlEvicted = """
            CREATE TABLE IF NOT EXISTS evicted_players (
                player_uuid VARCHAR(36) PRIMARY KEY,
                return_x INT,
                return_y INT,
                return_z INT
            )
        """.trimIndent()

        val sqlConfigs = """
            CREATE TABLE IF NOT EXISTS dungeon_configs (
                config_id VARCHAR(100) PRIMARY KEY,
                config_data TEXT
            )
        """.trimIndent()

        getConnection()?.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(sqlDungeons)
                stmt.execute(sqlPlayers)
                stmt.execute(sqlEvicted)
                stmt.execute(sqlConfigs)
            }
        }
    }

    fun saveActiveDungeon(instance: ActiveDungeon, serverId: String) {
        val sql = """
            INSERT INTO active_dungeons (instance_id, server_id, origin_x, origin_z, current_floor, config_id, last_active_time)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
            current_floor = VALUES(current_floor),
            last_active_time = VALUES(last_active_time)
        """.trimIndent()
        
        val sqliteSql = """
            INSERT INTO active_dungeons (instance_id, server_id, origin_x, origin_z, current_floor, config_id, last_active_time)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(instance_id) DO UPDATE SET 
            current_floor = excluded.current_floor,
            last_active_time = excluded.last_active_time
        """.trimIndent()

        getConnection()?.use { conn ->
            val query = if (conn.metaData.databaseProductName.contains("SQLite", true)) sqliteSql else sql
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, instance.instanceId.toString())
                stmt.setString(2, serverId)
                stmt.setInt(3, instance.originX)
                stmt.setInt(4, instance.originZ)
                stmt.setInt(5, instance.currentFloor)
                stmt.setString(6, instance.config.id)
                stmt.setLong(7, instance.lastActiveTime)
                stmt.executeUpdate()
            }
        }
    }

    fun deleteActiveDungeon(instanceId: UUID) {
        val sqlDungeons = "DELETE FROM active_dungeons WHERE instance_id = ?"
        val sqlPlayers = "DELETE FROM dungeon_players WHERE instance_id = ?"
        
        getConnection()?.use { conn ->
            conn.prepareStatement(sqlDungeons).use { stmt ->
                stmt.setString(1, instanceId.toString())
                stmt.executeUpdate()
            }
            conn.prepareStatement(sqlPlayers).use { stmt ->
                stmt.setString(1, instanceId.toString())
                stmt.executeUpdate()
            }
        }
    }

    fun getMaxOriginZ(): Int {
        val sql = "SELECT MAX(origin_z) as max_z FROM active_dungeons"
        var maxZ = 0
        getConnection()?.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    maxZ = rs.getInt("max_z")
                }
            }
        }
        return maxZ
    }

    fun loadActiveDungeons(serverId: String): Map<UUID, ActiveDungeon> {
        val map = mutableMapOf<UUID, ActiveDungeon>()
        val sql = "SELECT * FROM active_dungeons WHERE server_id = ?"
        
        getConnection()?.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, serverId)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val instanceId = UUID.fromString(rs.getString("instance_id"))
                    val configId = rs.getString("config_id")
                    val config = DungeonManager.configs[configId]
                    if (config != null) {
                        val dungeon = ActiveDungeon(
                            instanceId = instanceId,
                            config = config,
                            originX = rs.getInt("origin_x"),
                            originZ = rs.getInt("origin_z"),
                            currentFloor = rs.getInt("current_floor")
                        )
                        dungeon.lastActiveTime = rs.getLong("last_active_time")
                        map[instanceId] = dungeon
                    } else {
                        CobblemonDungeonDungeonsEngine.logger.warn("Skipped loading dungeon $instanceId because config '$configId' is missing.")
                    }
                }
            }
        }
        
        // Load players for these dungeons
        val sqlPlayers = "SELECT * FROM dungeon_players WHERE instance_id = ?"
        getConnection()?.use { conn ->
            for (dungeon in map.values) {
                conn.prepareStatement(sqlPlayers).use { stmt ->
                    stmt.setString(1, dungeon.instanceId.toString())
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        val playerUuid = UUID.fromString(rs.getString("player_uuid"))
                        val pos = BlockPos(rs.getInt("return_x"), rs.getInt("return_y"), rs.getInt("return_z"))
                        dungeon.returnLocations[playerUuid] = pos
                    }
                }
            }
        }
        
        return map
    }

    fun saveDungeonPlayer(instanceId: UUID, playerUuid: UUID, returnPos: BlockPos) {
        val sql = """
            INSERT INTO dungeon_players (instance_id, player_uuid, return_x, return_y, return_z)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
            return_x = VALUES(return_x), return_y = VALUES(return_y), return_z = VALUES(return_z)
        """.trimIndent()
        
        val sqliteSql = """
            INSERT INTO dungeon_players (instance_id, player_uuid, return_x, return_y, return_z)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(instance_id, player_uuid) DO UPDATE SET 
            return_x = excluded.return_x, return_y = excluded.return_y, return_z = excluded.return_z
        """.trimIndent()
        
        getConnection()?.use { conn ->
            val query = if (conn.metaData.databaseProductName.contains("SQLite", true)) sqliteSql else sql
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, instanceId.toString())
                stmt.setString(2, playerUuid.toString())
                stmt.setInt(3, returnPos.x)
                stmt.setInt(4, returnPos.y)
                stmt.setInt(5, returnPos.z)
                stmt.executeUpdate()
            }
        }
    }

    fun removeDungeonPlayer(instanceId: UUID, playerUuid: UUID) {
        val sql = "DELETE FROM dungeon_players WHERE instance_id = ? AND player_uuid = ?"
        getConnection()?.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, instanceId.toString())
                stmt.setString(2, playerUuid.toString())
                stmt.executeUpdate()
            }
        }
    }

    data class PlayerPos(val uuid: String, val x: Int, val y: Int, val z: Int)

    fun evictPlayersFromDungeon(instanceId: UUID) {
        val sqlSelect = "SELECT * FROM dungeon_players WHERE instance_id = ?"
        val sqlInsert = """
            INSERT INTO evicted_players (player_uuid, return_x, return_y, return_z)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
            return_x = VALUES(return_x), return_y = VALUES(return_y), return_z = VALUES(return_z)
        """.trimIndent()
        val sqliteInsert = """
            INSERT INTO evicted_players (player_uuid, return_x, return_y, return_z)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET 
            return_x = excluded.return_x, return_y = excluded.return_y, return_z = excluded.return_z
        """.trimIndent()

        getConnection()?.use { conn ->
            val query = if (conn.metaData.databaseProductName.contains("SQLite", true)) sqliteInsert else sqlInsert
            val toEvict = mutableListOf<PlayerPos>()
            
            conn.prepareStatement(sqlSelect).use { stmt ->
                stmt.setString(1, instanceId.toString())
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    toEvict.add(PlayerPos(rs.getString("player_uuid"), rs.getInt("return_x"), rs.getInt("return_y"), rs.getInt("return_z")))
                }
            }
            
            for (player in toEvict) {
                conn.prepareStatement(query).use { stmt ->
                    stmt.setString(1, player.uuid)
                    stmt.setInt(2, player.x)
                    stmt.setInt(3, player.y)
                    stmt.setInt(4, player.z)
                    stmt.executeUpdate()
                }
            }
        }
    }

    fun getAndRemoveEvictedPlayer(playerUuid: UUID): BlockPos? {
        val sqlSelect = "SELECT * FROM evicted_players WHERE player_uuid = ?"
        val sqlDelete = "DELETE FROM evicted_players WHERE player_uuid = ?"
        var pos: BlockPos? = null
        
        getConnection()?.use { conn ->
            conn.prepareStatement(sqlSelect).use { stmt ->
                stmt.setString(1, playerUuid.toString())
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    pos = BlockPos(rs.getInt("return_x"), rs.getInt("return_y"), rs.getInt("return_z"))
                }
            }
            if (pos != null) {
                conn.prepareStatement(sqlDelete).use { stmt ->
                    stmt.setString(1, playerUuid.toString())
                    stmt.executeUpdate()
                }
            }
        }
        return pos
    }

    fun loadAllDungeonConfigs(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val sql = "SELECT * FROM dungeon_configs"
        getConnection()?.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    map[rs.getString("config_id")] = rs.getString("config_data")
                }
            }
        }
        return map
    }

    fun saveDungeonConfig(configId: String, configData: String) {
        val sql = """
            INSERT INTO dungeon_configs (config_id, config_data)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE 
            config_data = VALUES(config_data)
        """.trimIndent()

        val sqliteSql = """
            INSERT INTO dungeon_configs (config_id, config_data)
            VALUES (?, ?)
            ON CONFLICT(config_id) DO UPDATE SET 
            config_data = excluded.config_data
        """.trimIndent()

        getConnection()?.use { conn ->
            val query = if (conn.metaData.databaseProductName.contains("SQLite", true)) sqliteSql else sql
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, configId)
                stmt.setString(2, configData)
                stmt.executeUpdate()
            }
        }
    }

    fun deleteDungeonConfig(configId: String) {
        val sql = "DELETE FROM dungeon_configs WHERE config_id = ?"
        getConnection()?.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, configId)
                stmt.executeUpdate()
            }
        }
    }
}
