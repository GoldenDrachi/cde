package net.drachi.cde.battleengine.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.drachi.cde.battleengine.config.BattleEngineConfigManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PokemonEntity.class)
public abstract class PokemonEntityMixin {

    /**
     * Prevent vanilla melee attacks if Real-Time Battle Engine is enabled.
     * This forces the Pokemon to rely entirely on HostileRealTimeGoal.
     */
    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void disableVanillaMelee(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (BattleEngineConfigManager.INSTANCE.getConfig().isRealtimeEnabled()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * PokemonEntity uses Brain to handle navigation by default, which conflicts
     * with our goalSelector goals (like DungeonFollowPlayerGoal and DungeonWanderGoal).
     * If realtime is enabled, we prevent the Brain from ticking so our goals can control movement.
     */
    @Redirect(
        method = "customServerAiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"
        )
    )
    private void redirectBrainTick(Brain<LivingEntity> brain, ServerLevel level, LivingEntity entity) {
        boolean realtime = BattleEngineConfigManager.INSTANCE.getConfig().isRealtimeEnabled();
        if (entity.tickCount % 40 == 0) {
            System.out.println("[CDE-AI-MIXIN] redirectBrainTick for " + entity.getName().getString() + " - realtime: " + realtime);
        }
        if (!realtime) {
            brain.tick(level, entity);
        }
    }
}
