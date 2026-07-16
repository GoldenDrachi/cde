package net.drachi.cde.battleengine

import net.drachi.cde.CDE
import net.drachi.cde.battleengine.battle.attack.*
import net.drachi.cde.battleengine.battle.utility.*
import net.drachi.cde.battleengine.battle.item.*
import net.drachi.cde.battleengine.battle.status.*
import net.drachi.cde.battleengine.client.KeybindManager

/**
 * Client-side module for the real-time battle engine.
 * Registers keybinds, HUD, and client-side networking receivers.
 * Registered as a Fabric ClientModInitializer entrypoint.
 */
object BattleEngineModuleClient {
    fun init() {
        CDE.logger.info("Initializing Cobblemon Dungeon Battle Engine Client")
        
        KeybindManager.register()
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(net.drachi.cde.battleengine.client.gui.BattleHudOverlay)
        net.drachi.cde.battleengine.client.ClientTickHandler.init()
        
        net.drachi.cde.network.NetworkHandler.CHANNEL.registerClientbound(net.drachi.cde.network.CooldownSyncPayload::class.java) { payload, context ->
            val client = net.minecraft.client.Minecraft.getInstance()
            client.execute {
                net.drachi.cde.battleengine.client.ClientCombatManager.setCooldown(payload.moveId, payload.expirationMs)
            }
        }

        net.drachi.cde.network.NetworkHandler.CHANNEL.registerClientbound(net.drachi.cde.network.SyncLockedTargetPayload::class.java) { payload, context ->
            val client = net.minecraft.client.Minecraft.getInstance()
            client.execute {
                net.drachi.cde.battleengine.client.ClientTargetManager.lockedTargetId = payload.entityId
                if (payload.entityId == -1) {
                    net.drachi.cde.battleengine.client.ClientTargetManager.entityStats.clear()
                }
            }
        }
        
        net.drachi.cde.network.NetworkHandler.CHANNEL.registerClientbound(net.drachi.cde.network.SyncEntityStatsPayload::class.java) { payload, context ->
            val client = net.minecraft.client.Minecraft.getInstance()
            client.execute {
                net.drachi.cde.battleengine.client.ClientTargetManager.updateEntityStats(payload.entityId, payload.stats)
            }
        }
        
        net.drachi.cde.network.NetworkHandler.CHANNEL.registerClientbound(net.drachi.cde.network.SyncWeatherPayload::class.java) { payload, context ->
            val client = net.minecraft.client.Minecraft.getInstance()
            client.execute {
                net.drachi.cde.battleengine.client.ClientCombatManager.activeWeather = payload.weatherCondition
            }
        }
        
        net.drachi.cde.network.NetworkHandler.CHANNEL.registerClientbound(net.drachi.cde.network.SyncPlayerStatePayload::class.java) { payload, context ->
            val client = net.minecraft.client.Minecraft.getInstance()
            client.execute {
                net.drachi.cde.battleengine.client.ClientCombatManager.activeItem = payload.activeItem
                net.drachi.cde.battleengine.client.ClientCombatManager.lockedMoveId = payload.lockedMoveId
            }
        }
        
        net.drachi.cde.network.NetworkHandler.CHANNEL.registerClientbound(net.drachi.cde.network.ForceSelectedSlotPayload::class.java) { payload, context ->
            val client = net.minecraft.client.Minecraft.getInstance()
            client.execute {
                com.cobblemon.mod.common.client.CobblemonClient.storage.selectedSlot = payload.slotIndex
                net.drachi.cde.battleengine.client.ClientCombatManager.lastSelectedSlot = payload.slotIndex
            }
        }

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.level != null && client.player != null) {
                val currentSlot = com.cobblemon.mod.common.client.CobblemonClient.storage.selectedSlot
                if (currentSlot != net.drachi.cde.battleengine.client.ClientCombatManager.lastSelectedSlot && currentSlot >= 0) {
                    if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cde", "main"))) {
                        net.drachi.cde.battleengine.client.ClientCombatManager.lastSelectedSlot = currentSlot
                        net.drachi.cde.network.NetworkHandler.sendSyncSelectedSlot(currentSlot)
                    }
                }
            }

            val targetId = net.drachi.cde.battleengine.client.ClientTargetManager.lockedTargetId
            val level = client.level
            
            if (level != null && targetId != -1) {
                val target = level.getEntity(targetId)
                if (target != null && target.isAlive) {
                    val radius = (target.bbWidth / 2.0) + 0.2
                    val angle = (level.gameTime * 0.2)
                    for (i in 0..1) {
                        val offsetAngle = angle + (i * Math.PI)
                        val px = target.x + Math.cos(offsetAngle) * radius
                        val py = target.y + 0.1
                        val pz = target.z + Math.sin(offsetAngle) * radius
                        
                        level.addParticle(
                            net.minecraft.core.particles.ParticleTypes.CRIT,
                            px, py, pz,
                            0.0, 0.0, 0.0
                        )
                    }
                }
            }
        }
        
        var lastFrameTime = System.currentTimeMillis()
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register { context ->
            val currentTime = System.currentTimeMillis()
            val dt = (currentTime - lastFrameTime) / 1000f
            lastFrameTime = currentTime
            
            val client = net.minecraft.client.Minecraft.getInstance()
            val targetId = net.drachi.cde.battleengine.client.ClientTargetManager.lockedTargetId
            val level = client.level
            val player = client.player
            
            if (level != null && player != null && targetId != -1) {
                val target = level.getEntity(targetId)
                if (target != null && target.isAlive) {
                    val dx = target.x - player.x
                    val dy = (target.y + target.bbHeight / 2) - (player.y + player.eyeHeight)
                    val dz = target.z - player.z
                    val distance = Math.sqrt(dx * dx + dz * dz)
                    val targetYaw = (Math.atan2(dz, dx) * (180.0 / Math.PI)).toFloat() - 90.0f
                    val targetPitch = (-(Math.atan2(dy, distance) * (180.0 / Math.PI))).toFloat()
                    
                    val smoothFactor = (10.0f * dt).coerceIn(0.01f, 1.0f)
                    
                    val yDiff = Math.abs(net.minecraft.util.Mth.degreesDifference(player.yRot, targetYaw))
                    val xDiff = Math.abs(net.minecraft.util.Mth.degreesDifference(player.xRot, targetPitch))
                    
                    val yStep = Math.max(0.5f, yDiff * smoothFactor)
                    val xStep = Math.max(0.5f, xDiff * smoothFactor)
                    
                    player.yRot = net.minecraft.util.Mth.approachDegrees(player.yRot, targetYaw, yStep)
                    player.xRot = net.minecraft.util.Mth.approachDegrees(player.xRot, targetPitch, xStep)
                    
                    player.yRotO = player.yRot
                    player.xRotO = player.xRot
                    
                    player.yBodyRot = player.yRot
                    player.yHeadRot = player.yRot
                }
            }
        }
    }
}
