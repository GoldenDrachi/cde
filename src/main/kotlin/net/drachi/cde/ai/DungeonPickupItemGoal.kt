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

    private fun getNormalizedSpeed(): Double {
        val baseSpeed = pokemon.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
        val safeBaseSpeed = maxOf(baseSpeed, 0.1)
        val targetSpeed = 0.3 // 1.2 equivalent for typical mob
        return targetSpeed / safeBaseSpeed
    }

    override fun start() {
        if (targetItem != null) {
            pokemon.navigation.moveTo(targetItem!!, getNormalizedSpeed())
            pokemon.brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
        }
    }

    override fun stop() {
        targetItem = null
        pokemon.navigation.stop()
    }

    override fun tick() {
        pokemon.brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
        val item = targetItem ?: return
        
        if (!item.isAlive || !pokemon.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty) {
            targetItem = null
            return
        }

        pokemon.lookControl.setLookAt(item, 30.0f, 30.0f)
        
        val distSq = pokemon.distanceToSqr(item)
        if (distSq <= 2.0) { // Pickup range
            val stack = item.item.copy()
            
            // Equip it
            pokemon.setItemSlot(EquipmentSlot.MAINHAND, stack)
            item.discard()
            targetItem = null
            
            // If owned, notify owner
            val ownerId = pokemon.pokemon.getOwnerUUID()
            if (ownerId != null) {
                val p = pokemon.level().getPlayerByUUID(ownerId)
                if (p != null && p is net.minecraft.server.level.ServerPlayer) {
                    val message = net.minecraft.network.chat.Component.literal("Your ")
                        .append(pokemon.name)
                        .append(" picked up ")
                        .append(stack.hoverName)
                        .withStyle(net.minecraft.ChatFormatting.YELLOW)
                    p.sendSystemMessage(message)
                }
            }
            
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
