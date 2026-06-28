package net.drachi.cdbe.battle.utility

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*


import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.LivingEntity
import java.util.UUID
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerPlayer

object EntityUtil {
    
    /**
     * Finds a LivingEntity by UUID across all server levels.
     */
    fun findLivingEntityByUUID(server: MinecraftServer, uuid: UUID): LivingEntity? {
        for (level in server.allLevels) {
            val entity = level.getEntity(uuid) as? LivingEntity
            if (entity != null) return entity
        }
        return null
    }

    /**
     * Gets the types of the given entity (or the player's active pokemon).
     * Returns a list of lowercase type names.
     */
    fun getPokemonTypes(entity: LivingEntity): List<String> {
        if (entity is PokemonEntity) {
            val types = mutableListOf<String>()
            entity.pokemon.primaryType?.name?.lowercase()?.let { types.add(it) }
            entity.pokemon.secondaryType?.name?.lowercase()?.let { types.add(it) }
            return types
        } else if (entity is ServerPlayer) {
            val activeMon = PlayerCombatManager.getActivePokemon(entity)
            if (activeMon != null) {
                val types = mutableListOf<String>()
                activeMon.primaryType?.name?.lowercase()?.let { types.add(it) }
                activeMon.secondaryType?.name?.lowercase()?.let { types.add(it) }
                return types
            }
        }
        return emptyList()
    }
    
    /**
     * Checks if the entity has any of the given types.
     */
    fun hasMatchingType(entity: LivingEntity, types: List<String>): Boolean {
        if (types.isEmpty()) return false
        val entityTypes = getPokemonTypes(entity)
        return types.any { t -> entityTypes.contains(t.lowercase()) }
    }
    
    /**
     * Raycasts from the entity's eyes to find a block or entity hit result.
     */
    fun raycastEntityOrBlock(entity: LivingEntity, range: Double): net.minecraft.world.phys.HitResult {
        val start = entity.eyePosition
        val look = entity.lookAngle
        val end = start.add(look.x * range, look.y * range, look.z * range)
        
        // Raycast Blocks
        val blockHit = entity.level().clip(net.minecraft.world.level.ClipContext(
            start, end, 
            net.minecraft.world.level.ClipContext.Block.COLLIDER, 
            net.minecraft.world.level.ClipContext.Fluid.NONE, 
            entity
        ))
        
        // Raycast Entities
        var closestEntity: net.minecraft.world.phys.EntityHitResult? = null
        var minDistance = blockHit?.location?.distanceTo(start) ?: range
        
        val box = entity.boundingBox.expandTowards(look.scale(range)).inflate(1.0)
        for (other in entity.level().getEntities(entity, box) { it is LivingEntity && it.isPickable }) {
            val aabb = other.boundingBox.inflate(0.3)
            val hitOptional = aabb.clip(start, end)
            if (hitOptional.isPresent) {
                val hitPos = hitOptional.get()
                val dist = start.distanceTo(hitPos)
                if (dist < minDistance) {
                    minDistance = dist
                    closestEntity = net.minecraft.world.phys.EntityHitResult(other, hitPos)
                }
            }
        }
        
        return closestEntity ?: blockHit
    }
    
    fun isFriendly(caster: LivingEntity, target: LivingEntity): Boolean {
        if (target == caster) return true

        val casterOwner = if (caster is net.minecraft.world.entity.player.Player) caster.uuid 
                          else (caster as? com.cobblemon.mod.common.entity.pokemon.PokemonEntity)?.pokemon?.getOwnerUUID()
        
        val targetOwner = if (target is net.minecraft.world.entity.player.Player) target.uuid 
                          else (target as? com.cobblemon.mod.common.entity.pokemon.PokemonEntity)?.pokemon?.getOwnerUUID()

        return casterOwner != null && casterOwner == targetOwner
    }
}
