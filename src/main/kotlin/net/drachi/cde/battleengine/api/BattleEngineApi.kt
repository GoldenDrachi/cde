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

        // Default Rule: The player's own Pokémon is always friendly to them
        if (caster is ServerPlayer && target is PokemonEntity && target.pokemon.getOwnerUUID() == caster.uuid) {
            return true
        }

        // Default Rule: A Pokémon cannot hit its own master
        if (caster is PokemonEntity && target is ServerPlayer && caster.pokemon.getOwnerUUID() == target.uuid) {
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
