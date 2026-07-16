package net.drachi.cde.dungeonsengine.client

import com.cobblemon.mod.common.client.CobblemonClient
import net.drachi.cde.network.NetworkHandler
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
import net.minecraft.client.renderer.RenderType
import net.drachi.cde.dungeonsengine.registry.ModBlocks

object DungeonsEngineModuleClient {
    private var lastSelectedSlot: Int = -1

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.level != null && client.player != null) {
                val currentSlot = CobblemonClient.storage.selectedSlot
                if (currentSlot != lastSelectedSlot) {
                    if (ClientPlayNetworking.canSend(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cde", "main"))) {
                        lastSelectedSlot = currentSlot
                        NetworkHandler.sendSyncSelectedSlot(currentSlot)
                    }
                }
            }
        }



        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.HAZARD_WATER, RenderType.translucent())
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.HAZARD_LAVA, RenderType.translucent())
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.HAZARD_VOID, RenderType.translucent())
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.HAZARD_WALL_WATER, RenderType.translucent())
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.HAZARD_WALL_LAVA, RenderType.translucent())
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.HAZARD_WALL_VOID, RenderType.translucent())
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.DUNGEON_PORTAL, RenderType.translucent())

        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register({ _, world, pos, _ ->
            if (world != null && pos != null) {
                net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(world, pos)
            } else {
                0x3F76E4
            }
        }, ModBlocks.HAZARD_WATER, ModBlocks.HAZARD_WALL_WATER)


        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register({ state, world, pos, tintIndex ->
            if (world != null && pos != null && tintIndex == 0) {
                val be = world.getBlockEntity(pos) as? net.drachi.cde.dungeonsengine.blocks.DungeonPortalBlockEntity
                if (be != null) {
                    return@register be.colorHex or -0x1000000
                }
            }
            0x800080 or -0x1000000 // Default fallback
        }, ModBlocks.DUNGEON_PORTAL)

        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register({ stack, tintIndex ->
            if (tintIndex == 0) {
                val data = stack.getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.CustomData.EMPTY)
                if (!data.isEmpty) {
                    val tag = data.copyTag()
                    if (tag.contains("ColorHex")) {
                        return@register tag.getInt("ColorHex") or -0x1000000
                    }
                }
            }
            0x800080 or -0x1000000 // Default fallback
        }, ModBlocks.DUNGEON_PORTAL)

        NetworkHandler.CHANNEL.registerClientbound(net.drachi.cde.network.OpenDungeonJoinUIPayload::class.java) { payload, context ->
            val client = net.minecraft.client.Minecraft.getInstance()
            client.execute {
                client.setScreen(net.drachi.cde.dungeonsengine.client.DungeonJoinScreen(payload.unlockedDungeons))
            }
        }

        NetworkHandler.CHANNEL.registerClientbound(net.drachi.cde.network.DungeonResultPayload::class.java) { payload, context ->
            val client = net.minecraft.client.Minecraft.getInstance()
            client.execute {
                client.setScreen(net.drachi.cde.dungeonsengine.client.DungeonResultScreen(payload))
            }
        }

        NetworkHandler.CHANNEL.registerClientbound(net.drachi.cde.network.OpenConfigScreenPacket::class.java) { payload, context ->
            val client = net.minecraft.client.Minecraft.getInstance()
            client.execute {
                val parsedConfig = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<net.drachi.cde.dungeonsengine.data.DungeonConfig>(payload.configJson)
                client.setScreen(net.drachi.cde.dungeonsengine.client.ConfigEditorScreen(parsedConfig))
            }
        }
    }
}
