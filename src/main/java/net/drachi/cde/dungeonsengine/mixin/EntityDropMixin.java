package net.drachi.cde.dungeonsengine.mixin;

import net.drachi.cde.dungeonsengine.data.DungeonManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityDropMixin {

    @Inject(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void interceptDungeonDrops(ItemStack stack, float offsetY, CallbackInfoReturnable<ItemEntity> cir) {
        Entity self = (Entity) (Object) this;
        
        if (self.getTags().contains("cde_spawned") && !self.level().isClientSide()) {
            if (self instanceof LivingEntity living) {
                // If this item exactly matches the item the Pokemon is holding in its main hand, allow it to drop.
                if (ItemStack.matches(living.getMainHandItem(), stack)) {
                    return;
                }
            }
            
            // Otherwise, it's a mob drop! Save it to the dungeon manager and prevent it from dropping.
            DungeonManager.INSTANCE.addLootToDungeon((ServerLevel) self.level(), self.blockPosition(), stack.copy());
            cir.setReturnValue(null);
        }
    }
}
