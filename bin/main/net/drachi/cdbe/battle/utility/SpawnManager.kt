package net.drachi.cdbe.battle.utility

import net.drachi.cdbe.config.ConfigManager

enum class HostilityState {
    HOSTILE,
    NEUTRAL,
    PEACEFUL
}

object SpawnManager {
    private val dimensionHostilityOverrides = mutableMapOf<String, HostilityState>()
    private val entityHostilityMap = java.util.WeakHashMap<net.minecraft.world.entity.LivingEntity, HostilityState>()

    fun setEntityHostility(entity: net.minecraft.world.entity.LivingEntity, state: HostilityState) {
        entityHostilityMap[entity] = state
    }
    
    fun getEntityHostility(entity: net.minecraft.world.entity.LivingEntity): HostilityState? {
        return entityHostilityMap[entity]
    }

    /**
     * Programmatically registers a specific dimension to always spawn Pokemon with a certain hostility state.
     * This acts as an API for dungeon systems or other mods to override the cdbe config.
     */
    fun registerDimensionHostility(dimensionId: String, state: HostilityState) {
        dimensionHostilityOverrides[dimensionId] = state
    }

    /**
     * Gets the expected HostilityState for a given dimension ID, checking runtime overrides first,
     * then checking the config. Defaults to NEUTRAL if neither specify.
     */
    fun getDimensionHostility(dimensionId: String): HostilityState {
        val override = dimensionHostilityOverrides[dimensionId]
        if (override != null) return override

        val configStr = ConfigManager.config.dimensionHostility[dimensionId]?.uppercase() ?: return HostilityState.NEUTRAL
        
        return try {
            HostilityState.valueOf(configStr)
        } catch (e: IllegalArgumentException) {
            HostilityState.NEUTRAL
        }
    }
}
