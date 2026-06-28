package net.drachi.cdbe.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.drachi.cdbe.config.ConfigManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PokemonEntity.class)
public abstract class PokemonEntityMixin {

    /**
     * Prevent vanilla melee attacks if Real-Time Battle Engine is enabled.
     * This forces the Pokemon to rely entirely on HostileRealTimeGoal.
     */
    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void disableVanillaMelee(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (ConfigManager.INSTANCE.getConfig().isRealtimeEnabled()) {
            cir.setReturnValue(false);
        }
    }
}
