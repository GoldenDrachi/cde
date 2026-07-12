package net.drachi.cdde.mechanics

import com.cobblemon.mod.common.pokemon.Pokemon

enum class HazardType {
    WATER,
    LAVA,
    VOID
}

object HazardMechanic {

    /**
     * Checks if a specific Pokemon is capable of traversing the given hazard type.
     */
    fun canTraverse(pokemon: Pokemon, hazardType: HazardType): Boolean {
        // Any Hazard: Flying type, Ghost type, or Levitate ability.
        val hasLevitate = pokemon.ability.name.lowercase() == "levitate"
        val hasFlying = pokemon.types.any { it.name.lowercase() == "flying" }
        val hasGhost = pokemon.types.any { it.name.lowercase() == "ghost" }

        if (hasLevitate || hasFlying || hasGhost) {
            return true
        }

        return when (hazardType) {
            HazardType.WATER -> {
                val hasWater = pokemon.types.any { it.name.lowercase() == "water" }
                val knowsSurf = pokemon.moveSet.getMoves().any { it.template.name.lowercase() == "surf" }
                hasWater || knowsSurf
            }
            HazardType.LAVA -> {
                pokemon.types.any { it.name.lowercase() == "fire" }
            }
            HazardType.VOID -> {
                false // Only Flying/Ghost/Levitate can cross Void
            }
        }
    }
}
