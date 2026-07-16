package net.drachi.cde.battleengine.battle.attack

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import kotlin.math.floor
import kotlin.random.Random

object DamageCalculator {
    fun calculateDamage(
        attacker: Pokemon, 
        defender: Pokemon, 
        rtMove: RealTimeMove, 
        phase: MovePhase,
        moveTemplate: MoveTemplate, 
        targetsHit: Int = 1,
        attackerEntity: net.minecraft.world.entity.LivingEntity,
        defenderEntity: net.minecraft.world.entity.LivingEntity
    ): DamageResult {
        val isNeutralAttack = rtMove.cobblemonMoveId == "cobblemon:neutral_attack"
        val actualMoveName = if (isNeutralAttack) "Neutral Attack" else moveTemplate.displayName.string
        
        if (moveTemplate.power == 0.0 && phase.basePower == 0 && phase.powerScaling == null) return DamageResult(0, DamageBreakdown(
            attackerLevel = attacker.level, moveName = actualMoveName, movePower = 0.0,
            isSpecial = false, rawAttackStat = 0, rawDefenseStat = 0,
            attackStatAfterStages = 0f, defenseStatAfterStages = 0f, attackStage = 0, defenseStage = 0,
            baseDamage = 0.0, multiTargetMod = false, isCrit = false, randomRoll = 100,
            hasStab = false, typeEffectiveness = 1.0f, isBurned = false, weatherMultiplier = 1.0f
        ))

        // Fetch physical/special classification from the Cobblemon move template directly
        val isSpecial = moveTemplate.damageCategory.name.equals("special", ignoreCase = true)

        // 1. Protect Check
        if (CombatStateManager.hasVolatileStatus(defenderEntity.uuid, "protect")) {
            val ignoresProtect = phase.bypasses?.ignoresProtect ?: false
            if (!ignoresProtect) {
                return DamageResult(
                    0, DamageBreakdown(
                        attackerLevel = attacker.level, moveName = actualMoveName, movePower = 0.0,
                        isSpecial = isSpecial, rawAttackStat = 0, rawDefenseStat = 0,
                        attackStatAfterStages = 0f, defenseStatAfterStages = 0f, attackStage = 0, defenseStage = 0,
                        baseDamage = 0.0, multiTargetMod = false, isCrit = false, randomRoll = 0,
                        hasStab = false, typeEffectiveness = 0.0f, isBurned = false, weatherMultiplier = 1.0f
                    )
                )
            }
        }
        
        // 2. OHKO Check
        if (phase.bypasses?.isOhko == true) {
            return DamageResult(
                defender.maxHealth, DamageBreakdown(
                    attackerLevel = attacker.level, moveName = actualMoveName, movePower = 999.0,
                    isSpecial = isSpecial, rawAttackStat = 0, rawDefenseStat = 0,
                    attackStatAfterStages = 0f, defenseStatAfterStages = 0f, attackStage = 0, defenseStage = 0,
                    baseDamage = defender.maxHealth.toDouble(), multiTargetMod = false, isCrit = false, randomRoll = 100,
                    hasStab = false, typeEffectiveness = 1.0f, isBurned = false, weatherMultiplier = 1.0f
                )
            )
        }
        // 3. Vanish Check
        val activeStatuses = CombatStateManager.getAllStatuses()[defenderEntity.uuid]
        var targetVanishType: String? = null
        if (activeStatuses != null) {
            targetVanishType = activeStatuses.keys.find { it.startsWith("vanish_") }?.removePrefix("vanish_")
        }

        var isVanishDoubleDamage = false
        if (targetVanishType != null) {
            val hitsVanishTypes = phase.hitsVanishTypes?.map { it.name.lowercase() } ?: emptyList()
            if (hitsVanishTypes.contains(targetVanishType)) {
                if (phase.doubleDamageOnVanish) {
                    isVanishDoubleDamage = true
                }
            } else {
                // Misses because target is vanished and move doesn't hit this vanish type
                return DamageResult(
                    0, DamageBreakdown(
                        attackerLevel = attacker.level, moveName = actualMoveName, movePower = 0.0,
                        isSpecial = isSpecial, rawAttackStat = 0, rawDefenseStat = 0,
                        attackStatAfterStages = 0f, defenseStatAfterStages = 0f, attackStage = 0, defenseStage = 0,
                        baseDamage = 0.0, multiTargetMod = false, isCrit = false, randomRoll = 0,
                        hasStab = false, typeEffectiveness = 0.0f, isBurned = false, weatherMultiplier = 1.0f
                    )
                )
            }
        }
        
        var power = if (phase.basePower > 0) phase.basePower.toDouble() else moveTemplate.power
        if (isVanishDoubleDamage) {
            power *= 2.0
        }
        if (phase.powerScaling != null) {
            when (phase.powerScaling) {
                "target_weight" -> {
                    // weight in Cobblemon is often in decigrams, divide by 10 for kg.
                    val w = defender.form.weight / 10.0f
                    power = when {
                        w < 10.0 -> 20.0
                        w < 25.0 -> 40.0
                        w < 50.0 -> 60.0
                        w < 100.0 -> 80.0
                        w < 200.0 -> 100.0
                        else -> 120.0
                    }
                }
            }
        }
        
        if (phase.sequentialData != null) {
            val stacks = CombatStateManager.getSequentialStacks(attackerEntity.uuid, rtMove.cobblemonMoveId)
            power *= Math.pow(phase.sequentialData.multiplier.toDouble(), (stacks - 1).toDouble())
        }

        val rawAtkStat = if (isSpecial) attacker.getStat(Stats.SPECIAL_ATTACK) else attacker.getStat(Stats.ATTACK)
        val rawDefStat = if (isSpecial) defender.getStat(Stats.SPECIAL_DEFENCE) else defender.getStat(Stats.DEFENCE)
        var attackStat = rawAtkStat.toFloat()
        var defenseStat = rawDefStat.toFloat()
        
        // Read Custom Stat Stages (-6 to +6)
        val atkStage: Int
        val defStage: Int
        if (!isSpecial) {
            atkStage = CombatStateManager.getStatStage(attackerEntity.uuid, "attack")
            attackStat *= getStatMultiplier(atkStage)

            defStage = CombatStateManager.getStatStage(defenderEntity.uuid, "defense")
            defenseStat *= getStatMultiplier(defStage)
        } else {
            atkStage = CombatStateManager.getStatStage(attackerEntity.uuid, "special_attack")
            attackStat *= getStatMultiplier(atkStage)

            defStage = CombatStateManager.getStatStage(defenderEntity.uuid, "special_defense")
            defenseStat *= getStatMultiplier(defStage)
        }

        // Item Stat Modifiers
        val attackerItem = PokemonItemManager.getActiveHeldItem(attacker)
        val defenderItem = PokemonItemManager.getActiveHeldItem(defender)
        
        if (rtMove.cobblemonMoveId != "cobblemon:neutral_attack") {
            if (attackerItem == "cobblemon:choice_band" && !isSpecial) attackStat *= 1.5f
            if (attackerItem == "cobblemon:choice_specs" && isSpecial) attackStat *= 1.5f
        }
        
        if (defenderItem == "cobblemon:assault_vest" && isSpecial) defenseStat *= 1.5f
        if (defenderItem == "cobblemon:eviolite" && defender.form.evolutions.isNotEmpty()) {
            defenseStat *= 1.5f
        }

        val level = attacker.level

        // Pokémon Legends Z-A / Gen V+ Damage Formula with precise integer math rounding
        // Damage = (((((2 * Level) / 5) + 2) * Power * A / D) / 50) + 2
        val step1 = (2 * level) / 5 + 2
        val step2 = (step1 * power * attackStat) / defenseStat
        val baseDamage = (step2 / 50) + 2

        var damage = baseDamage.toFloat()

        // Multiple Targets Modifier (Only applied if move doesn't hit friendlies, per user specs)
        val multiTargetApplied = targetsHit > 1 && !phase.hitsFriendlies
        if (multiTargetApplied) {
            damage = floor(damage * 0.5f)
        }

        // Domain Elemental Modifiers (Weather)
        val weatherMods = DomainManager.getActiveWeatherModifiers(defenderEntity)
        val atkType = if (isNeutralAttack) "typeless" else moveTemplate.elementalType.name.lowercase()
        val weatherMultiplier = weatherMods[atkType] ?: 1.0f
        if (weatherMultiplier != 1.0f) {
            damage = floor(damage * weatherMultiplier)
        }

        // Critical Hit (1/24 chance for standard moves in recent gens, 1.5x damage)
        val isCrit = Random.nextInt(24) == 0
        if (isCrit) {
            damage = floor(damage * 1.5f)
        }

        // Random roll (85 to 100)
        val randomRoll = Random.nextInt(85, 101)
        damage = floor((damage * randomRoll) / 100.0f)

        // STAB (Same Type Attack Bonus)
        val hasStab = if (isNeutralAttack) false else (attacker.primaryType?.name?.lowercase() == atkType || attacker.secondaryType?.name?.lowercase() == atkType)
        if (hasStab) {
            damage = floor(damage * 1.5f)
        }

        // Type Effectiveness
        var primaryDefType = defender.primaryType?.name?.lowercase() ?: "normal"
        var secondaryDefType = defender.secondaryType?.name?.lowercase()
        
        if (CombatStateManager.hasVolatileStatus(defenderEntity.uuid, "type_override_water")) {
            primaryDefType = "water"
            secondaryDefType = null
        }
        
        val typeMod = if (isNeutralAttack) 1.0f else TypeChart.getMultiplier(atkType, primaryDefType, secondaryDefType)
        damage = floor(damage * typeMod)

        // Item Damage Modifiers
        if (attackerItem != null) {
            val itemStrategy = net.drachi.cde.battleengine.battle.item.ItemRegistry.getItem(attackerItem)
            if (itemStrategy != null) {
                val itemMultiplier = itemStrategy.getDamageMultiplier(isSpecial, typeMod > 1.0f)
                damage = floor(damage * itemMultiplier)
            }
        }
        // Burn modifier (if attacker is burned and physical move)
        val isBurned = !isSpecial && (attacker.status?.status?.name?.path == "burn" || CombatStateManager.hasVolatileStatus(attackerEntity.uuid, "burn"))
        if (isBurned) {
            damage = floor(damage * 0.5f)
        }

        val globalDamageMultiplier = net.drachi.cde.battleengine.config.BattleEngineConfigManager.config.damageMultiplier
        val globalHpMultiplier = net.drachi.cde.battleengine.config.BattleEngineConfigManager.config.hpMultiplier
        damage = floor((damage * globalDamageMultiplier) / globalHpMultiplier)

        var finalDamage = damage.toInt()
        
        // Ensure at least 1 damage is dealt for attacking moves, unless immunity (typeMod == 0)
        if (finalDamage < 1 && moveTemplate.power > 0 && typeMod > 0.0f) finalDamage = 1
        
        // Endure Check
        if (CombatStateManager.hasVolatileStatus(defenderEntity.uuid, "endure")) {
            if (finalDamage >= defender.currentHealth) {
                finalDamage = defender.currentHealth - 1
            }
        }
        
        val breakdown = DamageBreakdown(
            attackerLevel = level,
            moveName = actualMoveName,
            movePower = power,
            isSpecial = isSpecial,
            rawAttackStat = rawAtkStat,
            rawDefenseStat = rawDefStat,
            attackStatAfterStages = attackStat,
            defenseStatAfterStages = defenseStat,
            attackStage = atkStage,
            defenseStage = defStage,
            baseDamage = baseDamage,
            multiTargetMod = multiTargetApplied,
            isCrit = isCrit,
            randomRoll = randomRoll,
            hasStab = hasStab,
            typeEffectiveness = typeMod,
            isBurned = isBurned,
            weatherMultiplier = weatherMultiplier
        )

        return DamageResult(finalDamage, breakdown)
    }

