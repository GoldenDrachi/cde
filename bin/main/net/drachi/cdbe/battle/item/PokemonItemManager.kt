package net.drachi.cdbe.battle.item

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*


import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.item.ItemStack
import java.util.UUID

object PokemonItemManager {
    
    // Tracks items that have been consumed and should not trigger until healed
    // Maps Pokemon UUID to a list of consumed item registry names
    private val consumedItems = mutableMapOf<UUID, MutableSet<String>>()
    
    // Tracks Choice item locks
    // Maps Pokemon UUID to the Move ID they are locked into
    private val choiceLocks = mutableMapOf<UUID, String>()
    
    // Tracks Metronome stack counts
    // Maps Pokemon UUID to a pair of (Move ID, Stack Count)
    private val metronomeStacks = mutableMapOf<UUID, Pair<String, Int>>()

    // Hooks for other mods to trigger when an item reset happens
    private val resetHooks = mutableListOf<(Pokemon) -> Unit>()

    /**
     * Registers a hook that will be called when a Pokemon's items are reset (e.g. by healing).
     */
    fun addResetHook(hook: (Pokemon) -> Unit) {
        resetHooks.add(hook)
    }

    /**
     * Triggers a reset of all consumed items for a given Pokemon.
     * This should be called when the Pokemon is healed (e.g., at a PC or Nurse Joy).
     */
    fun triggerItemReset(pokemon: Pokemon) {
        consumedItems.remove(pokemon.uuid)
        
        // Notify any registered hooks
        for (hook in resetHooks) {
            try {
                hook(pokemon)
            } catch (e: Exception) {
                // Prevent a bad hook from crashing the reset process
                e.printStackTrace()
            }
        }
    }

    /**
     * Clears states that should be reset upon switching out.
     */
    fun clearSwitchOutStates(pokemon: Pokemon) {
        choiceLocks.remove(pokemon.uuid)
        metronomeStacks.remove(pokemon.uuid)
    }

    /**
     * Marks a specific item as consumed for this Pokemon.
     */
    fun consumeItem(pokemon: Pokemon, itemId: String) {
        val consumed = consumedItems.getOrPut(pokemon.uuid) { mutableSetOf() }
        consumed.add(itemId)
    }

    /**
     * Checks if a specific item has been consumed by this Pokemon.
     */
    fun isItemConsumed(pokemon: Pokemon, itemId: String): Boolean {
        return consumedItems[pokemon.uuid]?.contains(itemId) ?: false
    }

    /**
     * Helper function to get the current active held item ID, factoring in consumption.
     * Returns null if the item is consumed or empty.
     */
    fun getActiveHeldItem(pokemon: Pokemon): String? {
        val itemStack = pokemon.heldItem()
        if (itemStack.isEmpty) return null
        
        val itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemStack.item).toString()
        if (isItemConsumed(pokemon, itemId)) {
            return null
        }
        
        return itemId
    }

    /**
     * Sets a Choice lock for the Pokemon.
     */
    fun setChoiceLock(pokemon: Pokemon, moveId: String) {
        choiceLocks[pokemon.uuid] = moveId
    }

    /**
     * Gets the current Choice lock for the Pokemon.
     */
    fun getChoiceLock(pokemon: Pokemon): String? {
        return choiceLocks[pokemon.uuid]
    }
    
    /**
     * Increments and returns the Metronome stack for a specific move.
     * If a different move is used, the stack resets.
     */
    fun incrementMetronomeStack(pokemon: Pokemon, moveId: String): Int {
        val current = metronomeStacks[pokemon.uuid]
        if (current != null && current.first == moveId) {
            val newStack = current.second + 1
            metronomeStacks[pokemon.uuid] = Pair(moveId, newStack)
            return newStack
        } else {
            metronomeStacks[pokemon.uuid] = Pair(moveId, 1)
            return 1
        }
    }
    
    /**
     * Gets the current Metronome stack for a move.
     */
    fun getMetronomeStack(pokemon: Pokemon, moveId: String): Int {
        val current = metronomeStacks[pokemon.uuid]
        if (current != null && current.first == moveId) {
            return current.second
        }
        return 0
    }

    fun getHeldItemString(pokemon: Pokemon): String {
        val stack = pokemon.heldItem()
        if (stack.isEmpty) return "none"
        val itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.item).toString()
        val displayName = stack.hoverName.string
        if (isItemConsumed(pokemon, itemId)) {
            return "consumed:$displayName"
        }
        return displayName
    }

    fun getChoiceLockedMove(pokemon: Pokemon): String? {
        return choiceLocks[pokemon.uuid]
    }
}
