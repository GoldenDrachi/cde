package net.drachi.cde.battleengine.battle.attack

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*

import net.minecraft.server.level.ServerPlayer

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.drachi.cde.battleengine.util.ParticleUtil
import net.minecraft.network.chat.Component

interface AttackStrategy {
    fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>?
}

class MeleeAttack : AttackStrategy {
    override fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>? {
        val targets = mutableSetOf<LivingEntity>()
        val phase = ctx.phase
        val caster = ctx.caster
        val world = ctx.world

        val startPos = caster.eyePosition
        val lookVec = caster.lookAngle
        val offsetDistance = (caster.bbWidth / 2.0) + (phase.range.toDouble() / 2.0)
        val targetPos = startPos.add(lookVec.scale(offsetDistance))

        val radius = 1.0 + (phase.range / 4.0)
        val box = net.minecraft.world.phys.AABB(
            targetPos.x - radius, targetPos.y - radius, targetPos.z - radius,
            targetPos.x + radius, targetPos.y + radius, targetPos.z + radius
        )
        
        targets.addAll(world.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive() && it != caster && !it.tags.contains("cdbe_dummy")})
        ParticleUtil.resolveParticles(world, phase, "crit", caster, targets, targetPos.subtract(0.0, 0.5, 0.0))

        return targets
    }
}

class AuraAttack : AttackStrategy {
    override fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>? {
        val targets = mutableSetOf<LivingEntity>()
        val phase = ctx.phase
        val caster = ctx.caster
        val world = ctx.world

        val box = caster.boundingBox.inflate(phase.range.toDouble())
        targets.addAll(world.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive() && it != caster && !it.tags.contains("cdbe_dummy") })

        ParticleUtil.resolveParticles(world, phase, "magic", caster, targets, caster.position().add(0.0, 1.0, 0.0), phase.range.toDouble(), 0.5, phase.range.toDouble())

        return targets
    }
}

class SelfAuraAttack : AttackStrategy {
    override fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>? {
        val aura = AuraAttack()
        val targets = aura.resolveTargets(ctx) ?: mutableSetOf()
        targets.add(ctx.caster)
        return targets
    }
}

class ProjectileAttack : AttackStrategy {
    override fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>? {
        val targets = mutableSetOf<LivingEntity>()
        val phase = ctx.phase
        val baseAcc = phase.accuracy ?: ctx.moveTemplate.accuracy.toInt()
        val isHoming = baseAcc == -1

        if (isHoming) {
            resolveHomingProjectile(ctx, targets)
        } else {
            resolveRaycastProjectile(ctx, targets)
        }
        return targets
    }

    private fun resolveHomingProjectile(ctx: MoveContext, targets: MutableSet<LivingEntity>) {
        val phase = ctx.phase
        val caster = ctx.caster
        val world = ctx.world
        val box = caster.boundingBox.inflate(phase.range.toDouble())
        val found = world.getEntitiesOfClass(LivingEntity::class.java, box) { 
            it.isAlive() && it != caster && (phase.hitsFriendlies || !net.drachi.cde.battleengine.api.BattleEngineApi.isFriendly(caster, it)) 
        }
        val lookVec = caster.lookAngle

        var bestTarget: LivingEntity? = null
        var bestScore = -1000.0

        for (entity in found) {
            val toTarget = entity.position().subtract(caster.position()).normalize()
            val dot = toTarget.dot(lookVec)
            if (dot > 0.3) {
                val dist = entity.distanceToSqr(caster)
                val score = dot * 50.0 - dist
                if (score > bestScore) {
                    bestScore = score
                    bestTarget = entity
                }
            }
        }

        if (bestTarget != null) {
            targets.add(bestTarget)
            val hitPos = bestTarget.position().add(0.0, bestTarget.bbHeight / 2.0, 0.0)
            spawnProjectileTrail(ctx, caster.position().add(0.0, caster.eyeHeight.toDouble(), 0.0), hitPos)
            resolveImpactAoe(ctx, hitPos, targets)
        }
        
        ParticleUtil.resolveParticles(world, phase, "crit", caster, targets, caster.position().add(0.0, 1.0, 0.0))
    }

