package net.drachi.cde.battleengine.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void makePuppetUnpickable(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.getTags().contains("cdbe_puppet")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void hidePuppetName(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.getTags().contains("cdbe_puppet")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void interceptDungeonDrops(net.minecraft.world.item.ItemStack stack, float offsetY, CallbackInfoReturnable<net.minecraft.world.entity.item.ItemEntity> cir) {
        Entity self = (Entity) (Object) this;
        
        if (self.getTags().contains("cde_spawned") && !self.level().isClientSide()) {
            if (self instanceof net.minecraft.world.entity.LivingEntity living) {
                // If this item exactly matches the item the Pokemon is holding in its main hand, allow it to drop.
                if (net.minecraft.world.item.ItemStack.matches(living.getMainHandItem(), stack)) {
                    return;
                }
            }
            
            // Otherwise, it's a mob drop! Save it to the dungeon manager and prevent it from dropping.
            net.drachi.cde.dungeonsengine.data.DungeonManager.INSTANCE.addLootToDungeon((net.minecraft.server.level.ServerLevel) self.level(), self.blockPosition(), stack.copy());
            cir.setReturnValue(null);
        }
    }
}
