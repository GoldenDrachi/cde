package net.drachi.cde.battleengine.mixin;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {
    @Accessor("speed")
    void setSpeed(float speed);

    @Accessor("speedOld")
    void setSpeedOld(float speedOld);

    @Accessor("position")
    void setPosition(float position);
}
