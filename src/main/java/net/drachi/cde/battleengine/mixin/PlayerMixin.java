package net.drachi.cde.battleengine.mixin;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    private void adjustPlayerDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (!net.drachi.cde.battleengine.config.BattleEngineConfigManager.INSTANCE.getConfig().getEnablePlayerMorph())
            return;

        Player self = (Player) (Object) this;
        EntityDimensions pokemonDimensions = net.drachi.cde.battleengine.util.MorphUtil.getMorphDimensions(self);
        if (pokemonDimensions != null) {
            cir.setReturnValue(pokemonDimensions);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void refreshDimensionsEveryTick(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!net.drachi.cde.battleengine.config.BattleEngineConfigManager.INSTANCE.getConfig().getEnablePlayerMorph())
            return;
        
        Player self = (Player) (Object) this;
        self.refreshDimensions();
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void preventJumpingWhenFrozen(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN)) {
            net.minecraft.world.effect.MobEffectInstance effect = self.getEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN);
            if (effect != null && effect.getAmplifier() >= 200) {
                ci.cancel();
            }
        }
    }
}
