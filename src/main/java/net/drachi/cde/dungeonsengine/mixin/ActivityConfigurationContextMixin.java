package net.drachi.cde.dungeonsengine.mixin;

import com.cobblemon.mod.common.api.ai.ActivityConfigurationContext;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ActivityConfigurationContext.class)
public abstract class ActivityConfigurationContextMixin {
    @Final
    @Shadow(remap = false)
    private List<Pair<Integer, BehaviorControl<? super LivingEntity>>> tasks;
    
    @Final
    @Shadow(remap = false)
    private Activity activity;

    @Inject(method = "apply", at = @At("HEAD"), remap = false)
    private void applyMixin(LivingEntity entity, CallbackInfo ci) {
        if (entity instanceof PokemonEntity) {
            String dimId = entity.level().dimension().location().toString();
            if (dimId.equals("cde:dungeon")) {
                if (!activity.getName().equals("core")) {
                    tasks.clear();
                }
            }
        }
    }
}
