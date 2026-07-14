package net.drachi.cdde.data

interface ConfigStorage {
    fun loadAll(): Map<String, DungeonConfig>
    fun save(config: DungeonConfig)
    fun delete(id: String)
}