    private fun getStatMultiplier(stage: Int): Float {
        return when {
            stage > 0 -> (2.0f + stage) / 2.0f
            stage < 0 -> 2.0f / (2.0f - stage)
            else -> 1.0f
        }
    }
    
    fun applyTrueDamage(entity: net.minecraft.world.entity.LivingEntity, trueDamage: Int) {
        val globalHpMultiplier = net.drachi.cde.battleengine.config.BattleEngineConfigManager.config.hpMultiplier
        val scaledDamage = (trueDamage / globalHpMultiplier).toInt()
        if (scaledDamage <= 0) return
        
        var targetPokemon: Pokemon? = null
        if (entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
            targetPokemon = entity.pokemon
        } else if (entity is net.minecraft.server.level.ServerPlayer) {
            targetPokemon = PlayerCombatManager.getActivePokemon(entity)
        }
        
        if (targetPokemon != null && targetPokemon.currentHealth > 0) {
            val newHealth = targetPokemon.currentHealth - scaledDamage
            targetPokemon.currentHealth = if (newHealth < 0) 0 else newHealth
            
            // Sync with Minecraft visual health
            val healthRatio = targetPokemon.currentHealth.toFloat() / targetPokemon.maxHealth.toFloat()
            val newMcHealth = entity.maxHealth * healthRatio
            val mcDamage = entity.health - newMcHealth
            
            if (mcDamage > 0) {
                // We use magic damage source just to trigger the hurt animation.
                // It's possible this kills the Minecraft entity if health hits 0.
                entity.hurt(entity.damageSources().magic(), mcDamage)
            }
        } else if (targetPokemon == null) {
            // For non-pokemon entities (just raw damage)
            entity.hurt(entity.damageSources().magic(), scaledDamage.toFloat())
        }
    }
}
