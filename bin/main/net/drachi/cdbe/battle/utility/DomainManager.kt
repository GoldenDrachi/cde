package net.drachi.cdbe.battle.utility

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*


import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.world.phys.Vec3
import net.minecraft.world.entity.LivingEntity
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.AABB
import net.minecraft.network.chat.Component
import net.minecraft.core.particles.ParticleOptions
import java.util.UUID
import net.drachi.cdbe.util.ParticleUtil

object DomainManager {

    data class ActiveDomain(
        val casterId: UUID,
        var lastKnownDimension: ResourceLocation,
        var lastKnownPos: Vec3,
        val data: DomainData,
        var expirationTimeMs: Long,
        val durationSound: String? = null
    )

    // Only one weather domain active at a time per dimension (or globally if we want simpler logic for now)
    // To support overlapping/replacing, we'll store a single global weather for now, or per-dimension.
    // Let's do per-dimension to be safe for multiplayer.
    private val activeWeatherDomains = mutableMapOf<ResourceLocation, ActiveDomain>()
    
    // Screens can overlap, so we map the Target UUID to a list of their active screens
    private val activeScreens = mutableMapOf<UUID, MutableList<ActiveDomain>>()

    fun castDomain(caster: LivingEntity, domainData: DomainData, durationSound: String? = null) {
        val durationMs = (domainData.durationTurns * net.drachi.cdbe.config.ConfigManager.config.turnToSecondsRatio * 1000).toLong()
        val expiration = System.currentTimeMillis() + durationMs
        
        val newDomain = ActiveDomain(
            casterId = caster.uuid,
            lastKnownDimension = caster.level().dimension().location(),
            lastKnownPos = caster.position(),
            data = domainData,
            expirationTimeMs = expiration,
            durationSound = durationSound
        )

        if (domainData.type == "weather") {
            // Overwrite existing weather in this dimension
            activeWeatherDomains[caster.level().dimension().location()] = newDomain
            if (net.drachi.cdbe.config.ConfigManager.config.debugLogging) {
                net.drachi.cdbe.CobblemonDungeonBattleEngine.LOGGER.info("Weather ${domainData.weatherCondition} cast in ${caster.level().dimension().location()} by ${caster.uuid}")
            }
        } else {
            // Screen
            val list = activeScreens.getOrPut(caster.uuid) { mutableListOf() }
            list.add(newDomain)
        }
    }

