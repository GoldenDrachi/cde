package net.drachi.cde.dungeonsengine.data

interface ConfigStorage {
    fun loadAll(): Map<String, DungeonConfig>
    fun save(config: DungeonConfig)
    fun delete(id: String)
}
