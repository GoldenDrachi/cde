package net.drachi.cde.battleengine.battle.utility

import net.drachi.cde.CDE

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*

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
import net.drachi.cde.battleengine.util.ParticleUtil

object DomainManager {

    data class ActiveDomain(
        val casterId: UUID,
        var lastKnownDimension: ResourceLocation,
        var lastKnownPos: Vec3,
        val data: DomainData,
        var expirationTimeMs: Long,
        val durationSound: String? = null
    )

    private val activeWeatherDomains = mutableMapOf<ResourceLocation, ActiveDomain>()
    private val defaultWeatherDomains = mutableMapOf<ResourceLocation, ActiveDomain>()
    
    private val activeScreens = mutableMapOf<UUID, MutableList<ActiveDomain>>()

    fun castDomain(caster: LivingEntity, domainData: DomainData, durationSound: String? = null) {
        val durationMs = (domainData.durationTurns * net.drachi.cde.battleengine.config.BattleEngineConfigManager.config.turnToSecondsRatio * 1000).toLong()
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
            var dataToUse = domainData
            if (caster.level().dimension().location().namespace == "cde" && caster.level().dimension().location().path == "dungeon") {
                dataToUse = domainData.copy(radius = -1.0f)
            }
            activeWeatherDomains[caster.level().dimension().location()] = newDomain.copy(data = dataToUse)
            if (net.drachi.cde.config.ConfigManager.globalConfig.debugLogging) {
                CDE.logger.info("Weather ${domainData.weatherCondition} cast in ${caster.level().dimension().location()} by ${caster.uuid}")
            }
        } else {
            val list = activeScreens.getOrPut(caster.uuid) { mutableListOf() }
            list.add(newDomain)
        }
    }
    
    fun setDefaultDimensionWeather(dimension: ResourceLocation, domainData: DomainData) {
        val newDomain = ActiveDomain(
            casterId = UUID.randomUUID(), 
            lastKnownDimension = dimension,
            lastKnownPos = Vec3.ZERO,
            data = domainData.copy(radius = -1.0f), 
            expirationTimeMs = Long.MAX_VALUE, 
            durationSound = null
        )
        defaultWeatherDomains[dimension] = newDomain
    }
    
    fun clearDefaultDimensionWeather(dimension: ResourceLocation) {
        defaultWeatherDomains.remove(dimension)
    }

    fun tick(server: MinecraftServer) {
        val currentTime = System.currentTimeMillis()

        val expiredWeathers = mutableListOf<ResourceLocation>()
        
        val weathersToTick = mutableMapOf<ResourceLocation, ActiveDomain>()
        for ((dim, weather) in defaultWeatherDomains) {
            weathersToTick[dim] = weather
        }
        for ((dim, weather) in activeWeatherDomains) {
            if (currentTime > weather.expirationTimeMs) {
                expiredWeathers.add(dim)
            } else {
                weathersToTick[dim] = weather 
            }
        }
        
        for ((dimensionKey, weather) in weathersToTick) {
            var level: ServerLevel? = null
            for (sl in server.allLevels) {
                if (sl.dimension().location() == dimensionKey) {
                    level = sl
                    break
                }
            }
            if (level == null) continue

            if (weather.data.radius > 0.0f) {
                val caster = EntityUtil.findLivingEntityByUUID(server, weather.casterId)
                if (caster != null && caster.isAlive) {
                    weather.lastKnownPos = caster.position()
                }
            }

            val isGlobal = weather.data.radius < 0.0f
            val radius = if (isGlobal) 60000000.0 else weather.data.radius.toDouble()
            val box = if (isGlobal) {
                AABB.ofSize(Vec3.ZERO, radius, radius, radius) 
            } else {
                AABB(
                    weather.lastKnownPos.x - radius, weather.lastKnownPos.y - radius, weather.lastKnownPos.z - radius,
                    weather.lastKnownPos.x + radius, weather.lastKnownPos.y + radius, weather.lastKnownPos.z + radius
                )
            }
            
            val affectedEntities = level.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive }

            if (server.tickCount % 4 == 0) {
                weather.data.ambientParticles?.let { pData ->
                    if (isGlobal) {
                        for (player in level.players()) {
                            for (i in 0 until pData.count) {
                                val rx = player.x + (Math.random() - 0.5) * 30.0
                                val ry = player.y + Math.random() * 20.0
                                val rz = player.z + (Math.random() - 0.5) * 30.0
                                ParticleUtil.spawnParticle(level, pData.type, rx, ry, rz, 1, 0.0, 0.0, 0.0, 0.0)
                            }
                        }
                    } else {
                        for (i in 0 until pData.count) {
                            val rx = weather.lastKnownPos.x + (Math.random() - 0.5) * 2 * radius
                            val ry = weather.lastKnownPos.y + Math.random() * radius
                            val rz = weather.lastKnownPos.z + (Math.random() - 0.5) * 2 * radius
                            ParticleUtil.spawnParticle(level, pData.type, rx, ry, rz, 1, 0.0, 0.0, 0.0, 0.0)
                        }
                    }
                }
            }

            if (server.tickCount % 40 == 0) {
                var soundToPlay = weather.durationSound
                if (soundToPlay == null) {
                    soundToPlay = if (weather.data.weatherCondition == "rain") "minecraft:weather.rain" else "minecraft:item.elytra.flying"
                }
                val res = ResourceLocation.tryParse(soundToPlay)
                if (res != null) {
                    val soundEvent = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(res)
                    if (isGlobal) {
                        for (player in level.players()) {
                            level.playSound(null, player.blockPosition(), soundEvent, net.minecraft.sounds.SoundSource.WEATHER, 0.5f, 1.0f)
                        }
                    } else {
                        level.playSound(null, net.minecraft.core.BlockPos.containing(weather.lastKnownPos), soundEvent, net.minecraft.sounds.SoundSource.WEATHER, 0.5f, 1.0f)
                    }
                }
            }

            if (server.tickCount % 20 == 0) {
                for (entity in affectedEntities) {
                    weather.data.tickDamage?.let { td ->
                        val isImmune = EntityUtil.hasMatchingType(entity, td.immuneTypes)
                        if (!isImmune) {
                            val damage = Math.max(1.0f, entity.maxHealth * (td.damagePercent / 100.0f))
                            entity.hurt(entity.damageSources().magic(), damage)
                        }
                    }

                    weather.data.statBuffs?.forEach { sb ->
                        if (EntityUtil.hasMatchingType(entity, listOf(sb.targetType))) {
                            CombatStateManager.applyStatChange(entity.uuid, sb.stat, (sb.multiplier - 1.0f).toInt(), 1500)
                        }
                    }
                }
            }
        }
        
        expiredWeathers.forEach { activeWeatherDomains.remove(it) }

        for ((targetId, screens) in activeScreens) {
            val iter = screens.iterator()
            while (iter.hasNext()) {
                val screen = iter.next()
                if (currentTime > screen.expirationTimeMs) {
                    iter.remove()
                    continue
                }

                val entity = EntityUtil.findLivingEntityByUUID(server, targetId)
                val level = entity?.level() as? ServerLevel

                if (entity != null && level != null && entity.isAlive) {
                    if (server.tickCount % 20 == 0) {
                        screen.data.statBuffs?.forEach { sb ->
                            CombatStateManager.applyStatChange(entity.uuid, sb.stat, (sb.multiplier - 1.0f).toInt(), 1500)
                        }
                    }
                    
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

    fun getActiveWeatherFor(entity: LivingEntity): ActiveDomain? {
        val levelLoc = entity.level().dimension().location()
        val active = activeWeatherDomains[levelLoc]
        if (active != null) {
            if (active.data.radius < 0.0f || entity.position().distanceToSqr(active.lastKnownPos) <= active.data.radius * active.data.radius) {
                return active
            }
        }
        val defaultWeather = defaultWeatherDomains[levelLoc]
        if (defaultWeather != null) {
            if (defaultWeather.data.radius < 0.0f || entity.position().distanceToSqr(defaultWeather.lastKnownPos) <= defaultWeather.data.radius * defaultWeather.data.radius) {
                return defaultWeather
            }
        }
        return null
    }

    fun getActiveWeatherModifiers(entity: LivingEntity): Map<String, Float> {
        val modifiers = mutableMapOf<String, Float>()
        val weather = getActiveWeatherFor(entity) ?: return modifiers
        
        weather.data.elementalModifiers?.let {
            modifiers.putAll(it)
        }
        return modifiers
    }
    
    fun getActiveWeatherCondition(entity: LivingEntity): String? {
        val weather = getActiveWeatherFor(entity) ?: return null
        return weather.data.weatherCondition
    }
}
