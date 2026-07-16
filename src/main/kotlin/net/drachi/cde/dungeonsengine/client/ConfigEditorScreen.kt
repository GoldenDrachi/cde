package net.drachi.cde.dungeonsengine.client

import net.drachi.cde.config.*

import io.wispforest.owo.ui.base.BaseOwoScreen
import io.wispforest.owo.ui.component.Components
import io.wispforest.owo.ui.container.Containers
import io.wispforest.owo.ui.container.FlowLayout
import io.wispforest.owo.ui.container.ScrollContainer
import io.wispforest.owo.ui.core.*
import net.drachi.cde.dungeonsengine.data.DungeonConfig
import net.drachi.cde.dungeonsengine.data.FloorRule
import net.drachi.cde.dungeonsengine.data.FloorConfig
import net.drachi.cde.dungeonsengine.data.PokemonSpawnEntry
import net.drachi.cde.dungeonsengine.data.ItemSpawnEntry
import net.minecraft.network.chat.Component
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import io.wispforest.owo.ui.component.ColorPickerComponent
import io.wispforest.owo.ui.core.Color

class ConfigEditorScreen(private var config: DungeonConfig) : BaseOwoScreen<FlowLayout>() {

    private lateinit var rootScroll: ScrollContainer<FlowLayout>
    private lateinit var contentFlow: FlowLayout
    private var lastScrollProgress = 0.0

    override fun createAdapter(): OwoUIAdapter<FlowLayout> {
        return OwoUIAdapter.create(this, Containers::verticalFlow)
    }

