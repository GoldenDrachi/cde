package net.drachi.cde.battleengine.mixin;

import com.cobblemon.mod.common.client.net.effect.SpawnSnowstormEntityParticleHandler;
import net.drachi.cde.battleengine.client.MorphRendererProxy;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Intercepts entity lookup in Cobblemon's snowstorm particle handler so that
 * particles bind to the morphed PokemonEntity (the visual puppet) rather than
 * the real player entity. Without this, particles spawned on a morphed player
 * would target an invisible entity and render at the wrong locators.
 *
 * Key remap notes:
 * - @Mixin remap=false: Target class is Cobblemon (mod), not vanilla.
 * - @At remap=true: The invoked method (ClientLevel.getEntity) IS vanilla
 * and must be remapped to intermediary names in production.
 */
@Mixin(value = SpawnSnowstormEntityParticleHandler.class, remap = false)
public class SpawnSnowstormEntityParticleHandlerMixin {

    /**
     * Redirects both calls to ClientLevel.getEntity(int) inside the handle method.
     * If the resolved entity has a morph (fake PokemonEntity), return that instead
     * so particle locators bind to the visible model.
     *
     * Remap strategy:
     * - @Mixin remap=false: target is a Cobblemon mod class, no intermediary
     * mapping exists.
     * - @Redirect remap=false: 'handle' is a Cobblemon method name, not vanilla.
     * - @At remap=true: ClientLevel.getEntity IS vanilla and must be remapped to
     * intermediary.
     */
    @Redirect(method = "handle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getEntity(I)Lnet/minecraft/world/entity/Entity;", remap = true), require = 0, remap = false)
    private Entity redirectGetEntity(ClientLevel world, int entityId) {
        Entity entity = world.getEntity(entityId);
        if (entity != null) {
            Entity fake = MorphRendererProxy.INSTANCE.getFakeEntityProvider().invoke(entity);
            if (fake != null && entity instanceof net.minecraft.client.player.AbstractClientPlayer player) {
                net.drachi.cde.battleengine.client.MorphRenderer.syncEntityForParticles(player,
                        (com.cobblemon.mod.common.entity.pokemon.PokemonEntity) fake);

                return fake;
            }
        }
        return entity;
    }
}
