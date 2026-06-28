package net.drachi.cdbe.client.gui

import net.drachi.cdbe.battle.attack.*
import net.drachi.cdbe.battle.utility.*
import net.drachi.cdbe.battle.item.*
import net.drachi.cdbe.battle.status.*


import com.cobblemon.mod.common.client.CobblemonClient
import com.mojang.blaze3d.systems.RenderSystem
import net.drachi.cdbe.CobblemonDungeonBattleEngine
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.resources.ResourceLocation
import net.minecraft.network.chat.Component

object BattleHudOverlay : HudRenderCallback {
    
    // We can use default Minecraft widgets or custom textures. For the POC, we'll draw simple rectangles.
    private val WIDGET_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/widgets.png")
    private val POWDER_SNOW_OUTLINE = ResourceLocation.withDefaultNamespace("textures/misc/powder_snow_outline.png")

    override fun onHudRender(drawContext: GuiGraphics, tickDelta: net.minecraft.client.DeltaTracker) {
        val client = Minecraft.getInstance()
        if (client.player == null) return

        val selectedSlot = CobblemonClient.storage.selectedSlot
        if (selectedSlot < 0) return

        val party = CobblemonClient.storage.party
        val pokemon = party.get(selectedSlot) ?: return
        
        // If the Pokemon is already sent out, the user can't use real-time attacks with it
        // We could grey it out or hide it, but we'll show a warning on cast. For HUD, let's just display it.

        val moveSet = pokemon.moveSet.getMoves()
        
        val screenWidth = client.window.guiScaledWidth
        val screenHeight = client.window.guiScaledHeight

        // Render at bottom right corner
        val startX = screenWidth - 120
        val startY = screenHeight - 90

        val weather = net.drachi.cdbe.client.ClientCombatManager.activeWeather
        if (!weather.isNullOrEmpty()) {
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            
            // Set color based on weather
            val color = when (weather.lowercase()) {
                "rain", "rain_dance" -> 0x883366FF.toInt()
                "sun", "sunny_day" -> 0x88FFCC33.toInt()
                "sandstorm" -> 0x88CCBBAA.toInt()
                "snow", "hail" -> 0x88FFFFFF.toInt()
                else -> 0x88FFFFFF.toInt()
            }
            
            val thickness = 2
            drawContext.fill(0, 0, screenWidth, thickness, color) // Top
            drawContext.fill(0, screenHeight - thickness, screenWidth, screenHeight, color) // Bottom
            drawContext.fill(0, 0, thickness, screenHeight, color) // Left
            drawContext.fill(screenWidth - thickness, 0, screenWidth, screenHeight, color) // Right
            
            RenderSystem.disableBlend()
        }

        RenderSystem.setShader(GameRenderer::getPositionTexShader)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        
        // Draw 4 slots
        for (i in 0 until 4) {
            val slotY = startY + (i * 20)
            
            // Background box (using generic fill for POC, but ideally use textures)
            drawContext.fill(startX, slotY, startX + 110, slotY + 18, 0x88000000.toInt())
            
            val move = if (i < moveSet.size) moveSet[i] else null
            if (move != null) {
                val moveIdStr = move.template.name.lowercase().replace(" ", "_")
                val fullId = "cobblemon:$moveIdStr"
                
                val remainingCd = net.drachi.cdbe.client.ClientCombatManager.getRemainingCooldown(fullId)
                val isOnCooldown = remainingCd > 0

                // Determine color based on move type (could be extracted to a utility later)
                val typeColor = if (isOnCooldown) 0xFF444444.toInt() else getTypeColor(move.template.elementalType.name)
                
                // Draw type colored border
                drawContext.fill(startX, slotY, startX + 2, slotY + 18, typeColor)
                
                // Determine text color (Red if out of PP)
                val textColor = if (move.currentPp <= 0) 0xFF0000 else 0xFFFFFF
                
                // Draw Move Name
                drawContext.drawString(client.font, Component.literal(move.template.displayName.string), startX + 5, slotY + 5, textColor)
                
                // Draw PP
                val ppText = "${move.currentPp}/${move.maxPp}"
                val ppWidth = client.font.width(ppText)
                drawContext.drawString(client.font, Component.literal(ppText), startX + 105 - ppWidth, slotY + 5, textColor)

                // Draw Cooldown Overlay
                val lockedMoveId = net.drachi.cdbe.client.ClientCombatManager.lockedMoveId
                val isLockedOut = lockedMoveId != "none" && fullId != lockedMoveId
                
                if (isOnCooldown || isLockedOut) {
                    drawContext.fill(startX + 2, slotY, startX + 110, slotY + 18, 0x99000000.toInt())
                    if (isOnCooldown) {
                        val cdText = String.format("%.1fs", remainingCd / 1000.0)
                        val cdWidth = client.font.width(cdText)
                        drawContext.drawString(client.font, Component.literal(cdText), startX + 55 - (cdWidth / 2), slotY + 5, 0xFFFFAA)
                    } else if (isLockedOut) {
                        drawContext.drawString(client.font, Component.translatable("cdbe.hud.locked"), startX + 55 - (client.font.width("Locked") / 2), slotY + 5, 0xFF4444)
                    }
                }
                
                // Draw keybind hint dynamically
                val keyHint = when (i) {
                    0 -> net.drachi.cdbe.client.KeybindManager.move1Key.translatedKeyMessage.string
                    1 -> net.drachi.cdbe.client.KeybindManager.move2Key.translatedKeyMessage.string
                    2 -> net.drachi.cdbe.client.KeybindManager.move3Key.translatedKeyMessage.string
                    3 -> net.drachi.cdbe.client.KeybindManager.move4Key.translatedKeyMessage.string
                    else -> ""
                }
                
                // Adjust for longer key names (like 'Mouse 4' or 'Space')
                val keyHintWidth = client.font.width(keyHint)
                drawContext.drawString(client.font, Component.literal(keyHint), startX - keyHintWidth - 5, slotY + 5, 0xFFFF00)
            } else {
                drawContext.drawString(client.font, Component.translatable("cdbe.hud.empty"), startX + 5, slotY + 5, 0x888888)
            }
        }
        
        // Draw selected Pokemon Name
        drawContext.drawString(client.font, Component.literal(pokemon.species.name), startX, startY - 15, 0x00FF00)
        
        var nextY = startY - 30
        
        // Draw Active Item
        val activeItem = net.drachi.cdbe.client.ClientCombatManager.activeItem
        if (activeItem != "none") {
            val itemStr = if (activeItem.startsWith("consumed:")) "Used: ${activeItem.removePrefix("consumed:")}" else "Item: $activeItem"
            val itemColor = if (activeItem.startsWith("consumed:")) 0x888888 else 0xFFFFAA
            drawContext.drawString(client.font, Component.literal(itemStr), startX, nextY, itemColor)
            nextY -= 12
        }
        // Draw Weather UI
        if (!weather.isNullOrEmpty()) {
            val weatherStr = "Weather: ${weather.capitalize()}"
            drawContext.drawString(client.font, Component.literal(weatherStr), startX, nextY, 0x55FFFF)
            nextY -= 12
        }
        
        // --- Render Player Stats ---
        val playerStats = net.drachi.cdbe.client.ClientTargetManager.entityStats[client.player!!.id]
        if (playerStats != null && playerStats.isNotEmpty()) {
            var statY = nextY
            var hasSubstitute = false
            var subHp = 0
            for ((statName, stage) in playerStats) {
                if (statName == "Substitute") {
                    hasSubstitute = true
                    subHp = stage
                } else if (statName == "Protect" || statName == "Endure" || statName == "Poison" || statName == "Burn" || statName == "Paralysis" || statName == "Stun") {
                    val color = when (statName) {
                        "Poison", "Burn" -> 0xFF0000
                        "Protect" -> 0x5555FF
                        "Endure" -> 0xFF5555
                        else -> 0x55FFFF
                    }
                    drawContext.drawString(client.font, Component.literal("[$statName]"), startX, statY, color)
                    statY -= 12
                } else {
                    val formattedName = if (statName.length >= 3) statName.substring(0, 3).uppercase() else statName.uppercase()
                    val sign = if (stage > 0) "+" else ""
                    val text = "[$formattedName $sign$stage]"
                    val color = if (stage > 0) 0x00FF00 else 0xFF0000
                    drawContext.drawString(client.font, Component.literal(text), startX, statY, color)
                    statY -= 12
                }
            }
            if (hasSubstitute) {
                val subMax = (pokemon.maxHealth * 0.25f).toInt().coerceAtLeast(1)
                val subRatio = (subHp.toFloat() / subMax.toFloat()).coerceIn(0f, 1f)
                drawContext.fill(startX, statY - 2, startX + 110, statY + 6, 0x88000000.toInt())
                drawContext.fill(startX, statY - 2, startX + (110 * subRatio).toInt(), statY + 6, 0xFF00FF00.toInt())
                val subText = "Sub: $subHp"
                drawContext.drawString(client.font, Component.literal(subText), startX + 55 - (client.font.width(subText) / 2), statY - 1, 0xFFFFFF)
                statY -= 12
            }
        }
        
        // --- Render Locked Target Stats ---
        val targetId = net.drachi.cdbe.client.ClientTargetManager.lockedTargetId
        if (targetId != -1) {
            val target = client.level?.getEntity(targetId)
            if (target != null) {
                val targetName = target.name.string
                val nameWidth = client.font.width(targetName)
                val centerX = screenWidth / 2
                val topY = 20
                
                // Draw Target Name
                drawContext.drawString(client.font, Component.literal(targetName), centerX - (nameWidth / 2), topY, 0xFF5555)
                
                val targetStats = net.drachi.cdbe.client.ClientTargetManager.entityStats[targetId]
                if (targetStats != null && targetStats.isNotEmpty()) {
                    var statXOffset = 0
                    val statStrings = mutableListOf<Pair<String, Int>>()
                    var hasSubstitute = false
                    var subHp = 0
                    
                    for ((statName, stage) in targetStats) {
                        if (statName == "Substitute") {
                            hasSubstitute = true
                            subHp = stage
                        } else if (statName == "Protect" || statName == "Endure" || statName == "Poison" || statName == "Burn" || statName == "Paralysis" || statName == "Stun") {
                            val c = when (statName) {
                                "Poison", "Burn" -> 0xFF0000
                                "Protect" -> 0x5555FF
                                "Endure" -> 0xFF5555
                                else -> 0x55FFFF
                            }
                            statStrings.add(Pair("[$statName] ", c))
                        } else {
                            val formattedName = if (statName.length >= 3) statName.substring(0, 3).uppercase() else statName.uppercase()
                            val sign = if (stage > 0) "+" else ""
                            statStrings.add(Pair("[$formattedName $sign$stage] ", if (stage > 0) 0x00FF00 else 0xFF0000))
                        }
                    }
                    
                    if (hasSubstitute) {
                        statStrings.add(Pair("[Sub: $subHp] ", 0x00FF00))
                    }

                    // Center the stats row
                    var totalWidth = 0
                    for (str in statStrings) {
                        totalWidth += client.font.width(str.first)
                    }
                    val statsStartX = centerX - (totalWidth / 2)
                    
                    for (str in statStrings) {
                        drawContext.drawString(client.font, Component.literal(str.first), statsStartX + statXOffset, topY + 12, str.second)
                        statXOffset += client.font.width(str.first)
                    }
                }
            }
        }
    }
    
    private fun getTypeColor(type: String): Int {
        return when (type.lowercase()) {
            "normal" -> 0xFFA8A878.toInt()
            "fire" -> 0xFFF08030.toInt()
            "water" -> 0xFF6890F0.toInt()
            "grass" -> 0xFF78C850.toInt()
            "electric" -> 0xFFF8D030.toInt()
            "ice" -> 0xFF98D8D8.toInt()
            "fighting" -> 0xFFC03028.toInt()
            "poison" -> 0xFFA040A0.toInt()
            "ground" -> 0xFFE0C068.toInt()
            "flying" -> 0xFFA890F0.toInt()
            "psychic" -> 0xFFF85888.toInt()
            "bug" -> 0xFFA8B820.toInt()
            "rock" -> 0xFFB8A038.toInt()
            "ghost" -> 0xFF705898.toInt()
            "dragon" -> 0xFF7038F8.toInt()
            "dark" -> 0xFF705848.toInt()
            "steel" -> 0xFFB8B8D0.toInt()
            "fairy" -> 0xFFEE99AC.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
    }
}