    fun tick(server: MinecraftServer) {
        val currentTime = System.currentTimeMillis()

        // --- 1. Tick Weather Domains ---
        val expiredWeathers = mutableListOf<ResourceLocation>()
        
        for ((dimensionKey, weather) in activeWeatherDomains) {
            if (currentTime > weather.expirationTimeMs) {
                expiredWeathers.add(dimensionKey)
                continue
            }

            // Find the level
            var level: ServerLevel? = null
            for (sl in server.allLevels) {
                if (sl.dimension().location() == dimensionKey) {
                    level = sl
                    break
                }
            }
            if (level == null) continue

            // Update position if caster is still around
            val caster = EntityUtil.findLivingEntityByUUID(server, weather.casterId)
            if (caster != null && caster.isAlive) {
                weather.lastKnownPos = caster.position()
            }

            // Get entities within radius
            val radius = weather.data.radius.toDouble()
            val box = AABB(
                weather.lastKnownPos.x - radius, weather.lastKnownPos.y - radius, weather.lastKnownPos.z - radius,
                weather.lastKnownPos.x + radius, weather.lastKnownPos.y + radius, weather.lastKnownPos.z + radius
            )
            
            val affectedEntities = level.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive }

            // Spawn ambient particles (performance optimization: only occasionally or fewer count)
            // Ticks every server tick (50ms). We don't want to spam particles every tick. 
            // We'll spawn particles only if server tick is a multiple of 4 (every 200ms)
            if (server.tickCount % 4 == 0) {
                weather.data.ambientParticles?.let { pData ->
                    // Spawn randomly inside the radius
                    for (i in 0 until pData.count) {
                        val rx = weather.lastKnownPos.x + (Math.random() - 0.5) * 2 * radius
                        val ry = weather.lastKnownPos.y + Math.random() * radius
                        val rz = weather.lastKnownPos.z + (Math.random() - 0.5) * 2 * radius
                        ParticleUtil.spawnParticle(level, pData.type, rx, ry, rz, 1, 0.0, 0.0, 0.0, 0.0)
                    }
                }
            }

            // Play duration sound every 40 ticks (2 seconds)
            if (server.tickCount % 40 == 0) {
                var soundToPlay = weather.durationSound
                if (soundToPlay == null) {
                    // Default to rain or wind
                    soundToPlay = if (weather.data.weatherCondition == "rain") "minecraft:weather.rain" else "minecraft:item.elytra.flying"
                }
                val res = ResourceLocation.tryParse(soundToPlay)
                if (res != null) {
                    val soundEvent = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(res)
                    level.playSound(null, net.minecraft.core.BlockPos.containing(weather.lastKnownPos), soundEvent, net.minecraft.sounds.SoundSource.WEATHER, 0.5f, 1.0f)
                }
            }

            // Apply tick effects every 20 ticks (1 second)
            if (server.tickCount % 20 == 0) {
                for (entity in affectedEntities) {
                    // Tick Damage
                    weather.data.tickDamage?.let { td ->
                        // Check immunity based on Cobblemon types
                        val isImmune = EntityUtil.hasMatchingType(entity, td.immuneTypes)
                        if (!isImmune) {
                            val damage = Math.max(1.0f, entity.maxHealth * (td.damagePercent / 100.0f))
                            entity.hurt(entity.damageSources().magic(), damage)
                        }
                    }

                    // Stat Buffs
                    weather.data.statBuffs?.forEach { sb ->
                        if (EntityUtil.hasMatchingType(entity, listOf(sb.targetType))) {
                            // Target type matches, apply buff!
                            // For now we just apply it directly to CombatStateManager for 1 second so it refreshes
                            CombatStateManager.applyStatChange(entity.uuid, sb.stat, (sb.multiplier - 1.0f).toInt(), 1500)
                        }
                    }
                }
            }
        }
        
        expiredWeathers.forEach { activeWeatherDomains.remove(it) }

        // --- 2. Tick Screens ---
        for ((targetId, screens) in activeScreens) {
            val iter = screens.iterator()
            while (iter.hasNext()) {
                val screen = iter.next()
                if (currentTime > screen.expirationTimeMs) {
                    iter.remove()
                    continue
                }

                // Find the entity across all levels
                val entity = EntityUtil.findLivingEntityByUUID(server, targetId)
                val level = entity?.level() as? ServerLevel

                if (entity != null && level != null && entity.isAlive) {
                    // Refresh stat buffs
                    if (server.tickCount % 20 == 0) {
                        screen.data.statBuffs?.forEach { sb ->
                            CombatStateManager.applyStatChange(entity.uuid, sb.stat, (sb.multiplier - 1.0f).toInt(), 1500)
                        }
                    }
                    
                    // Particles (orbital)
                    if (server.tickCount % 5 == 0) {
                        screen.data.ambientParticles?.let { pData ->
                            val time = server.tickCount * 0.1
                            val px = entity.x + Math.cos(time) * 1.5
                            val py = entity.y + 1.0
                            val pz = entity.z + Math.sin(time) * 1.5
                            ParticleUtil.spawnParticle(level, pData.type, px, py, pz, pData.count, 0.0, 0.0, 0.0, 0.0)
                        }
                    }
                }
            }
        }
    }

    fun getActiveWeatherModifiers(entity: LivingEntity): Map<String, Float> {
        val modifiers = mutableMapOf<String, Float>()
        val levelLoc = entity.level().dimension().location()
        val weather = activeWeatherDomains[levelLoc] ?: return modifiers
        
        // Check if entity is within radius
        if (entity.position().distanceToSqr(weather.lastKnownPos) <= weather.data.radius * weather.data.radius) {
            weather.data.elementalModifiers?.let {
                modifiers.putAll(it)
            }
        }
        return modifiers
    }
    
    fun getActiveWeatherCondition(entity: LivingEntity): String? {
        val levelLoc = entity.level().dimension().location()
        val weather = activeWeatherDomains[levelLoc] ?: return null
        
        if (entity.position().distanceToSqr(weather.lastKnownPos) <= weather.data.radius * weather.data.radius) {
            return weather.data.weatherCondition
        }
        return null
    }
    
}
