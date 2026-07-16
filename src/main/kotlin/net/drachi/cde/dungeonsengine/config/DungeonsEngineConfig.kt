package net.drachi.cde.dungeonsengine.config

import kotlinx.serialization.Serializable

@Serializable
data class DungeonsEngineConfig(
    val _comment_abandonTimeoutMinutes: String = "How long (in minutes) a dungeon remains active after all players leave or disconnect.",
    val abandonTimeoutMinutes: Int = 15,
    val _comment_floorStartFreezeTicks: String = "How long (in ticks, 20 ticks = 1s) to freeze all entities when a player enters a new floor.",
    val floorStartFreezeTicks: Int = 30
)