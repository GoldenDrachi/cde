package net.drachi.cde.battleengine.mixin.client;

import net.drachi.cde.battleengine.battle.utility.CombatStateManager;
import net.drachi.cde.battleengine.battle.attack.PlayerCombatManager;
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
        if (!net.drachi.cde.battleengine.config.BattleEngineConfigManager.INSTANCE.getConfig().getEnablePlayerMorph()) return;

        if (net.drachi.cde.battleengine.client.MorphRenderer.tryRenderMorph(player, entityYaw, partialTicks, poseStack, buffer, packedLight)) {
            ci.cancel();
        }
    }
}
