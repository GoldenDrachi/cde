package net.drachi.cdbe.battle.utility

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*


import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.AABB
import net.minecraft.world.entity.LivingEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.MinecraftServer
import net.minecraft.resources.ResourceLocation
import java.util.UUID
import net.drachi.cdbe.util.ParticleUtil

object HazardManager {
    
    data class ActiveHazard(
        val pos: Vec3,
        val dimension: ResourceLocation,
        val data: HazardData,
        val casterUuid: UUID,
        val hitEntities: MutableSet<UUID>,
        var expirationTick: Long,
        var remainingTriggers: Int,
        val particleType: String,
        val durationSound: String? = null,
        var currentTick: Long = 0,
        val hitsFriendlies: Boolean = false,
        var stacks: Int = 1
    )

    private val activeHazards = mutableListOf<ActiveHazard>()
    fun getActiveHazards(): List<ActiveHazard> = activeHazards.toList()

    fun placeHazard(caster: LivingEntity, targetPos: Vec3, hazardData: HazardData, particleType: String, durationSound: String? = null, hitsFriendlies: Boolean = false) {
        val level = caster.level()
        if (level !is ServerLevel) return
        
        val durationTurns = hazardData.durationTurns
        val maxTicks = if (durationTurns > 0) {
            (durationTurns * net.drachi.cdbe.config.ConfigManager.config.turnToSecondsRatio * 20).toLong()
        } else if (durationTurns < 0) {
            Long.MAX_VALUE // Infinite
        } else {
            (100 * 20).toLong() // 100 turns max default if 0
        }
        
        // Ensure default is stealth rock as requested
        val finalParticle = if (particleType.isBlank() || particleType == "magic") "cobblemon:stealth_rock" else particleType
        
        // Stacking Logic
        val existingHazard = activeHazards.find { 
            it.dimension == level.dimension().location() && 
            it.data.hazardType == hazardData.hazardType &&
            it.casterUuid == caster.uuid &&
            it.pos.distanceToSqr(targetPos) < 25.0 // Merge if within 5 blocks
        }
        
        if (existingHazard != null) {
            if (existingHazard.stacks < hazardData.maxStacks) {
                existingHazard.stacks++
                // Reset duration when stacking
                existingHazard.expirationTick = if (maxTicks == Long.MAX_VALUE) Long.MAX_VALUE else level.gameTime + maxTicks
            }
            return
        }
        
        val hazard = ActiveHazard(
            pos = targetPos,
            dimension = level.dimension().location(),
            data = hazardData,
            casterUuid = caster.uuid,
            hitEntities = mutableSetOf(),
            expirationTick = if (maxTicks == Long.MAX_VALUE) Long.MAX_VALUE else level.gameTime + maxTicks,
            remainingTriggers = hazardData.maxTriggers,
            particleType = finalParticle,
            durationSound = durationSound,
            currentTick = 0,
            hitsFriendlies = hitsFriendlies,
            stacks = 1
        )
        
        activeHazards.add(hazard)
        
        println("HazardManager: Placed hazard ${hazardData.hazardType} at $targetPos in ${hazard.dimension}. expirationTick=${hazard.expirationTick}, levelGameTime=${level.gameTime}")
    }
    
    fun tick(server: MinecraftServer) {
        val iterator = activeHazards.iterator()
        while (iterator.hasNext()) {
            val hazard = iterator.next()
            val level = server.allLevels.find { it.dimension().location() == hazard.dimension }
            
            if (level == null) {
                println("HazardManager: Removed hazard because level is null! Dimension: ${hazard.dimension}")
                iterator.remove()
                continue
            }
            if (level.gameTime > hazard.expirationTick) {
                println("HazardManager: Removed hazard because it expired! gameTime=${level.gameTime} > ${hazard.expirationTick}")
                iterator.remove()
                continue
            }
            if (hazard.data.maxTriggers > 0 && hazard.remainingTriggers <= 0) {
                println("HazardManager: Removed hazard because triggers reached 0!")
                iterator.remove()
                continue
            }
            
            hazard.currentTick++
            
            // Particles
            if (hazard.currentTick % 5 == 0L) {
                spawnHazardParticles(level, hazard)
            }
            
            // Sound
            if (hazard.currentTick % 40 == 0L && hazard.durationSound != null) {
                val res = ResourceLocation.tryParse(hazard.durationSound)
                if (res != null) {
                    level.playSound(null, hazard.pos.x, hazard.pos.y, hazard.pos.z, net.minecraft.sounds.SoundEvent.createVariableRangeEvent(res), net.minecraft.sounds.SoundSource.HOSTILE, 0.5f, 1.0f)
                }
            }
            
            // Process effects
            if (processTriggers(level, hazard)) {
                println("HazardManager: Removed hazard because processTriggers returned true (vanished)!")
                iterator.remove()
            }
        }
    }
    
