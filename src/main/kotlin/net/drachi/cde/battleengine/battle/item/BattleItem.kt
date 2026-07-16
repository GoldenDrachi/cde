package net.drachi.cde.battleengine.battle.item

import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*

import net.minecraft.server.level.ServerPlayer

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.entity.LivingEntity
import net.drachi.cde.battleengine.config.BattleEngineConfig

/**
 * Strategy interface for all Battle Items.
 */
interface BattleItem {
    val itemId: String

    /** Called when the holder takes damage from an attack. */
    fun onHitReceived(defenderStats: Pokemon, defenderEntity: LivingEntity, attackerEntity: LivingEntity, damage: Int, isSuperEffective: Boolean, attackType: String) {}

    /** Called periodically on the server tick (turn-based). */
    fun onTurnTick(player: ServerPlayer, mon: Pokemon, config: BattleEngineConfig) {}

    /** Called during damage calculation to modify outgoing damage. */
    fun getDamageMultiplier(isSpecial: Boolean, isSuperEffective: Boolean): Float = 1.0f

    /** Called after an attack finishes. */
    fun onPostAttack(ctx: MoveContext) {}
}

object ItemRegistry {
    private val items = mutableMapOf<String, BattleItem>()

    init {
        register(LeftoversItem())
        register(BlackSludgeItem())
        register(FlameOrbItem())
        register(ToxicOrbItem())
        register(LifeOrbItem())
        register(ExpertBeltItem())
        register(MuscleBandItem())
        register(WiseGlassesItem())
        register(BlunderPolicyItem())
        register(ThroatSprayItem())
        register(ShellBellItem())
        register(EjectButtonItem())
        register(WeaknessPolicyItem())
        register(AbsorbBulbItem())
        register(CellBatteryItem())
        register(RedCardItem())
    }

    fun register(item: BattleItem) {
        items[item.itemId] = item
    }

    fun getItem(itemId: String): BattleItem? {
        return items[itemId]
    }
}
