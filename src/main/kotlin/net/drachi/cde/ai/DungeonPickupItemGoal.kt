package net.drachi.cde.ai

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.drachi.cde.dungeonsengine.data.DungeonManager
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import java.util.*

class DungeonPickupItemGoal(private val pokemon: PokemonEntity) : Goal() {

    private var targetItem: ItemEntity? = null
    private var scanCooldown = 0

    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean {
        // Only run if the pokemon's main hand is empty
        if (!pokemon.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty) {
            return false
        }

        if (scanCooldown > 0) {
            scanCooldown--
            return false
        }
        
        scanCooldown = 20 // Scan every second
        
        // Find nearby items within 8 blocks
        val items = pokemon.level().getEntitiesOfClass(
            ItemEntity::class.java, 
            pokemon.boundingBox.inflate(8.0)
        ) { it.isAlive && !it.hasPickUpDelay() }

        if (items.isNotEmpty()) {
            targetItem = items.minByOrNull { it.distanceToSqr(pokemon) }
            return targetItem != null
        }
        
        return false
    }

    override fun canContinueToUse(): Boolean {
        return targetItem != null && targetItem!!.isAlive && pokemon.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty
    }

    override fun start() {
        if (targetItem != null) {
            pokemon.navigation.moveTo(targetItem!!, 1.2)
        }
    }

    override fun stop() {
        targetItem = null
        pokemon.navigation.stop()
    }

    override fun tick() {
        val item = targetItem ?: return
        
        pokemon.lookControl.setLookAt(item, 30.0f, 30.0f)
        
        // Check distance to item
        if (pokemon.distanceToSqr(item) <= 2.5) {
            val stack = item.item.copy()
            
            // Equip it
            pokemon.setItemSlot(EquipmentSlot.MAINHAND, stack)
            item.discard()
            
            // Send blue chat message to dungeon party
            if (pokemon.level() is net.minecraft.server.level.ServerLevel) {
                // We don't have getActiveDungeon(PokemonEntity), we can check ActiveDungeons
                val pos = pokemon.blockPosition()
                val instanceIndex = pos.z / 10000
                val expectedOriginZ = instanceIndex * 10000
                val dungeon = DungeonManager.activeDungeons.values.find { it.originZ == expectedOriginZ }
                
                if (dungeon != null) {
                    val message = Component.translatable(
                        "message.cde.pokemon_picked_up_item", 
                        pokemon.displayName, 
                        stack.displayName
                    ).withStyle(net.minecraft.ChatFormatting.BLUE)
                    
                    val partyMembers = net.drachi.cde.dungeonsengine.api.GroupAPI.getPartyMembersById(dungeon.instanceId) ?: emptyList()
                    val players = partyMembers.mapNotNull { pokemon.level().server?.playerList?.getPlayer(it) }
                    players.forEach { p ->
                        p.sendSystemMessage(message)
                    }
                }
            }
        } else if (pokemon.navigation.isDone) {
            pokemon.navigation.moveTo(item, 1.2)
        }
    }
}
