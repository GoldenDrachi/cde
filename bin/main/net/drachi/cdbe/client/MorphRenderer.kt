package net.drachi.cdbe.client

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EntityType
import com.mojang.blaze3d.vertex.PoseStack
import java.util.WeakHashMap

object MorphRenderer {
    private val fakeEntities = WeakHashMap<AbstractClientPlayer, PokemonEntity>()

    @JvmStatic
    fun renderMorph(player: AbstractClientPlayer, pokemon: Pokemon, entityYaw: Float, partialTicks: Float, poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int): Boolean {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return false

        // Check if substitute is active
        val hasSubstitute = CombatStateManager.hasVolatileStatus(player.uuid, "substitute")
        
        // Check if vanished
        val isVanished = CombatStateManager.getAllStatuses()[player.uuid]?.keys?.any { it.startsWith("vanish_") } == true
        if (isVanished) return true
        
        // Find or create fake entity
        var fakeEntity = fakeEntities[player]
        if (fakeEntity == null || fakeEntity.pokemon.uuid != pokemon.uuid) {
            fakeEntity = com.cobblemon.mod.common.entity.pokemon.PokemonEntity(level, pokemon)
            fakeEntity.addTag("cdbe_puppet")
            fakeEntity.entityData.set(PokemonEntity.HIDE_LABEL, true)
            fakeEntities[player] = fakeEntity
        }
        
        var renderEntity: net.minecraft.world.entity.Entity = fakeEntity
        if (hasSubstitute) {
            // Pending substitute rendering implementation (requires deeper Cobblemon API access)
        }

        // Sync position and rotation
        fakeEntity.setPos(player.x, player.y, player.z)
        fakeEntity.xo = player.xo
        fakeEntity.yo = player.yo
        fakeEntity.zo = player.zo
        fakeEntity.yRotO = player.yRotO
        fakeEntity.yBodyRot = player.yBodyRot
        fakeEntity.yBodyRotO = player.yBodyRotO
        fakeEntity.yHeadRot = player.yHeadRot
        fakeEntity.yHeadRotO = player.yHeadRotO
        fakeEntity.xRotO = player.xRotO
        fakeEntity.setXRot(player.xRot)
        fakeEntity.setYRot(player.yRot)
        
        // Sync walk animation so the pokemon animates when moving
        val selfAnim = player.walkAnimation
        val fakeAnim = fakeEntity.walkAnimation
        val accessor = fakeAnim as net.drachi.cdbe.mixin.WalkAnimationStateAccessor
        accessor.setSpeed(selfAnim.speed())
        // Kotlin can't read speedOld/position from MojMap easily without accessor unless it's public.
        // But player.walkAnimation is WalkAnimationState, let's use the accessor to read it!
        val selfAccessor = selfAnim as net.drachi.cdbe.mixin.WalkAnimationStateAccessor
        // Actually, speed() and position() are public, but speedOld is not. Wait, if we just use Java methods or accessor...
        // We'll just cast both. Wait, accessor doesn't have getters, only setters. Let's add getters to accessor later if needed,
        // or just use public methods `speed()` and `position()` and we'll ignore `speedOld` if it's private, or we'll add getters.
        accessor.setSpeed(selfAnim.speed())
        accessor.setPosition(selfAnim.position())

        // Sync items (render player's held item, or pokemon's held item)
        val playerItem = player.mainHandItem
        if (!playerItem.isEmpty) {
            fakeEntity.shownItem = playerItem
        } else {
            fakeEntity.shownItem = pokemon.heldItem()
        }

        // Sync vital movement states for animation controller (Geckolib/Bedrock)
        fakeEntity.setDeltaMovement(player.deltaMovement)
        fakeEntity.setOnGround(player.onGround())
        fakeEntity.xxa = player.xxa
        fakeEntity.yya = player.yya
        fakeEntity.zza = player.zza
        
        // Sync attack and swing states
        fakeEntity.attackAnim = player.attackAnim
        fakeEntity.oAttackAnim = player.oAttackAnim
        fakeEntity.swingTime = player.swingTime

        // Trigger Cobblemon attack animations if swing just started
        if (player.swingTime == 1) {
            try {
                // Try playing both common attack animations - missing ones are ignored safely by Cobblemon
                (fakeEntity as com.cobblemon.mod.common.entity.PosableEntity).playAnimation("physical_attack")
                (fakeEntity as com.cobblemon.mod.common.entity.PosableEntity).playAnimation("attack")
            } catch (_: Exception) {}
        }

        // Calculate entity animation manually to process limb swinging without ticking the AI
        if (fakeEntity.tickCount < player.tickCount) {
            fakeEntity.tickCount = player.tickCount
            // Let PokemonSideDelegate tick the Geckolib animation components if necessary (if exposed)
            // But calculateEntityAnimation(true) does the basic limb calculation for vanilla
            fakeEntity.calculateEntityAnimation(true)
            
            // The AI tick is cancelled by PokemonEntityTickMixin to prevent crashes,
            // but the mixin safely ticks Geckolib's delegate to process animations.
            fakeEntity.tick()
        }

        // Render the fake entity
        val dispatcher = mc.entityRenderDispatcher
        dispatcher.render(renderEntity, 0.0, 0.0, 0.0, entityYaw, partialTicks, poseStack, buffer, packedLight)

        return true
    }

    /**
     * Check if a player is currently morphed. Used by hand rendering suppression.
     */
    @JvmStatic
    fun isMorphed(player: AbstractClientPlayer): Boolean {
        val pokemon = net.drachi.cdbe.util.MorphUtil.getActivePokemon(player) ?: return false
        return pokemon.entity == null
    }
}
