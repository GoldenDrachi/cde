package net.drachi.cde.battleengine.api

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.server.level.ServerPlayer

/**
 * Public API for the Cobblemon Dungeon Battle Engine.
 * Other mods can interface with this to inject custom rules into the real-time combat system.
 */
object BattleEngineApi {

    // A list of predicates that can determine if a target is considered "friendly" to the caster.
    private val friendlyCheckers = mutableListOf<(LivingEntity, LivingEntity) -> Boolean>()

    /**
     * Register a custom checker to define whether a specific entity should be considered friendly
     * to the caster. This is highly useful for Party/Guild mods.
     *
     * @param checker A lambda that takes the Caster (usually a ServerPlayer) and the Target (LivingEntity).
     *                Return true if they are friendly (and thus immune to non-friendly-fire attacks).
     */
    fun registerFriendlyChecker(checker: (LivingEntity, LivingEntity) -> Boolean) {
        friendlyCheckers.add(checker)
    }

    /**
     * Checks if a target entity is friendly to the caster.
     * By default, a player's own Pokémon are friendly to them, and players can't hurt themselves.
     */
    fun isFriendly(caster: LivingEntity, target: LivingEntity): Boolean {
        // Default Rule: You can't hit yourself
        if (caster == target) return true

        val pvpEnabled = net.drachi.cde.battleengine.config.BattleEngineConfigManager.config.isPvpEnabled

        // Extract "Owner UUID" for logic (Player's UUID, or Pokemon's Owner UUID)
        val casterOwner = if (caster is PokemonEntity) caster.pokemon.getOwnerUUID() else if (caster is ServerPlayer) caster.uuid else null
        val targetOwner = if (target is PokemonEntity) target.pokemon.getOwnerUUID() else if (target is ServerPlayer) target.uuid else null

        // Handle relationships between Players and Owned Pokemon
        if (casterOwner != null && targetOwner != null) {
            // Same owner: Player hitting own Pokemon, Pokemon hitting its own master, or Pokemon hitting another Pokemon owned by same master
            if (casterOwner == targetOwner) return true
            
            // Different owners: If PvP is disabled, they are universally friendly. 
            // If PvP is enabled, they are hostile by default (but might be saved by friendlyCheckers below)
            if (!pvpEnabled) return true
        }
        
        // Spawned Pokemon vs Spawned Pokemon (Wild vs Wild)
        if (caster is PokemonEntity && caster.pokemon.getOwnerUUID() == null && target is PokemonEntity && target.pokemon.getOwnerUUID() == null) {
            return true
        }

        // Check external mods' definitions (e.g. Parties, Guilds, Teams)
        for (checker in friendlyCheckers) {
            if (checker(caster, target)) {
                return true
            }
        }

        return false
    }

    /**
     * Set a persistent global weather for a specific dimension.
     * This weather will affect all entities in the dimension unconditionally.
     * If a localized weather domain is cast, it will temporarily override this default.
     * 
     * @param dimension The dimension to apply the weather to.
     * @param weatherId The ID of the weather to apply (must exist in WeatherRegistry).
     * @return true if the weather was found and applied, false otherwise.
     */
    fun setDimensionWeather(dimension: net.minecraft.resources.ResourceLocation, weatherId: String): Boolean {
        val domainData = net.drachi.cde.battleengine.battle.weather.WeatherRegistry.getWeather(weatherId) ?: return false
        net.drachi.cde.battleengine.battle.utility.DomainManager.setDefaultDimensionWeather(dimension, domainData)
        return true
    }

    /**
     * Clear the persistent global weather for a specific dimension.
     */
    fun clearDimensionWeather(dimension: net.minecraft.resources.ResourceLocation) {
        net.drachi.cde.battleengine.battle.utility.DomainManager.clearDefaultDimensionWeather(dimension)
    }
}