    override fun build(rootComponent: FlowLayout) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT)
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER)

        contentFlow = Containers.verticalFlow(Sizing.content(), Sizing.content())
        contentFlow.horizontalAlignment(HorizontalAlignment.LEFT)
        contentFlow.padding(Insets.of(15))

        rootScroll = Containers.verticalScroll(Sizing.fill(90), Sizing.fill(90), contentFlow)
        rootScroll.surface(Surface.DARK_PANEL)
        rootScroll.padding(Insets.of(5))

        rootComponent.child(rootScroll)

        rebuildContent()
    }

    private fun rebuildContent() {
        if (this::rootScroll.isInitialized) {
            lastScrollProgress = getScrollProgress()
        }
        contentFlow.clearChildren()

        // 1. Header & Global Properties
        contentFlow.child(Components.label(Component.translatable("gui.cde.config.title", config.id)).margins(Insets.bottom(15)))

        val globalRow1 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
        globalRow1.child(createLabelInput("gui.cde.config.amount_of_floors", config.amountOfFloors.toString()) { config.amountOfFloors = it.toIntOrNull() ?: 5 }.margins(Insets.right(10)))
        contentFlow.child(globalRow1.margins(Insets.bottom(5)))

        val globalRow2 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
        val endFloorBtn = Components.button(Component.literal("End Floor: " + config.endFloorType.name)) { btn ->
            val next = when (config.endFloorType) {
                net.drachi.cde.dungeonsengine.data.EndFloorType.NORMAL -> net.drachi.cde.dungeonsengine.data.EndFloorType.TREASURE
                net.drachi.cde.dungeonsengine.data.EndFloorType.TREASURE -> net.drachi.cde.dungeonsengine.data.EndFloorType.BOSS
                net.drachi.cde.dungeonsengine.data.EndFloorType.BOSS -> net.drachi.cde.dungeonsengine.data.EndFloorType.NORMAL
            }
            config.endFloorType = next
            rebuildContent()
        }
        globalRow2.child(endFloorBtn)
        contentFlow.child(globalRow2.margins(Insets.bottom(10)))

        val stairRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
        val stairDirBtn = Components.button(Component.literal("Stairs: " + config.stairDirection.name)) { btn ->
            config.stairDirection = if (config.stairDirection == net.drachi.cde.dungeonsengine.data.StairDirection.DOWN) net.drachi.cde.dungeonsengine.data.StairDirection.UP else net.drachi.cde.dungeonsengine.data.StairDirection.DOWN
            rebuildContent()
        }
        stairRow.child(stairDirBtn.margins(Insets.right(10)))
        contentFlow.child(stairRow.margins(Insets.bottom(10)))

        // Color picker removed

        // 1.5 End Floor Config
        if (config.endFloorType != net.drachi.cde.dungeonsengine.data.EndFloorType.NORMAL) {
            val endBox = Containers.verticalFlow(Sizing.fill(100), Sizing.content())
            endBox.surface(Surface.outline(0xFFFFFF00.toInt()))
            endBox.padding(Insets.of(10))
            endBox.margins(Insets.bottom(15))
            
            endBox.child(Components.label(Component.literal("End Floor Configuration (${config.endFloorType.name})")).margins(Insets.bottom(10)))
            
            val themeRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            themeRow.child(Components.label(Component.literal("Theme:")).margins(Insets.right(10)))
            val themeLabel = if (config.endFloorConfig.theme.isEmpty()) "Select Theme" else config.endFloorConfig.theme
            themeRow.child(Components.button(Component.literal(themeLabel)) {
                val themes = net.drachi.cde.dungeonsengine.data.DungeonManager.availableRooms.keys.sorted().map { net.drachi.cde.dungeonsengine.client.ResourceSelectorScreen.ResourceEntry(it) }
                openResourceSelector(Component.translatable("gui.cde.config.select_theme"), themes) { selectedTheme ->
                    config.endFloorConfig.theme = selectedTheme
                    rebuildContent()
                }
            })
            endBox.child(themeRow.margins(Insets.bottom(10)))
            
            val tRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            tRow.child(Components.label(Component.literal("Treasure Items:")).margins(Insets.right(10)))
            tRow.child(Components.button(Component.literal("+")) {
                config.endFloorConfig.treasure.add(net.drachi.cde.dungeonsengine.data.ItemSpawnEntry())
                rebuildContent()
            })
            endBox.child(tRow.margins(Insets.bottom(5)))
            
            for (i in config.endFloorConfig.treasure.indices) {
                val spawn = config.endFloorConfig.treasure[i]
                val row = Containers.verticalFlow(Sizing.fill(100), Sizing.content())
                row.margins(Insets.bottom(5))
                row.padding(Insets.of(2))
                row.surface(Surface.flat(0x33000000.toInt()))
                
                val itemBtn = Components.button(Component.literal(spawn.item.split(":").lastOrNull()?.take(12) ?: spawn.item.take(12))) {
                    val allItems = net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().toList().sortedBy { it.toString() }.map { net.drachi.cde.dungeonsengine.client.ResourceSelectorScreen.ResourceEntry(it.toString(), net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(it))) }
                    openResourceSelector(Component.translatable("gui.cde.config.select_item"), allItems) {
                        spawn.item = it
                        rebuildContent()
                    }
                }
                
                val weightBox = Components.textBox(Sizing.fixed(60))
                weightBox.text(spawn.weight.toString())
                weightBox.onChanged().subscribe { t -> spawn.weight = t.toIntOrNull() ?: 10 }

                val minBox = Components.textBox(Sizing.fixed(60))
                minBox.text(spawn.minAmount.toString())
                minBox.onChanged().subscribe { t -> spawn.minAmount = t.toIntOrNull() ?: 1 }

                val maxBox = Components.textBox(Sizing.fixed(60))
                maxBox.text(spawn.maxAmount.toString())
                maxBox.onChanged().subscribe { t -> spawn.maxAmount = t.toIntOrNull() ?: 1 }

                val delBtn = Components.button(Component.literal("X")) {
                    config.endFloorConfig.treasure.removeAt(i)
                    rebuildContent()
                }

                val row1 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
                row1.child(itemBtn.margins(Insets.right(5)))
                row1.child(Components.label(Component.translatable("gui.cde.config.weight")).margins(Insets.right(2)))
                row1.child(weightBox.margins(Insets.right(5)))
                row1.child(delBtn)
                row.child(row1.margins(Insets.bottom(2)))

                val row2 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
                row2.child(Components.label(Component.translatable("gui.cde.config.amount")).margins(Insets.right(2)))
                row2.child(minBox.margins(Insets.right(2)))
                row2.child(Components.label(Component.literal("-")).margins(Insets.right(2)))
                row2.child(maxBox.margins(Insets.right(5)))
                row.child(row2)

                endBox.child(row)
            }

            if (config.endFloorType == net.drachi.cde.dungeonsengine.data.EndFloorType.BOSS) {
                // Boss
                val bRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
                bRow.child(Components.label(Component.literal("Boss Pokémon:")).margins(Insets.right(10)))
                bRow.child(Components.button(Component.literal("+")) {
                    config.endFloorConfig.boss.add(net.drachi.cde.dungeonsengine.data.PokemonSpawnEntry())
                    rebuildContent()
                })
                endBox.child(bRow.margins(Insets.of(10, 0, 5, 0)))
                for (i in config.endFloorConfig.boss.indices) {
                    val spawn = config.endFloorConfig.boss[i]
                    val row = Containers.verticalFlow(Sizing.fill(100), Sizing.content())
                    row.margins(Insets.bottom(5))
                    row.padding(Insets.of(2))
                    row.surface(Surface.flat(0x33000000.toInt()))
                    
                    val monWrapper = Containers.horizontalFlow(Sizing.content(), Sizing.content())
                    monWrapper.surface(Surface.flat(0x77000000.toInt()))
                    monWrapper.padding(Insets.of(2))
                    monWrapper.tooltip(Component.literal(spawn.pokemon))
                    
                    try {
                        val p = com.cobblemon.mod.common.api.pokemon.PokemonProperties.parse(spawn.pokemon.split(":").lastOrNull() ?: spawn.pokemon).create()
                        val itemStack = com.cobblemon.mod.common.item.PokemonItem.from(p)
                        val itemComp = Components.item(itemStack).sizing(Sizing.fixed(24))
                        monWrapper.child(itemComp)
                    } catch (e: Exception) {
                        val lbl = Components.label(Component.literal(spawn.pokemon.split(":").lastOrNull()?.take(8) ?: spawn.pokemon.take(8)))
                        monWrapper.child(lbl.margins(Insets.of(4)))
                    }

                    monWrapper.mouseDown().subscribe { _, _, button ->
                        if (button == 0) {
                            val entries = mutableListOf<net.drachi.cde.dungeonsengine.client.ResourceSelectorScreen.ResourceEntry>()
                            try {
                                val allSpecies = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.implemented.sortedBy { it.nationalPokedexNumber }
                                allSpecies.forEach { species -> 
                                    try {
                                        val p = com.cobblemon.mod.common.api.pokemon.PokemonProperties.parse(species.name.lowercase()).create()
                                        val itemStack = com.cobblemon.mod.common.item.PokemonItem.from(p)
                                        entries.add(net.drachi.cde.dungeonsengine.client.ResourceSelectorScreen.ResourceEntry(species.name.lowercase(), itemStack))
                                    } catch (e: Exception) {
                                        entries.add(net.drachi.cde.dungeonsengine.client.ResourceSelectorScreen.ResourceEntry(species.name.lowercase()))
                                    }
                                }
                            } catch (e: Exception) {}
                            
                            openResourceSelector(Component.translatable("gui.cde.config.select_pokemon"), entries) {
                                spawn.pokemon = it
                                rebuildContent()
                            }
                            true
                        } else false
                    }
                    
                    val weightBox = Components.textBox(Sizing.fixed(60))
                    weightBox.text(spawn.weight.toString())
                    weightBox.onChanged().subscribe { t -> spawn.weight = t.toIntOrNull() ?: 10 }

                    val minLvlBox = Components.textBox(Sizing.fixed(60))
                    minLvlBox.text(spawn.minLevel.toString())
                    minLvlBox.onChanged().subscribe { t -> spawn.minLevel = t.toIntOrNull() ?: 1 }

                    val maxLvlBox = Components.textBox(Sizing.fixed(60))
                    maxLvlBox.text(spawn.maxLevel.toString())
                    maxLvlBox.onChanged().subscribe { t -> spawn.maxLevel = t.toIntOrNull() ?: 50 }
                    
                    val recruitBox = Components.textBox(Sizing.fixed(60))
                    recruitBox.text(spawn.baseRecruitment.toString())
                    recruitBox.onChanged().subscribe { t -> spawn.baseRecruitment = t.toIntOrNull() ?: 0 }

                    val delBtn = Components.button(Component.literal("X")) {
                        config.endFloorConfig.boss.removeAt(i)
                        rebuildContent()
                    }

                    val row1 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
                    row1.child(monWrapper.margins(Insets.right(5)))
                    row1.child(Components.label(Component.translatable("gui.cde.config.weight")).margins(Insets.right(2)))
                    row1.child(weightBox.margins(Insets.right(5)))
                    row1.child(delBtn)
                    row.child(row1.margins(Insets.bottom(2)))

                    val row2 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
                    row2.child(Components.label(Component.translatable("gui.cde.config.level")).margins(Insets.right(2)))
                    row2.child(minLvlBox.margins(Insets.right(2)))
                    row2.child(Components.label(Component.literal("-")).margins(Insets.right(2)))
                    row2.child(maxLvlBox.margins(Insets.right(5)))
                    row2.child(Components.label(Component.literal("Recruit:")).margins(Insets.right(2)))
                    row2.child(recruitBox.margins(Insets.right(5)))
                    row.child(row2)

                    endBox.child(row)
                }
                
                // Minions
                val mRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
                mRow.child(Components.label(Component.literal("Minion Pokémon:")).margins(Insets.right(10)))
                mRow.child(Components.button(Component.literal("+")) {
                    config.endFloorConfig.minion.add(net.drachi.cde.dungeonsengine.data.PokemonSpawnEntry())
                    rebuildContent()
                })
                endBox.child(mRow.margins(Insets.of(10, 0, 5, 0)))
                for (i in config.endFloorConfig.minion.indices) {
                    val spawn = config.endFloorConfig.minion[i]
                    val row = Containers.verticalFlow(Sizing.fill(100), Sizing.content())
                    row.margins(Insets.bottom(5))
                    row.padding(Insets.of(2))
                    row.surface(Surface.flat(0x33000000.toInt()))
                    
                    val monWrapper = Containers.horizontalFlow(Sizing.content(), Sizing.content())
                    monWrapper.surface(Surface.flat(0x77000000.toInt()))
                    monWrapper.padding(Insets.of(2))
                    monWrapper.tooltip(Component.literal(spawn.pokemon))
                    
                    try {
                        val p = com.cobblemon.mod.common.api.pokemon.PokemonProperties.parse(spawn.pokemon.split(":").lastOrNull() ?: spawn.pokemon).create()
                        val itemStack = com.cobblemon.mod.common.item.PokemonItem.from(p)
                        val itemComp = Components.item(itemStack).sizing(Sizing.fixed(24))
                        monWrapper.child(itemComp)
                    } catch (e: Exception) {
                        val lbl = Components.label(Component.literal(spawn.pokemon.split(":").lastOrNull()?.take(8) ?: spawn.pokemon.take(8)))
                        monWrapper.child(lbl.margins(Insets.of(4)))
                    }

                    monWrapper.mouseDown().subscribe { _, _, button ->
                        if (button == 0) {
                            val entries = mutableListOf<net.drachi.cde.dungeonsengine.client.ResourceSelectorScreen.ResourceEntry>()
                            try {
                                val allSpecies = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.implemented.sortedBy { it.nationalPokedexNumber }
                                allSpecies.forEach { species -> 
                                    try {
                                        val p = com.cobblemon.mod.common.api.pokemon.PokemonProperties.parse(species.name.lowercase()).create()
                                        val itemStack = com.cobblemon.mod.common.item.PokemonItem.from(p)
                                        entries.add(net.drachi.cde.dungeonsengine.client.ResourceSelectorScreen.ResourceEntry(species.name.lowercase(), itemStack))
                                    } catch (e: Exception) {
                                        entries.add(net.drachi.cde.dungeonsengine.client.ResourceSelectorScreen.ResourceEntry(species.name.lowercase()))
                                    }
                                }
                            } catch (e: Exception) {}
                            
                            openResourceSelector(Component.translatable("gui.cde.config.select_pokemon"), entries) {
                                spawn.pokemon = it
                                rebuildContent()
                            }
                            true
                        } else false
                    }
                    
                    val weightBox = Components.textBox(Sizing.fixed(60))
                    weightBox.text(spawn.weight.toString())
                    weightBox.onChanged().subscribe { t -> spawn.weight = t.toIntOrNull() ?: 10 }

                    val minLvlBox = Components.textBox(Sizing.fixed(60))
                    minLvlBox.text(spawn.minLevel.toString())
                    minLvlBox.onChanged().subscribe { t -> spawn.minLevel = t.toIntOrNull() ?: 1 }

                    val maxLvlBox = Components.textBox(Sizing.fixed(60))
                    maxLvlBox.text(spawn.maxLevel.toString())
                    maxLvlBox.onChanged().subscribe { t -> spawn.maxLevel = t.toIntOrNull() ?: 50 }
                    
                    val recruitBox = Components.textBox(Sizing.fixed(60))
                    recruitBox.text(spawn.baseRecruitment.toString())
                    recruitBox.onChanged().subscribe { t -> spawn.baseRecruitment = t.toIntOrNull() ?: 0 }

                    val delBtn = Components.button(Component.literal("X")) {
                        config.endFloorConfig.minion.removeAt(i)
                        rebuildContent()
                    }

                    val row1 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
                    row1.child(monWrapper.margins(Insets.right(5)))
                    row1.child(Components.label(Component.translatable("gui.cde.config.weight")).margins(Insets.right(2)))
                    row1.child(weightBox.margins(Insets.right(5)))
                    row1.child(delBtn)
                    row.child(row1.margins(Insets.bottom(2)))

                    val row2 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
                    row2.child(Components.label(Component.translatable("gui.cde.config.level")).margins(Insets.right(2)))
                    row2.child(minLvlBox.margins(Insets.right(2)))
                    row2.child(Components.label(Component.literal("-")).margins(Insets.right(2)))
                    row2.child(maxLvlBox.margins(Insets.right(5)))
                    row2.child(Components.label(Component.literal("Recruit:")).margins(Insets.right(2)))
                    row2.child(recruitBox.margins(Insets.right(5)))
                    row.child(row2)

                    endBox.child(row)
                }
            }
            
            contentFlow.child(endBox)
        }

        // 2. Floor Rules
        contentFlow.child(Components.label(Component.translatable("gui.cde.config.floor_rules_header")).margins(Insets.bottom(10)))
        
        for (i in config.floorRules.indices) {
            val rule = config.floorRules[i]
            val ruleBox = Containers.verticalFlow(Sizing.fill(100), Sizing.content())
            ruleBox.surface(Surface.outline(0xFF555555.toInt()))
            ruleBox.padding(Insets.of(10))
            ruleBox.margins(Insets.bottom(10))

            val headerRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            val ruleHeaderLabel = Components.label(Component.translatable("gui.cde.config.rule_header", i)).margins(Insets.right(5))
            headerRow.child(ruleHeaderLabel)
            
            val rangeBox = Components.textBox(Sizing.fixed(100))
            rangeBox.text(rule.range)
            rangeBox.tooltip(Component.translatable("gui.cde.config.range_tooltip"))
            rangeBox.onChanged().subscribe { rule.range = it }
            headerRow.child(rangeBox.margins(Insets.right(15)))

            val delBtn = Components.button(Component.translatable("gui.cde.config.delete_rule")) {
                config.floorRules.removeAt(i)
                rebuildContent()
            }
            headerRow.child(delBtn)
            ruleBox.child(headerRow.margins(Insets.bottom(10)))

            val floorGlobalRow1 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            floorGlobalRow1.child(createBiomePicker("gui.cde.config.biome", rule.config.biome) { rule.config.biome = it; rebuildContent() }.margins(Insets.right(10)))
            val genHazardsBox = Components.checkbox(Component.translatable("gui.cde.config.generate_hazard_seas")).checked(rule.config.generateHazardSeas)
            genHazardsBox.onChanged { checked -> rule.config.generateHazardSeas = checked }
            floorGlobalRow1.child(genHazardsBox)
            ruleBox.child(floorGlobalRow1.margins(Insets.bottom(10)))

            val fStairRow1 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            fStairRow1.child(createBlockPicker("Base Stair Block", rule.config.stairBaseBlock, allowStairs = false) { rule.config.stairBaseBlock = it; rebuildContent() }.margins(Insets.right(10)))
            ruleBox.child(fStairRow1.margins(Insets.bottom(5)))
            
            val fStairRow2 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            fStairRow2.child(createBlockPicker("Stair Step Block", rule.config.stairStepBlock, allowStairs = true, requireStairs = true) { rule.config.stairStepBlock = it; rebuildContent() })
            ruleBox.child(fStairRow2.margins(Insets.bottom(10)))

            // Min/Max Rooms & Prune
            ruleBox.child(createLabelInput("gui.cde.config.min_rooms", rule.config.minRooms.toString()) { rule.config.minRooms = it.toIntOrNull() ?: 5 })
            ruleBox.child(createLabelInput("gui.cde.config.max_rooms", rule.config.maxRooms.toString()) { rule.config.maxRooms = it.toIntOrNull() ?: 10 })
            ruleBox.child(createLabelInput("gui.cde.config.prune_percent", rule.config.deadEndPrunePercent.toString()) { rule.config.deadEndPrunePercent = it.toIntOrNull() ?: 20 })

            // Palettes
            val palettesRow1 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            palettesRow1.child(createBlockPicker("block.cde.palette_a", rule.config.paletteA) { rule.config.paletteA = it; rebuildContent() }.margins(Insets.right(10)))
            palettesRow1.child(createBlockPicker("block.cde.palette_b", rule.config.paletteB) { rule.config.paletteB = it; rebuildContent() }.margins(Insets.right(10)))
            ruleBox.child(palettesRow1.margins(Insets.bottom(5)))

            val palettesRow2 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            palettesRow2.child(createBlockPicker("block.cde.palette_c", rule.config.paletteC) { rule.config.paletteC = it; rebuildContent() }.margins(Insets.right(10)))
            palettesRow2.child(createBlockPicker("block.cde.palette_d", rule.config.paletteD) { rule.config.paletteD = it; rebuildContent() })
            ruleBox.child(palettesRow2.margins(Insets.bottom(10)))

            // Hazards
            val hazardRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            val currentHazard = rule.config.hazards.firstOrNull() ?: "minecraft:water"
            hazardRow.child(createBlockPicker("block.cde.hazard", currentHazard, hazardsOnly = true) { 
                rule.config.hazards.clear()
                rule.config.hazards.add(it)
                rebuildContent() 
            })
            ruleBox.child(hazardRow.margins(Insets.bottom(10)))

            // Themes
            ruleBox.child(Components.label(Component.translatable("gui.cde.config.themes_label")).margins(Insets.bottom(2)))
            val themesBox = Components.textBox(Sizing.fill(80))
            themesBox.text(rule.config.activeSets.joinToString(","))
            themesBox.onChanged().subscribe { 
                rule.config.activeSets = it.split(",").map { t -> t.trim() }.filter { t -> t.isNotEmpty() }.toMutableList() 
            }
            
            // "Add Theme" button parsing templates
            val addThemeBtn = Components.button(Component.translatable("gui.cde.config.add_theme")) {
                val themes = net.drachi.cde.dungeonsengine.data.DungeonManager.availableRooms.keys.sorted().map { ResourceSelectorScreen.ResourceEntry(it) }
                openResourceSelector(Component.translatable("gui.cde.config.select_theme"), themes) { selectedTheme ->
                    if (!rule.config.activeSets.contains(selectedTheme)) {
                        rule.config.activeSets.add(selectedTheme)
                        rebuildContent()
                    }
                }
            }
            val themeRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            themeRow.child(themesBox.margins(Insets.right(10)))
            themeRow.child(addThemeBtn)
            ruleBox.child(themeRow.margins(Insets.bottom(10)))

            // Spawns
            ruleBox.child(buildPokemonSpawnsUI(rule.config))
            ruleBox.child(buildItemSpawnsUI(rule.config))

            contentFlow.child(ruleBox)
        }

        val addRuleBtn = Components.button(Component.translatable("gui.cde.config.add_floor_rule")) {
            config.floorRules.add(FloorRule(range = "${config.floorRules.size + 1}"))
            rebuildContent()
        }
        contentFlow.child(addRuleBtn.margins(Insets.bottom(20)))

        // 3. Save / Cancel Buttons
        val btnRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
        btnRow.horizontalAlignment(HorizontalAlignment.CENTER)
        
        val saveBtn = Components.button(Component.translatable("gui.cde.config.save")) {
            val json = Json { ignoreUnknownKeys = true }.encodeToString(config)
            net.drachi.cde.network.NetworkHandler.CHANNEL.clientHandle().send(net.drachi.cde.network.SaveConfigPacket(json))
            net.minecraft.client.Minecraft.getInstance().setScreen(null)
        }
        val cancelBtn = Components.button(Component.translatable("gui.cde.config.cancel")) {
            net.minecraft.client.Minecraft.getInstance().setScreen(null)
        }
        
        btnRow.child(saveBtn.margins(Insets.right(20)))
        btnRow.child(cancelBtn)
        contentFlow.child(btnRow)

        if (this::rootScroll.isInitialized) {
            try {
               rootScroll.scrollTo(lastScrollProgress)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun getScrollProgress(): Double {
        return try {
            val offsetField = io.wispforest.owo.ui.container.ScrollContainer::class.java.getDeclaredField("scrollOffset")
            offsetField.isAccessible = true
            val maxScrollField = io.wispforest.owo.ui.container.ScrollContainer::class.java.getDeclaredField("maxScroll")
            maxScrollField.isAccessible = true
            
            val offset = offsetField.getDouble(rootScroll)
            val max = maxScrollField.getInt(rootScroll).toDouble()
            if (max > 0) offset / max else 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    private fun buildPokemonSpawnsUI(config: FloorConfig): io.wispforest.owo.ui.core.Component {
        val flow = Containers.verticalFlow(Sizing.fill(100), Sizing.content())
        flow.margins(Insets.bottom(10))
        flow.child(Components.label(Component.translatable("gui.cde.config.pokemon_spawns")).margins(Insets.bottom(2)))
        
        for (i in config.pokemonSpawns.indices) {
            val spawn = config.pokemonSpawns[i]
            val row = Containers.verticalFlow(Sizing.fill(100), Sizing.content())
            row.margins(Insets.bottom(5))
            row.padding(Insets.of(2))
            row.surface(Surface.flat(0x33000000.toInt()))
            
            val monWrapper = Containers.horizontalFlow(Sizing.content(), Sizing.content())
            monWrapper.surface(Surface.flat(0x77000000.toInt()))
            monWrapper.padding(Insets.of(2))
            monWrapper.tooltip(Component.literal(spawn.pokemon))
            
            try {
                val p = com.cobblemon.mod.common.api.pokemon.PokemonProperties.parse(spawn.pokemon.split(":").lastOrNull() ?: spawn.pokemon).create()
                val itemStack = com.cobblemon.mod.common.item.PokemonItem.from(p)
                val itemComp = Components.item(itemStack).sizing(Sizing.fixed(24))
                monWrapper.child(itemComp)
            } catch (e: Exception) {
                val lbl = Components.label(Component.literal(spawn.pokemon.split(":").lastOrNull()?.take(8) ?: spawn.pokemon.take(8)))
                monWrapper.child(lbl.margins(Insets.of(4)))
            }

            monWrapper.mouseDown().subscribe { _, _, button ->
                if (button == 0) {
                    val entries = mutableListOf<ResourceSelectorScreen.ResourceEntry>()
                    try {
                        val allSpecies = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.implemented.sortedBy { it.nationalPokedexNumber }
                        allSpecies.forEach { species -> 
                            try {
                                val p = com.cobblemon.mod.common.api.pokemon.PokemonProperties.parse(species.name.lowercase()).create()
                                val itemStack = com.cobblemon.mod.common.item.PokemonItem.from(p)
                                entries.add(ResourceSelectorScreen.ResourceEntry(species.name.lowercase(), itemStack))
                            } catch (e: Exception) {
                                entries.add(ResourceSelectorScreen.ResourceEntry(species.name.lowercase()))
                            }
                        }
                    } catch (e: Exception) {}
                    
                    openResourceSelector(Component.translatable("gui.cde.config.select_pokemon"), entries) {
                        spawn.pokemon = it
                        rebuildContent()
                    }
                    true
                } else false
            }
            
            val weightBox = Components.textBox(Sizing.fixed(60))
            weightBox.text(spawn.weight.toString())
            weightBox.onChanged().subscribe { spawn.weight = it.toIntOrNull() ?: 10 }
            
            val minLvlBox = Components.textBox(Sizing.fixed(60))
            minLvlBox.text(spawn.minLevel.toString())
            minLvlBox.onChanged().subscribe { spawn.minLevel = it.toIntOrNull() ?: 1 }

            val maxLvlBox = Components.textBox(Sizing.fixed(60))
            maxLvlBox.text(spawn.maxLevel.toString())
            maxLvlBox.onChanged().subscribe { spawn.maxLevel = it.toIntOrNull() ?: 50 }

            val recruitBox = Components.textBox(Sizing.fixed(60))
            recruitBox.text(spawn.baseRecruitment.toString())
            recruitBox.onChanged().subscribe { spawn.baseRecruitment = it.toIntOrNull() ?: 0 }

            val delBtn = Components.button(Component.literal("X")) {
                config.pokemonSpawns.removeAt(i)
                rebuildContent()
            }

            val row1 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            row1.child(monWrapper.margins(Insets.right(5)))
            row1.child(Components.label(Component.translatable("gui.cde.config.weight")).margins(Insets.right(2)))
            row1.child(weightBox.margins(Insets.right(5)))
            row1.child(delBtn)
            row.child(row1.margins(Insets.bottom(2)))

            val row2 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            row2.child(Components.label(Component.translatable("gui.cde.config.level")).margins(Insets.right(2)))
            row2.child(minLvlBox.margins(Insets.right(2)))
            row2.child(Components.label(Component.literal("-")).margins(Insets.right(2)))
            row2.child(maxLvlBox.margins(Insets.right(5)))
            row2.child(Components.label(Component.translatable("gui.cde.config.recruit")).margins(Insets.right(2)))
            row2.child(recruitBox.margins(Insets.right(5)))
            row.child(row2)
            flow.child(row)
        }

        val addBtn = Components.button(Component.translatable("gui.cde.config.add_pokemon")) {
            config.pokemonSpawns.add(PokemonSpawnEntry())
            rebuildContent()
        }
        flow.child(addBtn)
        return flow
    }

    private fun buildItemSpawnsUI(config: FloorConfig): io.wispforest.owo.ui.core.Component {
        val flow = Containers.verticalFlow(Sizing.fill(100), Sizing.content())
        flow.margins(Insets.bottom(10))
        flow.child(Components.label(Component.translatable("gui.cde.config.item_spawns")).margins(Insets.bottom(2)))
        
        for (i in config.itemSpawns.indices) {
            val spawn = config.itemSpawns[i]
            val row = Containers.verticalFlow(Sizing.fill(100), Sizing.content())
            row.margins(Insets.bottom(5))
            row.padding(Insets.of(2))
            row.surface(Surface.flat(0x33000000.toInt()))
            
            val itemBtn = Components.button(Component.literal(spawn.item.split(":").lastOrNull()?.take(12) ?: spawn.item.take(12))) {
                val allItems = BuiltInRegistries.ITEM.keySet().toList().sortedBy { it.toString() }.map { ResourceSelectorScreen.ResourceEntry(it.toString(), ItemStack(BuiltInRegistries.ITEM.get(it))) }
                openResourceSelector(Component.translatable("gui.cde.config.select_item"), allItems) {
                    spawn.item = it
                    rebuildContent()
                }
            }
            
            val weightBox = Components.textBox(Sizing.fixed(60))
            weightBox.text(spawn.weight.toString())
            weightBox.onChanged().subscribe { spawn.weight = it.toIntOrNull() ?: 10 }
            
            val minBox = Components.textBox(Sizing.fixed(60))
            minBox.text(spawn.minAmount.toString())
            minBox.onChanged().subscribe { spawn.minAmount = it.toIntOrNull() ?: 1 }

            val maxBox = Components.textBox(Sizing.fixed(60))
            maxBox.text(spawn.maxAmount.toString())
            maxBox.onChanged().subscribe { spawn.maxAmount = it.toIntOrNull() ?: 1 }

            val delBtn = Components.button(Component.literal("X")) {
                config.itemSpawns.removeAt(i)
                rebuildContent()
            }

            val row1 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            row1.child(itemBtn.margins(Insets.right(5)))
            row1.child(Components.label(Component.translatable("gui.cde.config.weight")).margins(Insets.right(2)))
            row1.child(weightBox.margins(Insets.right(5)))
            row1.child(delBtn)
            row.child(row1.margins(Insets.bottom(2)))

            val row2 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            row2.child(Components.label(Component.translatable("gui.cde.config.amount")).margins(Insets.right(2)))
            row2.child(minBox.margins(Insets.right(2)))
            row2.child(Components.label(Component.literal("-")).margins(Insets.right(2)))
            row2.child(maxBox.margins(Insets.right(5)))
            row.child(row2)
            flow.child(row)
        }

        val addBtn = Components.button(Component.translatable("gui.cde.config.add_item")) {
            config.itemSpawns.add(ItemSpawnEntry())
            rebuildContent()
        }
        flow.child(addBtn)
        return flow
    }

    private fun createLabelInput(translatableKey: String, value: String, onChanged: (String) -> Unit): io.wispforest.owo.ui.core.Component {
        val row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
        row.margins(Insets.bottom(5))
        
        val label = Components.label(Component.translatable(translatableKey))
        val box = Components.textBox(Sizing.fixed(60))
        box.text(value)
        box.onChanged().subscribe(onChanged)
        
        row.child(label.margins(Insets.right(10)))
        row.child(box)
        return row
    }

    private fun createBlockPicker(translatableKey: String, currentBlock: String, allowStairs: Boolean = false, requireStairs: Boolean = false, hazardsOnly: Boolean = false, onSelect: (String) -> Unit): io.wispforest.owo.ui.core.Component {
        val row = Containers.verticalFlow(Sizing.content(), Sizing.content())
        row.child(Components.label(Component.translatable(translatableKey)).margins(Insets.bottom(2)))
        
        val shortName = currentBlock.split(":").lastOrNull() ?: currentBlock
        val btn = Components.button(Component.literal(shortName)) {
            val validBlocks = if (hazardsOnly) {
                listOf(
                    ResourceSelectorScreen.ResourceEntry("minecraft:water", ItemStack(net.minecraft.world.item.Items.WATER_BUCKET)),
                    ResourceSelectorScreen.ResourceEntry("minecraft:lava", ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET)),
                    ResourceSelectorScreen.ResourceEntry("minecraft:air", ItemStack.EMPTY),
                    ResourceSelectorScreen.ResourceEntry("none", ItemStack.EMPTY)
                )
            } else {
                BuiltInRegistries.BLOCK.keySet().toList().sortedBy { it.toString() }.filter { id ->
                    val block = BuiltInRegistries.BLOCK.get(id)
                    val isStair = block is net.minecraft.world.level.block.StairBlock
                    val isAir = block === net.minecraft.world.level.block.Blocks.AIR
                    if (id.namespace == "cde") false
                    else if (isAir) false
                    else if (requireStairs) isStair
                    else if (!allowStairs && isStair) false
                    else {
                        val state = block.defaultBlockState()
                        net.minecraft.world.level.block.Block.isShapeFullBlock(state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO))
                    }
                }.map { ResourceSelectorScreen.ResourceEntry(it.toString(), ItemStack(BuiltInRegistries.BLOCK.get(it).asItem())) }
            }
            openResourceSelector(Component.translatable("gui.cde.config.select_block_for", Component.translatable(translatableKey).string), validBlocks, onSelect)
        }
        row.child(btn)
        return row
    }

    private fun createBiomePicker(translatableKey: String, currentBiome: String, onSelect: (String) -> Unit): io.wispforest.owo.ui.core.Component {
        val row = Containers.verticalFlow(Sizing.content(), Sizing.content())
        row.child(Components.label(Component.translatable(translatableKey)).margins(Insets.bottom(2)))
        
        val shortName = currentBiome.split(":").lastOrNull() ?: currentBiome
        val btn = Components.button(Component.literal(shortName)) {
            val mc = net.minecraft.client.Minecraft.getInstance()
            val registryAccess = mc.connection?.registryAccess() ?: mc.level?.registryAccess()
            
            val validBiomes = if (registryAccess != null) {
                registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.BIOME).keySet().toList().sortedBy { it.toString() }
                    .map { ResourceSelectorScreen.ResourceEntry(it.toString()) }
            } else {
                emptyList()
            }
            openResourceSelector(Component.translatable("gui.cde.config.select_biome"), validBiomes, onSelect)
        }
        row.child(btn)
        return row
    }

    private fun openResourceSelector(title: Component, entries: List<ResourceSelectorScreen.ResourceEntry>, onSelect: (String) -> Unit) {
        net.minecraft.client.Minecraft.getInstance().setScreen(ResourceSelectorScreen(this, entries, title, onSelect))
    }
}