    private fun spawnHazardParticles(level: ServerLevel, hazard: ActiveHazard) {
        val radius = hazard.data.ringRadius.toDouble()
        val numParticles = (radius * 4).toInt().coerceAtLeast(8)
        for (i in 0 until numParticles) {
            val angle = (i * Math.PI * 2) / numParticles + (hazard.currentTick * 0.05)
            val px = hazard.pos.x + Math.cos(angle) * radius
            val py = hazard.pos.y + 0.5
            val pz = hazard.pos.z + Math.sin(angle) * radius
            ParticleUtil.spawnParticle(level, hazard.particleType, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }
    
    // Returns true if the hazard should be destroyed
    private fun processTriggers(level: ServerLevel, hazard: ActiveHazard): Boolean {
        val radius = hazard.data.ringRadius.toDouble()
        val box = AABB(
            hazard.pos.x - radius, hazard.pos.y - 2.0, hazard.pos.z - radius,
            hazard.pos.x + radius, hazard.pos.y + 4.0, hazard.pos.z + radius
        )

        val affectedEntities = level.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive }

        val affectedIds = affectedEntities.map { it.uuid }.toSet()
        hazard.hitEntities.removeIf { it !in affectedIds }

        for (entity in affectedEntities) {
            if (entity.uuid == hazard.casterUuid) continue
            if (hazard.hitEntities.contains(entity.uuid)) continue
            
            val casterEntity = level.getEntity(hazard.casterUuid) as? LivingEntity
            if (casterEntity != null && !hazard.hitsFriendlies && EntityUtil.isFriendly(casterEntity, entity)) continue

            val pokemonTypes = EntityUtil.getPokemonTypes(entity)

            if (shouldVanish(hazard.data, pokemonTypes)) {
                return true
            }

            if (isImmune(hazard.data, pokemonTypes)) continue

            applyHazardEffects(level, hazard, entity, pokemonTypes)
            
            hazard.hitEntities.add(entity.uuid)
            if (hazard.remainingTriggers > 0) {
                hazard.remainingTriggers--
            }
            
            if (hazard.remainingTriggers == 0) return true
        }
        
        return false
    }

    private fun shouldVanish(data: HazardData, pokemonTypes: List<String>): Boolean {
        val vanishType = data.vanishesWhenTouchedByType ?: return false
        return pokemonTypes.contains(vanishType.lowercase())
    }

    private fun isImmune(data: HazardData, pokemonTypes: List<String>): Boolean {
        if (data.affectsFlying) return false
        return pokemonTypes.contains("flying")
    }

    private fun applyHazardEffects(level: ServerLevel, hazard: ActiveHazard, entity: LivingEntity, pokemonTypes: List<String>) {
        val damageMultiplier = calculateDamageMultiplier(hazard.data, pokemonTypes)
        if (damageMultiplier > 0.0f && hazard.data.damagePercent > 0.0f) {
            val baseDamagePercent = hazard.data.damagePercent / 100.0f
            val damage = entity.maxHealth * baseDamagePercent * damageMultiplier
            if (damage > 0) {
                entity.hurt(level.damageSources().magic(), damage)
            }
        }
        
        val stackSpecificEffect = hazard.data.stackStatusEffects?.get(hazard.stacks.toString())
        val effectData = stackSpecificEffect ?: hazard.data.statusEffect
        
        if (effectData != null) {
            val finalEffectData = effectData
            if (kotlin.random.Random.nextInt(100) < finalEffectData.chance) {
                val casterEntity = level.getEntity(hazard.casterUuid) as? net.minecraft.server.level.ServerPlayer
                StatusEffectHandler.applyToTarget(entity, finalEffectData, casterEntity, true)
            }
        }

        // Hit particles
        val effectType = hazard.data.statusEffect?.type
        val pType = if (effectType == "poison") "composter" else "crit"
        ParticleUtil.spawnEntityParticle(level, pType, entity)

        if (net.drachi.cdbe.config.ConfigManager.config.debugLogging) {
            net.drachi.cdbe.CobblemonDungeonBattleEngine.LOGGER.info("Virtual Hazard triggered on ${entity.uuid} with multiplier $damageMultiplier")
        }
    }
    
    private fun calculateDamageMultiplier(data: HazardData, pokemonTypes: List<String>): Float {
        if (data.damageType == null || pokemonTypes.isEmpty()) return 1.0f
        val primary = pokemonTypes.getOrNull(0) ?: "normal"
        val secondary = pokemonTypes.getOrNull(1)
        return TypeChart.getMultiplier(data.damageType.lowercase(), primary, secondary)
    }
}
