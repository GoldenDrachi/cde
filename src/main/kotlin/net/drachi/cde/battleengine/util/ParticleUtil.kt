package net.drachi.cde.battleengine.util

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.server.level.ServerLevel
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.drachi.cde.battleengine.battle.attack.MovePhase

object ParticleUtil {
    fun getParticleType(name: String): SimpleParticleType {
        return when (name.lowercase()) {
            "flame" -> ParticleTypes.FLAME
            "rain" -> ParticleTypes.RAIN
            "sand" -> ParticleTypes.ASH
            "snow" -> ParticleTypes.SNOWFLAKE
            "magic" -> ParticleTypes.WITCH
            "witch" -> ParticleTypes.WITCH
            "explosion" -> ParticleTypes.EXPLOSION
            "happy_villager" -> ParticleTypes.HAPPY_VILLAGER
            "crit" -> ParticleTypes.CRIT
            "enchanted_hit" -> ParticleTypes.ENCHANTED_HIT
            "sweep_attack" -> ParticleTypes.SWEEP_ATTACK
            "sonic_boom" -> ParticleTypes.SONIC_BOOM
            "poof" -> ParticleTypes.POOF
            "end_rod" -> ParticleTypes.END_ROD
            "composter" -> ParticleTypes.COMPOSTER
            "splash" -> ParticleTypes.SPLASH
            else -> ParticleTypes.SMOKE
        }
    }

    fun spawnParticle(world: ServerLevel, particleString: String, x: Double, y: Double, z: Double, count: Int, dx: Double, dy: Double, dz: Double, speed: Double) {
        if (particleString.startsWith("cobblemon:")) {
            val resLoc = ResourceLocation.tryParse(particleString)
            if (resLoc != null) {
                val packet = SpawnSnowstormParticlePacket(resLoc, Vec3(x, y, z))
                packet.sendToPlayersAround(x, y, z, 64.0, world.dimension()) { true }
            }
        } else {
            val pType = getParticleType(particleString)
            world.sendParticles(pType, x, y, z, count, dx, dy, dz, speed)
        }
    }

    fun spawnEntityParticle(world: ServerLevel, particleString: String, entity: Entity, targetEntity: Entity? = null) {
        if (particleString.startsWith("cobblemon:")) {
            val resLoc = ResourceLocation.tryParse(particleString)
            if (resLoc != null) {
                val locators = listOf("special", "physical", "target", "mouth", "right_arm", "head", "body", "center", "root")
                val targetLocators = listOf("target", "center", "root")
                
                val packet = if (targetEntity != null) {
                    SpawnSnowstormEntityParticlePacket(resLoc, entity.id, locators, targetEntity.id, targetLocators)
                } else {
                    SpawnSnowstormEntityParticlePacket(resLoc, entity.id, locators, null, emptyList())
                }
                
                packet.sendToAllPlayers()
            }
        } else {
            // Fallback for vanilla particles attached to an entity
            val pType = getParticleType(particleString)
            world.sendParticles(pType, entity.x, entity.y + entity.bbHeight / 2.0, entity.z, 10, entity.bbWidth / 2.0, entity.bbHeight / 2.0, entity.bbWidth / 2.0, 0.0)
        }
    }

    fun resolveParticles(world: ServerLevel, phase: MovePhase?, defaultType: String, caster: LivingEntity, targets: Set<LivingEntity>, defaultPos: Vec3, dx: Double = 0.0, dy: Double = 0.0, dz: Double = 0.0) {
        val allParticles = mutableListOf<ParticleData>()
        if (phase?.particles != null) allParticles.add(phase.particles)
        if (phase?.extraParticles != null) allParticles.addAll(phase.extraParticles)

        if (allParticles.isEmpty()) {
            spawnParticle(world, defaultType, defaultPos.x, defaultPos.y, defaultPos.z, 1, dx, dy, dz, 0.0)
            return
        }

        val dummyTarget = if (targets.isEmpty() && allParticles.any { it.bindToTarget || (it.bindToCaster && it.bindToTarget) }) {
            spawnDummyTarget(world, defaultPos, 15)
        } else null

        for (data in allParticles) {
            val pType = data.type
            val pCount = data.count
            
            if (data.bindToCaster && data.bindToTarget) {
                if (targets.isNotEmpty()) {
                    targets.forEach { spawnEntityParticle(world, pType, caster, it) }
                } else {
                    if (dummyTarget != null) {
                        DelayedActionManager.addDelayedAction(5) {
                            spawnEntityParticle(world, pType, caster, dummyTarget)
                        }
                    }
                }
            } else if (data.bindToCaster) {
                spawnEntityParticle(world, pType, caster)
            } else if (data.bindToTarget) {
                if (targets.isNotEmpty()) {
                    targets.forEach { spawnEntityParticle(world, pType, it) }
                } else {
                    if (dummyTarget != null) {
                        DelayedActionManager.addDelayedAction(5) {
                            spawnEntityParticle(world, pType, dummyTarget)
                        }
                    }
                }
            } else {
                spawnParticle(world, pType, defaultPos.x, defaultPos.y, defaultPos.z, pCount, dx, dy, dz, 0.0)
            }
        }
    }

    fun resolveOnHitParticles(world: ServerLevel, onHitData: ParticleData?, target: LivingEntity) {
        if (onHitData == null) return
        if (onHitData.bindToTarget) {
            spawnEntityParticle(world, onHitData.type, target)
        } else {
            spawnParticle(world, onHitData.type, target.x, target.y + target.bbHeight / 2.0, target.z, onHitData.count, 0.2, 0.2, 0.2, 0.0)
        }
    }

fun spawnDummyTarget(world: ServerLevel, pos: Vec3, durationTicks: Int): Entity {
    val pokemon = com.cobblemon.mod.common.pokemon.Pokemon()
    val dummy = com.cobblemon.mod.common.entity.pokemon.PokemonEntity(world, pokemon)
    
    dummy.setPos(pos.x, pos.y, pos.z)
    dummy.isNoAi = true
    dummy.isSilent = true
    dummy.isInvulnerable = true
    dummy.noPhysics = true
    dummy.setNoGravity(true)
    dummy.isInvisible = true
    dummy.addTag("cdbe_dummy")
    dummy.customName = net.minecraft.network.chat.Component.literal("\u00A7cdbe_dummy")
    dummy.isCustomNameVisible = false
    dummy.entityData.set(com.cobblemon.mod.common.entity.pokemon.PokemonEntity.HIDE_LABEL, true)
    
    world.addFreshEntity(dummy)
    
    DelayedActionManager.addDelayedAction(durationTicks) {
        dummy.discard()
    }
    
    return dummy
}
}
