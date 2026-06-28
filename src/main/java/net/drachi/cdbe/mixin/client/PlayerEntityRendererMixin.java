package net.drachi.cdbe.mixin.client;

import net.drachi.cdbe.battle.utility.CombatStateManager;
import net.drachi.cdbe.battle.attack.PlayerCombatManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

@Mixin(PlayerRenderer.class)
public abstract class PlayerEntityRendererMixin {

    @Inject(method = "render*", at = @At("HEAD"), cancellable = true)
    private void renderMorphedPokemon(AbstractClientPlayer player, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!net.drachi.cdbe.config.ConfigManager.INSTANCE.getConfig().getEnablePlayerMorph()) return;

        Pokemon activeMon = net.drachi.cdbe.util.MorphUtil.getActivePokemon(player);
        if (activeMon != null) {
            // Check if the pokemon is currently sent out in the world
            if (activeMon.getEntity() != null) return;
            
            
            
            boolean rendered = net.drachi.cdbe.client.MorphRenderer.renderMorph(player, activeMon, entityYaw, partialTicks, poseStack, buffer, packedLight);
            if (rendered) {
                ci.cancel();
            }
        }
    }
}
