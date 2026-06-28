package net.drachi.cdbe.util

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*


import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.server.level.ServerLevel
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.minecraft.world.entity.Entity

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
            val packet = SpawnSnowstormParticlePacket(ResourceLocation.parse(particleString), Vec3(x, y, z))
            packet.sendToPlayersAround(x, y, z, 64.0, world.dimension()) { true }
        } else {
            val pType = getParticleType(particleString)
            world.sendParticles(pType, x, y, z, count, dx, dy, dz, speed)
        }
    }

    fun spawnEntityParticle(world: ServerLevel, particleString: String, entity: Entity) {
        if (particleString.startsWith("cobblemon:")) {
            // Send the packet attached to the entity's ID
            val packet = SpawnSnowstormEntityParticlePacket(ResourceLocation.parse(particleString), entity.id, emptyList(), null, emptyList())
            packet.sendToPlayersAround(entity.x, entity.y, entity.z, 64.0, world.dimension()) { true }
        } else {
            // Fallback for vanilla particles attached to an entity
            val pType = getParticleType(particleString)
            world.sendParticles(pType, entity.x, entity.y + entity.bbHeight / 2.0, entity.z, 10, entity.bbWidth / 2.0, entity.bbHeight / 2.0, entity.bbWidth / 2.0, 0.0)
        }
    }
}
