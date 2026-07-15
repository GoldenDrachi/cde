package net.drachi.cdbe.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
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
        if (!net.drachi.cdbe.config.ConfigManager.INSTANCE.getConfig().getEnablePlayerMorph())
            return;

        Player self = (Player) (Object) this;
        Pokemon activeMon = net.drachi.cdbe.util.MorphUtil.getActivePokemon(self);
        if (activeMon != null && activeMon.getEntity() == null) {
            // Use the pokemon's form hitbox, scaled by its base scale and individual scale modifier
            float scale = activeMon.getForm().getBaseScale() * activeMon.getScaleModifier();
            EntityDimensions pokemonDimensions = activeMon.getForm().getHitbox().scale(scale);
            
            // Constrain if in dungeon
            if (self.level().dimension().location().getNamespace().equals("cdde") && self.level().dimension().location().getPath().equals("dungeon")) {
                pokemonDimensions = net.drachi.cdbe.battle.utility.EntityUtil.INSTANCE.constrainDimensions(pokemonDimensions, 3.0f, 4.0f);
            }
            
            cir.setReturnValue(pokemonDimensions);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void refreshDimensionsEveryTick(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!net.drachi.cdbe.config.ConfigManager.INSTANCE.getConfig().getEnablePlayerMorph())
            return;
        
        Player self = (Player) (Object) this;
        self.refreshDimensions();
    }
}
