package net.drachi.cde.battleengine.battle.utility

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


object TypeChart {

    // Simple Type Effectiveness Matrix for the POC
    // Maps Attack Type -> (Defending Type -> Multiplier)
    private val chart = mapOf(
        "normal" to mapOf("rock" to 0.5f, "ghost" to 0.0f, "steel" to 0.5f),
        "fire" to mapOf("fire" to 0.5f, "water" to 0.5f, "grass" to 2.0f, "ice" to 2.0f, "bug" to 2.0f, "rock" to 0.5f, "dragon" to 0.5f, "steel" to 2.0f),
        "water" to mapOf("fire" to 2.0f, "water" to 0.5f, "grass" to 0.5f, "ground" to 2.0f, "rock" to 2.0f, "dragon" to 0.5f),
        "grass" to mapOf("fire" to 0.5f, "water" to 2.0f, "grass" to 0.5f, "poison" to 0.5f, "ground" to 2.0f, "flying" to 0.5f, "bug" to 0.5f, "rock" to 2.0f, "dragon" to 0.5f, "steel" to 0.5f),
        "electric" to mapOf("water" to 2.0f, "grass" to 0.5f, "electric" to 0.5f, "ground" to 0.0f, "flying" to 2.0f, "dragon" to 0.5f),
        "ice" to mapOf("fire" to 0.5f, "water" to 0.5f, "grass" to 2.0f, "ice" to 0.5f, "ground" to 2.0f, "flying" to 2.0f, "dragon" to 2.0f, "steel" to 0.5f),
        "fighting" to mapOf("normal" to 2.0f, "ice" to 2.0f, "poison" to 0.5f, "flying" to 0.5f, "psychic" to 0.5f, "bug" to 0.5f, "rock" to 2.0f, "ghost" to 0.0f, "dark" to 2.0f, "steel" to 2.0f, "fairy" to 0.5f),
        "poison" to mapOf("grass" to 2.0f, "poison" to 0.5f, "ground" to 0.5f, "rock" to 0.5f, "ghost" to 0.5f, "steel" to 0.0f, "fairy" to 2.0f),
        "ground" to mapOf("fire" to 2.0f, "electric" to 2.0f, "grass" to 0.5f, "poison" to 2.0f, "flying" to 0.0f, "bug" to 0.5f, "rock" to 2.0f, "steel" to 2.0f),
        "flying" to mapOf("electric" to 0.5f, "grass" to 2.0f, "fighting" to 2.0f, "bug" to 2.0f, "rock" to 0.5f, "steel" to 0.5f),
        "psychic" to mapOf("fighting" to 2.0f, "poison" to 2.0f, "psychic" to 0.5f, "dark" to 0.0f, "steel" to 0.5f),
        "bug" to mapOf("fire" to 0.5f, "grass" to 2.0f, "fighting" to 0.5f, "poison" to 0.5f, "flying" to 0.5f, "psychic" to 2.0f, "ghost" to 0.5f, "dark" to 2.0f, "steel" to 0.5f, "fairy" to 0.5f),
        "rock" to mapOf("fire" to 2.0f, "ice" to 2.0f, "fighting" to 0.5f, "ground" to 0.5f, "flying" to 2.0f, "bug" to 2.0f, "steel" to 0.5f),
        "ghost" to mapOf("normal" to 0.0f, "psychic" to 2.0f, "ghost" to 2.0f, "dark" to 0.5f),
        "dragon" to mapOf("dragon" to 2.0f, "steel" to 0.5f, "fairy" to 0.0f),
        "dark" to mapOf("fighting" to 0.5f, "psychic" to 2.0f, "ghost" to 2.0f, "dark" to 0.5f, "fairy" to 0.5f),
        "steel" to mapOf("fire" to 0.5f, "water" to 0.5f, "electric" to 0.5f, "ice" to 2.0f, "rock" to 2.0f, "steel" to 0.5f, "fairy" to 2.0f),
        "fairy" to mapOf("fire" to 0.5f, "fighting" to 2.0f, "poison" to 0.5f, "dragon" to 2.0f, "dark" to 2.0f, "steel" to 0.5f)
    )

    fun getMultiplier(attackType: String, defendType1: String, defendType2: String?): Float {
        val atk = attackType.lowercase()
        val def1 = defendType1.lowercase()
        
        var mult = chart[atk]?.get(def1) ?: 1.0f
        
        if (defendType2 != null) {
            val def2 = defendType2.lowercase()
            mult *= chart[atk]?.get(def2) ?: 1.0f
        }
        
        return mult
    }
}
