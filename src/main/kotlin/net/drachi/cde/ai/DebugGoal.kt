package net.drachi.cde.ai
import net.drachi.cde.CDE
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.ai.goal.Goal
import net.drachi.cde.battleengine.BattleEngineModule
import java.util.EnumSet

class DebugGoal(private val pokemonEntity: PokemonEntity) : Goal() {
    init {
        this.flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }
    override fun canUse(): Boolean {
        if (pokemonEntity.tickCount % 20 == 0) {
            CDE.logger.info("DebugGoal running for " + pokemonEntity.uuid + ". Target: " + pokemonEntity.target + "")
        }
        return false
    }
}
