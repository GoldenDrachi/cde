package net.drachi.cde.network

import io.wispforest.owo.network.OwoNetChannel
import net.drachi.cde.config.ConfigManager
import net.drachi.cde.dungeonsengine.mechanics.PlayerHazardStateManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

// ==========================================
// Dungeons Engine Payloads
// ==========================================

@JvmRecord
data class DungeonResultPayload(val dungeonName: String, val partyName: String, val messageKey: String)

@JvmRecord
data class OpenDungeonJoinUIPayload(val unlockedDungeons: List<String>)

@JvmRecord
data class JoinDungeonRequestPayload(val dungeonId: String)

@JvmRecord
data class SaveConfigPacket(val configJson: String)

@JvmRecord
data class OpenConfigScreenPacket(val id: String, val configJson: String)

// ==========================================
// Shared & Battle Engine Payloads
// ==========================================

@JvmRecord
data class SyncSelectedSlotPayload(val slotIndex: Int)

@JvmRecord
data class CastMovePayload(val partySlotIndex: Int, val moveSlotIndex: Int)

@JvmRecord
data class ToggleTargetPayload(val isTargeting: Boolean)

@JvmRecord
data class CooldownSyncPayload(val moveId: String, val expirationMs: Long)

@JvmRecord
data class ForceSelectedSlotPayload(val slotIndex: Int)

@JvmRecord
data class SyncLockedTargetPayload(val entityId: Int)

@JvmRecord
data class SyncEntityStatsPayload(val entityId: Int, val stats: Map<String, Int>)

@JvmRecord
data class SyncWeatherPayload(val weatherCondition: String)

@JvmRecord
data class SyncPlayerStatePayload(val activeItem: String, val lockedMoveId: String)

object NetworkHandler {
    val CHANNEL: OwoNetChannel = OwoNetChannel.create(ResourceLocation.fromNamespaceAndPath("cde", "main"))

    fun registerPayloads() {
        val modules = ConfigManager.globalConfig.modules
        
        // Register Shared
        CHANNEL.registerServerbound(SyncSelectedSlotPayload::class.java) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@registerServerbound
            player.server.execute {
                if (modules.dungeonsEnabled) {
                    PlayerHazardStateManager.setSelectedSlot(player.uuid, payload.slotIndex)
                }
                if (modules.battleEngineEnabled) {
                    net.drachi.cde.battleengine.battle.attack.PlayerCombatManager.setActivePokemon(player, payload.slotIndex)
                }
            }
        }

        // Register Dungeons Engine (Only server receivers here, client receivers moved to DungeonsEngineModuleClient)
        if (modules.dungeonsEnabled) {
            CHANNEL.registerServerbound(SaveConfigPacket::class.java) { payload, context ->
                val player = context.player() as? ServerPlayer ?: return@registerServerbound
                if (!player.hasPermissions(2)) return@registerServerbound
                player.server.execute {
                    try {
                        val config = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<net.drachi.cde.dungeonsengine.data.DungeonConfig>(payload.configJson)
                        net.drachi.cde.config.ConfigManager.saveDungeonConfig(config)
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Successfully saved configuration: ${config.id}"))
                    } catch (e: Exception) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Failed to save configuration. Invalid JSON."))
                    }
                }
            }

            CHANNEL.registerServerbound(JoinDungeonRequestPayload::class.java) { payload, context ->
                val player = context.player() as? ServerPlayer ?: return@registerServerbound
                player.server.execute {
                    net.drachi.cde.dungeonsengine.data.DungeonManager.joinDungeon(player, payload.dungeonId, bypassUnlockCheck = false)
                }
            }
        }

        // Register Battle Engine
        if (modules.battleEngineEnabled) {
            CHANNEL.registerServerbound(CastMovePayload::class.java) { payload, context ->
                val player = context.player() as? ServerPlayer ?: return@registerServerbound
                player.server.execute {
                    net.drachi.cde.battleengine.battle.attack.AttackExecutor.handleCastRequest(player, payload.partySlotIndex, payload.moveSlotIndex)
                }
            }

            CHANNEL.registerServerbound(ToggleTargetPayload::class.java) { payload, context ->
                val player = context.player() as? ServerPlayer ?: return@registerServerbound
                player.server.execute {
                    if (payload.isTargeting) {
                        net.drachi.cde.battleengine.battle.attack.PlayerCombatManager.toggleTargetLock(player)
                    }
                }
            }
        }
    }

    // ==========================================
    // Sender Helpers
    // ==========================================

    fun sendSyncSelectedSlot(slotIndex: Int) {
        CHANNEL.clientHandle().send(SyncSelectedSlotPayload(slotIndex))
    }

    fun sendDungeonResult(player: ServerPlayer, dungeonName: String, partyName: String, messageKey: String) {
        CHANNEL.serverHandle(player).send(DungeonResultPayload(dungeonName, partyName, messageKey))
    }
    
    fun sendOpenConfigScreen(player: ServerPlayer, id: String, configJson: String) {
        CHANNEL.serverHandle(player).send(OpenConfigScreenPacket(id, configJson))
    }

    // Battle Engine Senders
    fun sendCastMove(partySlotIndex: Int, moveSlotIndex: Int) {
        CHANNEL.clientHandle().send(CastMovePayload(partySlotIndex, moveSlotIndex))
    }

    fun sendForceSelectedSlot(player: ServerPlayer, slotIndex: Int) {
        CHANNEL.serverHandle(player).send(ForceSelectedSlotPayload(slotIndex))
    }

    fun sendToggleTarget() {
        CHANNEL.clientHandle().send(ToggleTargetPayload(true))
    }

    fun sendCooldownSync(player: ServerPlayer, moveId: String, expirationMs: Long) {
        CHANNEL.serverHandle(player).send(CooldownSyncPayload(moveId, expirationMs))
    }

    fun sendSyncLockedTarget(player: ServerPlayer, entityId: Int) {
        CHANNEL.serverHandle(player).send(SyncLockedTargetPayload(entityId))
    }

    fun sendSyncEntityStats(player: ServerPlayer, entityId: Int, stats: Map<String, Int>) {
        CHANNEL.serverHandle(player).send(SyncEntityStatsPayload(entityId, stats))
    }

    fun sendSyncWeather(player: ServerPlayer, weatherCondition: String) {
        CHANNEL.serverHandle(player).send(SyncWeatherPayload(weatherCondition))
    }
    
    fun sendPlayerStateSync(player: ServerPlayer, activeItem: String, lockedMoveId: String) {
        CHANNEL.serverHandle(player).send(SyncPlayerStatePayload(activeItem, lockedMoveId))
    }
}