    private fun resolveRaycastProjectile(ctx: MoveContext, targets: MutableSet<LivingEntity>) {
        val phase = ctx.phase
        val caster = ctx.caster
        val world = ctx.world
        val lookVec = caster.lookAngle
        val step = 0.5
        var currentPos = caster.eyePosition // Fix: Augenhöhe statt Füße

        var hitPos = currentPos
        var hit = false
        
        for (i in 0 until (phase.range * 2).toInt()) {
            currentPos = currentPos.add(lookVec.multiply(step, step, step))

            val blockPos = net.minecraft.core.BlockPos.containing(currentPos.x, currentPos.y, currentPos.z)
            if (!world.getBlockState(blockPos).isAir) {
                hitPos = currentPos
                hit = true
                break
            }

            val radius = 0.5 + (phase.range * 0.05)
            val box = AABB(currentPos.x - radius, currentPos.y - radius, currentPos.z - radius, currentPos.x + radius, currentPos.y + radius, currentPos.z + radius)
            val found = world.getEntitiesOfClass(LivingEntity::class.java, box) { 
                it.isAlive() && it != caster && 
                !it.tags.contains("cdbe_dummy") &&
                (phase.hitsFriendlies || !net.drachi.cde.battleengine.api.BattleEngineApi.isFriendly(caster, it)) 
            }
        
            if (found.isNotEmpty()) {
                hitPos = currentPos
                hit = true
                if (phase.impactAoeData == null) targets.addAll(found)
                break
            }

            val pType = phase.particles?.type ?: "crit"
            if (!pType.startsWith("cobblemon:")) {
                ParticleUtil.spawnParticle(world, pType, currentPos.x, currentPos.y, currentPos.z, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }

        if (hit) resolveImpactAoe(ctx, hitPos, targets)
    
        ParticleUtil.resolveParticles(world, phase, "crit", caster, targets, currentPos)
    }

    private fun spawnProjectileTrail(ctx: MoveContext, startPos: Vec3, hitPos: Vec3) {
        val pType = ctx.phase.particles?.type ?: "crit"
        if (pType.startsWith("cobblemon:")) return
        val dist = Math.sqrt(startPos.distanceToSqr(hitPos))
        val steps = (dist * 2).coerceAtLeast(1.0).toInt()
        for (i in 0..steps) {
            val fraction = i.toDouble() / steps
            val pX = startPos.x + (hitPos.x - startPos.x) * fraction
            val pY = startPos.y + (hitPos.y - startPos.y) * fraction
            val pZ = startPos.z + (hitPos.z - startPos.z) * fraction
            ParticleUtil.spawnParticle(ctx.world, pType, pX, pY, pZ, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun resolveImpactAoe(ctx: MoveContext, hitPos: Vec3, targets: MutableSet<LivingEntity>) {
        val aoe = ctx.phase.impactAoeData ?: return
        val aoeRadius = aoe.radius.toDouble()
        val aoeBox = AABB(hitPos.x - aoeRadius, hitPos.y - aoeRadius, hitPos.z - aoeRadius, hitPos.x + aoeRadius, hitPos.y + aoeRadius, hitPos.z + aoeRadius)
        targets.addAll(ctx.world.getEntitiesOfClass(LivingEntity::class.java, aoeBox) { it.isAlive() && it != ctx.caster })

        val pType = aoe.particles?.type ?: "explosion"
        ParticleUtil.spawnParticle(ctx.world, pType, hitPos.x, hitPos.y, hitPos.z, 20, aoeRadius/2, aoeRadius/2, aoeRadius/2, 0.1)
        ctx.caster.hurtMarked = true
    }
}

class BeamAttack : AttackStrategy {
    override fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>? {
        // Currently BEAM shares projectile code in AttackExecutor, so we delegate.
        return ProjectileAttack().resolveTargets(ctx)
    }
}

class TargetedAttack : AttackStrategy {
    override fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>? {
        return ProjectileAttack().resolveTargets(ctx)
    }
}

class ConeAttack : AttackStrategy {
    override fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>? {
        val targets = mutableSetOf<LivingEntity>()
        val phase = ctx.phase
        val caster = ctx.caster
        val world = ctx.world

        val lookVec = caster.lookAngle
        val targetPos = caster.eyePosition.add(lookVec.multiply(phase.range.toDouble() / 1.5, 0.0, phase.range.toDouble() / 1.5))
        val height = caster.bbHeight.toDouble()
        
        val box = net.minecraft.world.phys.AABB(
            targetPos.x - phase.range.toDouble(), targetPos.y - height, targetPos.z - phase.range.toDouble(),
            targetPos.x + phase.range.toDouble(), targetPos.y + height, targetPos.z + phase.range.toDouble()
        )
        targets.addAll(world.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive() && it != caster && !it.tags.contains("cdbe_dummy") })

        ParticleUtil.resolveParticles(world, phase, "crit", caster, targets, targetPos.add(0.0, 1.0, 0.0), phase.range.toDouble()/2, 0.0, phase.range.toDouble()/2)

        return targets
    }
}

class WaveAttack : AttackStrategy {
    override fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>? {
        return ConeAttack().resolveTargets(ctx)
    }
}

class DashAttack : AttackStrategy {
    override fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>? {
        val targets = mutableSetOf<LivingEntity>()
        val lookVec = ctx.caster.lookAngle
        if (ctx.phase.mobilityData != null) {
            val speed = ctx.phase.mobilityData.dashSpeed?.toDouble() ?: 2.0
            val dashX = lookVec.x * speed
            val dashY = (lookVec.y * speed).coerceAtMost(0.75)
            val dashZ = lookVec.z * speed
            ctx.caster.deltaMovement = ctx.caster.deltaMovement.add(dashX, dashY, dashZ)
            ctx.caster.hurtMarked = true
        }
        val box = ctx.caster.boundingBox.inflate(2.0)
        targets.addAll(ctx.world.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive() && it != ctx.caster && !it.tags.contains("cdbe_dummy") })
        return targets
    }
}

class TeleportAttack : AttackStrategy {
    override fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>? {
        val mob = ctx.phase.mobilityData ?: return mutableSetOf()
        val lookVec = ctx.caster.lookAngle
        val step = 0.5
        var currentPos = ctx.caster.eyePosition
        val range = mob.teleportRange ?: 5.0f
        for (i in 0 until (range * 2).toInt()) {
            val nextPos = currentPos.add(lookVec.multiply(step, step, step))
            val blockPos = net.minecraft.core.BlockPos.containing(nextPos.x, nextPos.y, nextPos.z)
            if (!ctx.world.getBlockState(blockPos).isAir) break
            currentPos = nextPos
        }
        ctx.caster.teleportTo(currentPos.x, currentPos.y, currentPos.z)
        return mutableSetOf()
    }
}
class SelfAttack : AttackStrategy {
    override fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>? {
        val moveName = if (ctx.move.cobblemonMoveId == "cobblemon:neutral_attack") "Neutral Attack" else ctx.moveTemplate.displayName.string
        if (ctx.showLog) (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.literal("§3✧ §fUsed §b$moveName"), false)
        val gained = StatusEffectHandler.applyToSelf(ctx.caster, ctx.phase.statusEffects)
        if (gained.isNotEmpty()) {
            (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(Component.literal("You gained: ${gained.joinToString(", ")}!").withColor(0x00FF00), true)
        }

        ParticleUtil.resolveParticles(ctx.world, ctx.phase, "magic", ctx.caster, mutableSetOf(), ctx.caster.position().add(0.0, 1.0, 0.0), 0.5, 0.5, 0.5)

        ctx.anyTargetHit = true
        return null
    }
}

class DomainAttack : AttackStrategy {
    override fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>? {
        if (ctx.phase.domainData != null) DomainManager.castDomain(ctx.caster, ctx.phase.domainData, ctx.phase.durationSound)
        return null
    }
}

class HazardAttack : AttackStrategy {
    override fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>? {
        val hazardData = ctx.phase.hazardData ?: return null
        
        var targetPos = ctx.caster.position()
        
        // Snap to floor
        var floorY = targetPos.y
        for (y in targetPos.y.toInt() downTo ctx.caster.level().minBuildHeight) {
            val pos = net.minecraft.core.BlockPos(targetPos.x.toInt(), y, targetPos.z.toInt())
            if (!ctx.caster.level().getBlockState(pos).isAir) {
                floorY = y.toDouble() + 1.0
                break
            }
        }
        targetPos = net.minecraft.world.phys.Vec3(targetPos.x, floorY, targetPos.z)

        val moveName = if (ctx.move.cobblemonMoveId == "cobblemon:neutral_attack") "Neutral Attack" else ctx.moveTemplate.displayName.string
        
        if (hazardData.failOnMaxStacks) {
            val activeHazards = net.drachi.cde.battleengine.battle.utility.HazardManager.getActiveHazards()
            val existingHazard = activeHazards.find { it.data.hazardType == hazardData.hazardType && it.casterUuid == ctx.caster.uuid && it.pos.distanceTo(targetPos) <= 5.0 }
            if (existingHazard != null && existingHazard.stacks >= hazardData.maxStacks) {
                if (ctx.showLog) (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(net.minecraft.network.chat.Component.literal("§c⚠ §fBut it failed! Max stacks reached for §e$moveName"), false)
                return null
            }
        }

        val pType = ctx.phase.particles?.type ?: "crit"
        net.drachi.cde.battleengine.battle.utility.HazardManager.placeHazard(ctx.caster, targetPos, hazardData, pType, ctx.phase.durationSound, ctx.phase.hitsFriendlies)
        if (ctx.showLog) (ctx.caster as? net.minecraft.server.level.ServerPlayer)?.displayClientMessage(net.minecraft.network.chat.Component.literal("§3✧ §fPlaced §b$moveName"), false)
        return null
    }
}


class ColumnAttack : AttackStrategy {
    override fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>? {
        val targets = mutableSetOf<LivingEntity>()
        val phase = ctx.phase
        val caster = ctx.caster
        val world = ctx.world

        var targetPos: net.minecraft.world.phys.Vec3? = null
        
        if (caster is net.minecraft.server.level.ServerPlayer) {
            val lockedTargetId = PlayerCombatManager.getLockedTarget(caster)
            if (lockedTargetId != -1) {
                val t = world.getEntity(lockedTargetId)
                if (t != null && t.distanceToSqr(caster) <= phase.range * phase.range) {
                    targetPos = t.position()
                }
            }
        } else if (caster is net.minecraft.world.entity.Mob) {
            val t = caster.target
            if (t != null && t.distanceToSqr(caster) <= phase.range * phase.range) {
                targetPos = t.position()
            }
        }
        
        if (targetPos == null) {
            val hitResult = net.drachi.cde.battleengine.battle.utility.EntityUtil.raycastEntityOrBlock(caster, phase.range.toDouble())
            var tp = hitResult.location
            var bp = net.minecraft.core.BlockPos.containing(tp.x, tp.y, tp.z)
            var iterations = 0
            while (world.getBlockState(bp).isAir && world.getBlockState(bp.below()).isAir && iterations < 64) {
                tp = tp.add(0.0, -1.0, 0.0)
                bp = net.minecraft.core.BlockPos.containing(tp.x, tp.y, tp.z)
                iterations++
            }
            targetPos = tp
        }

        val radius = phase.impactAoeData?.radius?.toDouble() ?: 1.5
        val height = caster.bbHeight.toDouble() * 1.5
        val box = net.minecraft.world.phys.AABB(
            targetPos.x - radius, targetPos.y - height, targetPos.z - radius,
            targetPos.x + radius, targetPos.y + height, targetPos.z + radius
        )
        targets.addAll(world.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive() && it != caster && !it.tags.contains("cdbe_dummy")})

        ParticleUtil.resolveParticles(world, phase, "lightning", caster, targets, targetPos, 0.5, 0.5, 0.5)

        return targets
    }
}

class CrawlAttack : AttackStrategy {
    override fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>? {
        val targets = mutableSetOf<LivingEntity>()
        val phase = ctx.phase
        val caster = ctx.caster
        val world = ctx.world
        val lookVec = net.minecraft.world.phys.Vec3(caster.lookAngle.x, 0.0, caster.lookAngle.z).normalize()
        val step = 0.5
        var currentPos = caster.position().add(0.0, 0.1, 0.0) // Start near ground

        var hitPos = currentPos
        var hit = false
        for (i in 0 until (phase.range * 2).toInt()) {
            currentPos = currentPos.add(lookVec.multiply(step, step, step))
            
            // Snap to ground
            var blockPos = net.minecraft.core.BlockPos.containing(currentPos.x, currentPos.y, currentPos.z)
            while (world.getBlockState(blockPos).isAir && world.getBlockState(blockPos.below()).isAir && currentPos.y > caster.y - 5) {
                currentPos = currentPos.add(0.0, -1.0, 0.0)
                blockPos = net.minecraft.core.BlockPos.containing(currentPos.x, currentPos.y, currentPos.z)
            }
            while (!world.getBlockState(blockPos).isAir && currentPos.y < caster.y + 5) {
                currentPos = currentPos.add(0.0, 1.0, 0.0)
                blockPos = net.minecraft.core.BlockPos.containing(currentPos.x, currentPos.y, currentPos.z)
            }

            val box = net.minecraft.world.phys.AABB(currentPos.x - 1.5, currentPos.y - 1.5, currentPos.z - 1.5, currentPos.x + 1.5, currentPos.y + 1.5, currentPos.z + 1.5)
            val found = world.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive() && it != caster }
            if (found.isNotEmpty()) {
                hitPos = currentPos
                hit = true
                if (phase.impactAoeData == null) targets.addAll(found)
                break
            }

            val pType = phase.particles?.type ?: "block"
            ParticleUtil.spawnParticle(world, pType, currentPos.x, currentPos.y, currentPos.z, 5, 0.2, 0.1, 0.2, 0.0)
        }

        if (hit && phase.impactAoeData != null) {
            val aoeRadius = phase.impactAoeData.radius.toDouble()
            val aoeBox = net.minecraft.world.phys.AABB(hitPos.x - aoeRadius, hitPos.y - aoeRadius, hitPos.z - aoeRadius, hitPos.x + aoeRadius, hitPos.y + aoeRadius, hitPos.z + aoeRadius)
            targets.addAll(world.getEntitiesOfClass(LivingEntity::class.java, aoeBox) { it.isAlive() && it != caster && !it.tags.contains("cdbe_dummy")})
            val pType = phase.impactAoeData.particles?.type ?: "explosion"
            ParticleUtil.spawnParticle(world, pType, hitPos.x, hitPos.y, hitPos.z, 20, aoeRadius/2, aoeRadius/2, aoeRadius/2, 0.1)
        }

        return targets
    }
}

class VanishAttack : AttackStrategy {
    override fun resolveTargets(ctx: MoveContext): MutableSet<LivingEntity>? {
        val phase = ctx.phase
        val caster = ctx.caster
        val world = ctx.world
        var targetsHit = mutableSetOf<LivingEntity>()
        var hitAny = false
        
        // If range > 0, it behaves like TARGETED and tries to hit an enemy
        if (phase.range > 0f) {
            val targeted = TargetedAttack().resolveTargets(ctx)
            if (targeted != null && targeted.isNotEmpty()) {
                targetsHit.addAll(targeted)
                hitAny = true
            }
        } else {
            // Range 0 means it's a self-only vanish like Dig or Fly
            hitAny = true
        }

        if (hitAny) {
            val vanishTypeStr = phase.vanishType?.name?.lowercase() ?: "complete"
            val durationMs = StatusEffectHandler.calculateDurationMs(phase.attackDurationTurns)

            // Apply vanish to caster
            CombatStateManager.applyVolatileStatus(caster.uuid, "vanish_$vanishTypeStr", durationMs)
            caster.addEffect(net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, StatusEffectHandler.calculateDurationTicks(phase.attackDurationTurns), 1, false, false))

            // Apply vanish and properties to targets if we hit any
            if (targetsHit.isNotEmpty()) {
                for (target in targetsHit) {
                    CombatStateManager.applyVolatileStatus(target.uuid, "vanish_$vanishTypeStr", durationMs)
                    target.addEffect(net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, StatusEffectHandler.calculateDurationTicks(phase.attackDurationTurns), 1, false, false))
                    
                    if (phase.bindTargetToCaster) {
                        target.startRiding(caster, true)
                    }
                }
            }

            ParticleUtil.resolveParticles(world, phase, "poof", caster, targetsHit, caster.position().add(0.0, 1.0, 0.0), 0.5, 0.5, 0.5)
            
            return targetsHit
        } else {
            // Missed!
            return mutableSetOf()
        }
    }
}

object AttackStrategyRegistry {
    fun getStrategy(type: AttackTypeEnum?): AttackStrategy? {
        return when (type) {
            AttackTypeEnum.MELEE -> MeleeAttack()
            AttackTypeEnum.PROJECTILE -> ProjectileAttack()
            AttackTypeEnum.BEAM -> BeamAttack()
            AttackTypeEnum.AURA -> AuraAttack()
            AttackTypeEnum.TARGETED -> TargetedAttack()
            AttackTypeEnum.SELF -> SelfAttack()
            AttackTypeEnum.SELF_AURA -> SelfAuraAttack()
            AttackTypeEnum.CONE -> ConeAttack()
            AttackTypeEnum.WAVE -> WaveAttack()
            AttackTypeEnum.DOMAIN -> DomainAttack()
            AttackTypeEnum.HAZARD -> HazardAttack()
            AttackTypeEnum.DASH -> DashAttack()
            AttackTypeEnum.TELEPORT -> TeleportAttack()

            AttackTypeEnum.COLUMN -> ColumnAttack()
            AttackTypeEnum.CRAWL -> CrawlAttack()
            AttackTypeEnum.VANISH -> VanishAttack()
            null -> null
        }
    }
}
