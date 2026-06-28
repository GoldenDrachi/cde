package net.drachi.cdbe

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*

import net.fabricmc.api.ClientModInitializer
import net.drachi.cdbe.client.KeybindManager

class CobblemonDungeonBattleEngineClient : ClientModInitializer {
    override fun onInitializeClient() {
        CobblemonDungeonBattleEngine.LOGGER.info("Initializing Cobblemon Dungeon Battle Engine Client")
        
        KeybindManager.register()
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(net.drachi.cdbe.client.gui.BattleHudOverlay)
        net.drachi.cdbe.client.ClientTickHandler.init()
        
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(net.drachi.cdbe.network.CooldownSyncPayload.ID) { payload, context ->
            context.client().execute {
                net.drachi.cdbe.client.ClientCombatManager.setCooldown(payload.moveId, payload.expirationMs)
            }
        }

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(net.drachi.cdbe.network.SyncLockedTargetPayload.ID) { payload, context ->
            context.client().execute {
                net.drachi.cdbe.client.ClientTargetManager.lockedTargetId = payload.entityId
                if (payload.entityId == -1) {
                    net.drachi.cdbe.client.ClientTargetManager.entityStats.clear() // Clear stats when unlocked
                }
            }
        }
        
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(net.drachi.cdbe.network.SyncEntityStatsPayload.ID) { payload, context ->
            context.client().execute {
                net.drachi.cdbe.client.ClientTargetManager.updateEntityStats(payload.entityId, payload.stats)
            }
        }
        
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(net.drachi.cdbe.network.SyncWeatherPayload.ID) { payload, context ->
            context.client().execute {
                net.drachi.cdbe.client.ClientCombatManager.activeWeather = payload.weatherCondition
            }
        }
        
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(net.drachi.cdbe.network.SyncPlayerStatePayload.ID) { payload, context ->
            context.client().execute {
                net.drachi.cdbe.client.ClientCombatManager.activeItem = payload.activeItem
                net.drachi.cdbe.client.ClientCombatManager.lockedMoveId = payload.lockedMoveId
            }
        }
        
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(net.drachi.cdbe.network.ForceSelectedSlotPayload.ID) { payload, context ->
            context.client().execute {
                com.cobblemon.mod.common.client.CobblemonClient.storage.selectedSlot = payload.slotIndex
                net.drachi.cdbe.client.ClientCombatManager.lastSelectedSlot = payload.slotIndex
            }
        }

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { client ->
            val currentSlot = com.cobblemon.mod.common.client.CobblemonClient.storage.selectedSlot
            if (currentSlot != net.drachi.cdbe.client.ClientCombatManager.lastSelectedSlot && currentSlot >= 0) {
                net.drachi.cdbe.client.ClientCombatManager.lastSelectedSlot = currentSlot
                net.drachi.cdbe.network.NetworkHandler.sendSyncSelectedSlot(currentSlot)
            }

            val targetId = net.drachi.cdbe.client.ClientTargetManager.lockedTargetId
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
            val targetId = net.drachi.cdbe.client.ClientTargetManager.lockedTargetId
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
