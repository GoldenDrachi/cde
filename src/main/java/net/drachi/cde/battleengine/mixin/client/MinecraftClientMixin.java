package net.drachi.cde.battleengine.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void interceptLeftClickAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player != null && net.drachi.cde.battleengine.client.MorphRenderer.isMorphed(mc.player)) {
            // Let vanilla handle block interaction (mining)
            if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                return;
            }

            // Get the current selected pokemon slot
            int selectedPartySlot = net.drachi.cde.battleengine.util.MorphUtil.getSelectedSlot();
            if (selectedPartySlot >= 0) {
                // Send neutral attack (-1)
                net.drachi.cde.network.NetworkHandler.INSTANCE.sendCastMove(selectedPartySlot, -1);
                
                // Still allow swinging the hand visually
                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                
                // Cancel vanilla attack (mining blocks, hitting entities)
                cir.setReturnValue(false);
            }
        }
    }
}
