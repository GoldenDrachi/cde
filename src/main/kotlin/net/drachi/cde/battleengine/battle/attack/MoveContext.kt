package net.drachi.cde.battleengine.battle.attack

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*


import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.entity.LivingEntity
import net.minecraft.server.level.ServerLevel

data class MoveContext(
    val caster: LivingEntity,
    val pokemonStats: Pokemon,
    val move: RealTimeMove,
    val phase: MovePhase,
    val moveTemplate: com.cobblemon.mod.common.api.moves.MoveTemplate,
    val world: ServerLevel,
    val showLog: Boolean,
    val showDetail: Boolean,
    val heldItem: String?,
    val isRepeating: Boolean,
    var totalDamageDealt: Int = 0,
    var lifeOrbTriggered: Boolean = false,
    var blunderPolicyTriggered: Boolean = false,
    var throatSprayTriggered: Boolean = false,
    var anyTargetHit: Boolean = false
)
