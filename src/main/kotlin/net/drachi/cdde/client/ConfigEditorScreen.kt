package net.drachi.cdde.client

import io.wispforest.owo.ui.base.BaseOwoScreen
import io.wispforest.owo.ui.component.Components
import io.wispforest.owo.ui.container.Containers
import io.wispforest.owo.ui.container.FlowLayout
import io.wispforest.owo.ui.container.ScrollContainer
import io.wispforest.owo.ui.core.*
import net.drachi.cdde.data.DungeonConfig
import net.drachi.cdde.data.FloorRule
import net.drachi.cdde.data.FloorConfig
import net.drachi.cdde.data.PokemonSpawnEntry
import net.drachi.cdde.data.ItemSpawnEntry
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
        contentFlow.child(Components.label(Component.translatable("gui.cdde.config.title", config.id)).margins(Insets.bottom(15)))

        val globalRow1 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
        globalRow1.child(createLabelInput("gui.cdde.config.amount_of_floors", config.amountOfFloors.toString()) { config.amountOfFloors = it.toIntOrNull() ?: 5 }.margins(Insets.right(10)))
        globalRow1.child(createLabelInput("gui.cdde.config.biome", config.biome) { config.biome = it })
        contentFlow.child(globalRow1.margins(Insets.bottom(5)))

        val globalRow2 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
        val genHazardsBox = Components.checkbox(Component.translatable("gui.cdde.config.generate_hazard_seas")).checked(config.generateHazardSeas)
        genHazardsBox.onChanged { checked -> config.generateHazardSeas = checked }
        globalRow2.child(genHazardsBox.margins(Insets.right(20)))

        val endFloorBtn = Components.button(Component.literal("End Floor: " + config.endFloorType.name)) { btn ->
            val next = when (config.endFloorType) {
                net.drachi.cdde.data.EndFloorType.NORMAL -> net.drachi.cdde.data.EndFloorType.TREASURE
                net.drachi.cdde.data.EndFloorType.TREASURE -> net.drachi.cdde.data.EndFloorType.BOSS
                net.drachi.cdde.data.EndFloorType.BOSS -> net.drachi.cdde.data.EndFloorType.NORMAL
            }
            config.endFloorType = next
            rebuildContent()
        }
        globalRow2.child(endFloorBtn)
        contentFlow.child(globalRow2.margins(Insets.bottom(10)))

        val stairRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
        val stairDirBtn = Components.button(Component.literal("Stairs: " + config.stairDirection.name)) { btn ->
            config.stairDirection = if (config.stairDirection == net.drachi.cdde.data.StairDirection.DOWN) net.drachi.cdde.data.StairDirection.UP else net.drachi.cdde.data.StairDirection.DOWN
            rebuildContent()
        }
        stairRow.child(stairDirBtn.margins(Insets.right(10)))
        stairRow.child(createBlockPicker("Base Stair Block", config.stairBaseBlock, allowStairs = false) { config.stairBaseBlock = it; rebuildContent() }.margins(Insets.right(10)))
        stairRow.child(createBlockPicker("Stair Step Block", config.stairStepBlock, allowStairs = true, requireStairs = true) { config.stairStepBlock = it; rebuildContent() })
        contentFlow.child(stairRow.margins(Insets.bottom(10)))

        // Color Picker for Portal
        contentFlow.child(Components.label(Component.translatable("gui.cdde.config.portal_color")).margins(Insets.bottom(5)))
        val colorPicker = ColorPickerComponent().showAlpha(false)
        val initialColor = Color.ofRgb(config.portalColor)
        colorPicker.selectedColor(initialColor)
        colorPicker.sizing(Sizing.fixed(200), Sizing.fixed(100))
        colorPicker.onChanged().subscribe { c -> config.portalColor = c.rgb() }
        contentFlow.child(colorPicker.margins(Insets.bottom(15)))

        // 1.5 End Floor Config
        if (config.endFloorType != net.drachi.cdde.data.EndFloorType.NORMAL) {
            val endBox = Containers.verticalFlow(Sizing.fill(100), Sizing.content())
            endBox.surface(Surface.outline(0xFFFFFF00.toInt()))
            endBox.padding(Insets.of(10))
            endBox.margins(Insets.bottom(15))
            
            endBox.child(Components.label(Component.literal("End Floor Configuration (${config.endFloorType.name})")).margins(Insets.bottom(10)))
            
            val tRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            tRow.child(Components.label(Component.literal("Treasure Items:")).margins(Insets.right(10)))
            tRow.child(Components.button(Component.literal("+")) {
                config.endFloorConfig.treasure.add(net.drachi.cdde.data.ItemSpawnEntry())
                rebuildContent()
            })
            endBox.child(tRow.margins(Insets.bottom(5)))
            
            for (i in config.endFloorConfig.treasure.indices) {
                val spawn = config.endFloorConfig.treasure[i]
                val row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
                row.margins(Insets.bottom(2))
                
                val itemBtn = Components.button(Component.literal(spawn.item.split(":").lastOrNull()?.take(12) ?: spawn.item.take(12))) {
                    val allItems = net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().toList().map { net.drachi.cdde.client.ResourceSelectorScreen.ResourceEntry(it.toString(), net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(it))) }
                    openResourceSelector(Component.translatable("gui.cdde.config.select_item"), allItems) {
                        spawn.item = it
                        rebuildContent()
                    }
                }
                
                val weightBox = Components.textBox(Sizing.fixed(40))
                weightBox.text(spawn.weight.toString())
                weightBox.onChanged().subscribe { t -> spawn.weight = t.toIntOrNull() ?: 10 }

                val minBox = Components.textBox(Sizing.fixed(30))
                minBox.text(spawn.minAmount.toString())
                minBox.onChanged().subscribe { t -> spawn.minAmount = t.toIntOrNull() ?: 1 }

                val maxBox = Components.textBox(Sizing.fixed(30))
                maxBox.text(spawn.maxAmount.toString())
                maxBox.onChanged().subscribe { t -> spawn.maxAmount = t.toIntOrNull() ?: 1 }

                val delBtn = Components.button(Component.literal("X")) {
                    config.endFloorConfig.treasure.removeAt(i)
                    rebuildContent()
                }

                row.child(itemBtn.margins(Insets.right(5)))
                row.child(Components.label(Component.translatable("gui.cdde.config.weight")).margins(Insets.right(2)))
                row.child(weightBox.margins(Insets.right(5)))
                row.child(Components.label(Component.translatable("gui.cdde.config.amount")).margins(Insets.right(2)))
                row.child(minBox.margins(Insets.right(2)))
                row.child(Components.label(Component.literal("-")).margins(Insets.right(2)))
                row.child(maxBox.margins(Insets.right(5)))
                row.child(delBtn)

                endBox.child(row)
            }

            if (config.endFloorType == net.drachi.cdde.data.EndFloorType.BOSS) {
                // Boss
                val bRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
                bRow.child(Components.label(Component.literal("Boss Pokémon:")).margins(Insets.right(10)))
                bRow.child(Components.button(Component.literal("+")) {
                    config.endFloorConfig.boss.add(net.drachi.cdde.data.PokemonSpawnEntry())
                    rebuildContent()
                })
                endBox.child(bRow.margins(Insets.of(10, 0, 5, 0)))
                for (i in config.endFloorConfig.boss.indices) {
                    val spawn = config.endFloorConfig.boss[i]
                    val row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
                    row.margins(Insets.bottom(2))
                    
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
                            val entries = mutableListOf<net.drachi.cdde.client.ResourceSelectorScreen.ResourceEntry>()
                            try {
                                val allSpecies = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.implemented
                                allSpecies.forEach { species -> 
                                    try {
                                        val p = com.cobblemon.mod.common.api.pokemon.PokemonProperties.parse(species.name.lowercase()).create()
                                        val itemStack = com.cobblemon.mod.common.item.PokemonItem.from(p)
                                        entries.add(net.drachi.cdde.client.ResourceSelectorScreen.ResourceEntry(species.name.lowercase(), itemStack))
                                    } catch (e: Exception) {
                                        entries.add(net.drachi.cdde.client.ResourceSelectorScreen.ResourceEntry(species.name.lowercase()))
                                    }
                                }
                            } catch (e: Exception) {}
                            
                            openResourceSelector(Component.translatable("gui.cdde.config.select_pokemon"), entries) {
                                spawn.pokemon = it
                                rebuildContent()
                            }
                            true
                        } else false
                    }
                    
                    val weightBox = Components.textBox(Sizing.fixed(40))
                    weightBox.text(spawn.weight.toString())
                    weightBox.onChanged().subscribe { t -> spawn.weight = t.toIntOrNull() ?: 10 }

                    val minLvlBox = Components.textBox(Sizing.fixed(30))
                    minLvlBox.text(spawn.minLevel.toString())
                    minLvlBox.onChanged().subscribe { t -> spawn.minLevel = t.toIntOrNull() ?: 1 }

                    val maxLvlBox = Components.textBox(Sizing.fixed(30))
                    maxLvlBox.text(spawn.maxLevel.toString())
                    maxLvlBox.onChanged().subscribe { t -> spawn.maxLevel = t.toIntOrNull() ?: 50 }
                    
                    val recruitBox = Components.textBox(Sizing.fixed(30))
                    recruitBox.text(spawn.baseRecruitment.toString())
                    recruitBox.onChanged().subscribe { t -> spawn.baseRecruitment = t.toIntOrNull() ?: 0 }

                    val delBtn = Components.button(Component.literal("X")) {
                        config.endFloorConfig.boss.removeAt(i)
                        rebuildContent()
                    }

                    row.child(monWrapper.margins(Insets.right(5)))
                    row.child(Components.label(Component.translatable("gui.cdde.config.weight")).margins(Insets.right(2)))
                    row.child(weightBox.margins(Insets.right(5)))
                    row.child(Components.label(Component.translatable("gui.cdde.config.level")).margins(Insets.right(2)))
                    row.child(minLvlBox.margins(Insets.right(2)))
                    row.child(Components.label(Component.literal("-")).margins(Insets.right(2)))
                    row.child(maxLvlBox.margins(Insets.right(5)))
                    row.child(Components.label(Component.literal("Recruit:")).margins(Insets.right(2)))
                    row.child(recruitBox.margins(Insets.right(5)))
                    row.child(delBtn)

                    endBox.child(row)
                }
                
                // Minions
                val mRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
                mRow.child(Components.label(Component.literal("Minion Pokémon:")).margins(Insets.right(10)))
                mRow.child(Components.button(Component.literal("+")) {
                    config.endFloorConfig.minion.add(net.drachi.cdde.data.PokemonSpawnEntry())
                    rebuildContent()
                })
                endBox.child(mRow.margins(Insets.of(10, 0, 5, 0)))
                for (i in config.endFloorConfig.minion.indices) {
                    val spawn = config.endFloorConfig.minion[i]
                    val row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
                    row.margins(Insets.bottom(2))
                    
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
                            val entries = mutableListOf<net.drachi.cdde.client.ResourceSelectorScreen.ResourceEntry>()
                            try {
                                val allSpecies = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.implemented
                                allSpecies.forEach { species -> 
                                    try {
                                        val p = com.cobblemon.mod.common.api.pokemon.PokemonProperties.parse(species.name.lowercase()).create()
                                        val itemStack = com.cobblemon.mod.common.item.PokemonItem.from(p)
                                        entries.add(net.drachi.cdde.client.ResourceSelectorScreen.ResourceEntry(species.name.lowercase(), itemStack))
                                    } catch (e: Exception) {
                                        entries.add(net.drachi.cdde.client.ResourceSelectorScreen.ResourceEntry(species.name.lowercase()))
                                    }
                                }
                            } catch (e: Exception) {}
                            
                            openResourceSelector(Component.translatable("gui.cdde.config.select_pokemon"), entries) {
                                spawn.pokemon = it
                                rebuildContent()
                            }
                            true
                        } else false
                    }
                    
                    val weightBox = Components.textBox(Sizing.fixed(40))
                    weightBox.text(spawn.weight.toString())
                    weightBox.onChanged().subscribe { t -> spawn.weight = t.toIntOrNull() ?: 10 }

                    val minLvlBox = Components.textBox(Sizing.fixed(30))
                    minLvlBox.text(spawn.minLevel.toString())
                    minLvlBox.onChanged().subscribe { t -> spawn.minLevel = t.toIntOrNull() ?: 1 }

                    val maxLvlBox = Components.textBox(Sizing.fixed(30))
                    maxLvlBox.text(spawn.maxLevel.toString())
                    maxLvlBox.onChanged().subscribe { t -> spawn.maxLevel = t.toIntOrNull() ?: 50 }
                    
                    val recruitBox = Components.textBox(Sizing.fixed(30))
                    recruitBox.text(spawn.baseRecruitment.toString())
                    recruitBox.onChanged().subscribe { t -> spawn.baseRecruitment = t.toIntOrNull() ?: 0 }

                    val delBtn = Components.button(Component.literal("X")) {
                        config.endFloorConfig.minion.removeAt(i)
                        rebuildContent()
                    }

                    row.child(monWrapper.margins(Insets.right(5)))
                    row.child(Components.label(Component.translatable("gui.cdde.config.weight")).margins(Insets.right(2)))
                    row.child(weightBox.margins(Insets.right(5)))
                    row.child(Components.label(Component.translatable("gui.cdde.config.level")).margins(Insets.right(2)))
                    row.child(minLvlBox.margins(Insets.right(2)))
                    row.child(Components.label(Component.literal("-")).margins(Insets.right(2)))
                    row.child(maxLvlBox.margins(Insets.right(5)))
                    row.child(Components.label(Component.literal("Recruit:")).margins(Insets.right(2)))
                    row.child(recruitBox.margins(Insets.right(5)))
                    row.child(delBtn)

                    endBox.child(row)
                }
            }
            
            contentFlow.child(endBox)
        }

        // 2. Floor Rules
        contentFlow.child(Components.label(Component.translatable("gui.cdde.config.floor_rules_header")).margins(Insets.bottom(10)))
        
        for (i in config.floorRules.indices) {
            val rule = config.floorRules[i]
            val ruleBox = Containers.verticalFlow(Sizing.fill(100), Sizing.content())
            ruleBox.surface(Surface.outline(0xFF555555.toInt()))
            ruleBox.padding(Insets.of(10))
            ruleBox.margins(Insets.bottom(10))

            val headerRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            val ruleHeaderLabel = Components.label(Component.translatable("gui.cdde.config.rule_header", i)).margins(Insets.right(5))
            headerRow.child(ruleHeaderLabel)
            
            val rangeBox = Components.textBox(Sizing.fixed(100))
            rangeBox.text(rule.range)
            rangeBox.tooltip(Component.translatable("gui.cdde.config.range_tooltip"))
            rangeBox.onChanged().subscribe { rule.range = it }
            headerRow.child(rangeBox.margins(Insets.right(15)))

            val delBtn = Components.button(Component.translatable("gui.cdde.config.delete_rule")) {
                config.floorRules.removeAt(i)
                rebuildContent()
            }
            headerRow.child(delBtn)
            ruleBox.child(headerRow.margins(Insets.bottom(10)))

            // Min/Max Rooms & Prune
            ruleBox.child(createLabelInput("gui.cdde.config.min_rooms", rule.config.minRooms.toString()) { rule.config.minRooms = it.toIntOrNull() ?: 5 })
            ruleBox.child(createLabelInput("gui.cdde.config.max_rooms", rule.config.maxRooms.toString()) { rule.config.maxRooms = it.toIntOrNull() ?: 10 })
            ruleBox.child(createLabelInput("gui.cdde.config.prune_percent", rule.config.deadEndPrunePercent.toString()) { rule.config.deadEndPrunePercent = it.toIntOrNull() ?: 20 })

            // Palettes
            val palettesRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            palettesRow.child(createBlockPicker("block.cdde.palette_a", rule.config.paletteA) { rule.config.paletteA = it; rebuildContent() }.margins(Insets.right(10)))
            palettesRow.child(createBlockPicker("block.cdde.palette_b", rule.config.paletteB) { rule.config.paletteB = it; rebuildContent() }.margins(Insets.right(10)))
            palettesRow.child(createBlockPicker("block.cdde.palette_c", rule.config.paletteC) { rule.config.paletteC = it; rebuildContent() }.margins(Insets.right(10)))
            palettesRow.child(createBlockPicker("block.cdde.palette_d", rule.config.paletteD) { rule.config.paletteD = it; rebuildContent() })
            ruleBox.child(palettesRow.margins(Insets.bottom(10)))

            // Hazards
            val hazardRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            val currentHazard = rule.config.hazards.firstOrNull() ?: "minecraft:water"
            hazardRow.child(createBlockPicker("block.cdde.hazard", currentHazard, hazardsOnly = true) { 
                rule.config.hazards.clear()
                rule.config.hazards.add(it)
                rebuildContent() 
            })
            ruleBox.child(hazardRow.margins(Insets.bottom(10)))

            // Themes
            ruleBox.child(Components.label(Component.translatable("gui.cdde.config.themes_label")).margins(Insets.bottom(2)))
            val themesBox = Components.textBox(Sizing.fill(80))
            themesBox.text(rule.config.activeSets.joinToString(","))
            themesBox.onChanged().subscribe { 
                rule.config.activeSets = it.split(",").map { t -> t.trim() }.filter { t -> t.isNotEmpty() }.toMutableList() 
            }
            
            // "Add Theme" button parsing templates
            val addThemeBtn = Components.button(Component.translatable("gui.cdde.config.add_theme")) {
                val themes = net.drachi.cdde.data.DungeonManager.availableRooms.keys.map { ResourceSelectorScreen.ResourceEntry(it) }
                openResourceSelector(Component.translatable("gui.cdde.config.select_theme"), themes) { selectedTheme ->
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

        val addRuleBtn = Components.button(Component.translatable("gui.cdde.config.add_floor_rule")) {
            config.floorRules.add(FloorRule(range = "${config.floorRules.size + 1}"))
            rebuildContent()
        }
        contentFlow.child(addRuleBtn.margins(Insets.bottom(20)))

        // 3. Save / Cancel Buttons
        val btnRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
        btnRow.horizontalAlignment(HorizontalAlignment.CENTER)
        
        val saveBtn = Components.button(Component.translatable("gui.cdde.config.save")) {
            val json = Json { ignoreUnknownKeys = true }.encodeToString(config)
            net.drachi.cdde.network.NetworkHandler.CHANNEL.clientHandle().send(net.drachi.cdde.network.SaveConfigPacket(json))
            net.minecraft.client.Minecraft.getInstance().setScreen(null)
        }
        val cancelBtn = Components.button(Component.translatable("gui.cdde.config.cancel")) {
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
        flow.child(Components.label(Component.translatable("gui.cdde.config.pokemon_spawns")).margins(Insets.bottom(2)))
        
        for (i in config.pokemonSpawns.indices) {
            val spawn = config.pokemonSpawns[i]
            val row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            row.margins(Insets.bottom(2))
            
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
                        val allSpecies = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.implemented
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
                    
                    openResourceSelector(Component.translatable("gui.cdde.config.select_pokemon"), entries) {
                        spawn.pokemon = it
                        rebuildContent()
                    }
                    true
                } else false
            }
            
            val weightBox = Components.textBox(Sizing.fixed(40))
            weightBox.text(spawn.weight.toString())
            weightBox.onChanged().subscribe { spawn.weight = it.toIntOrNull() ?: 10 }
            
            val minLvlBox = Components.textBox(Sizing.fixed(30))
            minLvlBox.text(spawn.minLevel.toString())
            minLvlBox.onChanged().subscribe { spawn.minLevel = it.toIntOrNull() ?: 1 }

            val maxLvlBox = Components.textBox(Sizing.fixed(30))
            maxLvlBox.text(spawn.maxLevel.toString())
            maxLvlBox.onChanged().subscribe { spawn.maxLevel = it.toIntOrNull() ?: 50 }

            val recruitBox = Components.textBox(Sizing.fixed(35))
            recruitBox.text(spawn.baseRecruitment.toString())
            recruitBox.onChanged().subscribe { spawn.baseRecruitment = it.toIntOrNull() ?: 0 }

            val delBtn = Components.button(Component.literal("X")) {
                config.pokemonSpawns.removeAt(i)
                rebuildContent()
            }

            row.child(monWrapper.margins(Insets.right(5)))
            row.child(Components.label(Component.translatable("gui.cdde.config.weight")).margins(Insets.right(2)))
            row.child(weightBox.margins(Insets.right(5)))
            row.child(Components.label(Component.translatable("gui.cdde.config.level")).margins(Insets.right(2)))
            row.child(minLvlBox.margins(Insets.right(2)))
            row.child(Components.label(Component.literal("-")).margins(Insets.right(2)))
            row.child(maxLvlBox.margins(Insets.right(5)))
            row.child(Components.label(Component.translatable("gui.cdde.config.recruit")).margins(Insets.right(2)))
            row.child(recruitBox.margins(Insets.right(5)))
            row.child(delBtn)
            flow.child(row)
        }

        val addBtn = Components.button(Component.translatable("gui.cdde.config.add_pokemon")) {
            config.pokemonSpawns.add(PokemonSpawnEntry())
            rebuildContent()
        }
        flow.child(addBtn)
        return flow
    }

    private fun buildItemSpawnsUI(config: FloorConfig): io.wispforest.owo.ui.core.Component {
        val flow = Containers.verticalFlow(Sizing.fill(100), Sizing.content())
        flow.margins(Insets.bottom(10))
        flow.child(Components.label(Component.translatable("gui.cdde.config.item_spawns")).margins(Insets.bottom(2)))
        
        for (i in config.itemSpawns.indices) {
            val spawn = config.itemSpawns[i]
            val row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
            row.margins(Insets.bottom(2))
            
            val itemBtn = Components.button(Component.literal(spawn.item.split(":").lastOrNull()?.take(12) ?: spawn.item.take(12))) {
                val allItems = BuiltInRegistries.ITEM.keySet().toList().map { ResourceSelectorScreen.ResourceEntry(it.toString(), ItemStack(BuiltInRegistries.ITEM.get(it))) }
                openResourceSelector(Component.translatable("gui.cdde.config.select_item"), allItems) {
                    spawn.item = it
                    rebuildContent()
                }
            }
            
            val weightBox = Components.textBox(Sizing.fixed(40))
            weightBox.text(spawn.weight.toString())
            weightBox.onChanged().subscribe { spawn.weight = it.toIntOrNull() ?: 10 }
            
            val minBox = Components.textBox(Sizing.fixed(30))
            minBox.text(spawn.minAmount.toString())
            minBox.onChanged().subscribe { spawn.minAmount = it.toIntOrNull() ?: 1 }

            val maxBox = Components.textBox(Sizing.fixed(30))
            maxBox.text(spawn.maxAmount.toString())
            maxBox.onChanged().subscribe { spawn.maxAmount = it.toIntOrNull() ?: 1 }

            val delBtn = Components.button(Component.literal("X")) {
                config.itemSpawns.removeAt(i)
                rebuildContent()
            }

            row.child(itemBtn.margins(Insets.right(5)))
            row.child(Components.label(Component.translatable("gui.cdde.config.weight")).margins(Insets.right(2)))
            row.child(weightBox.margins(Insets.right(5)))
            row.child(Components.label(Component.translatable("gui.cdde.config.amount")).margins(Insets.right(2)))
            row.child(minBox.margins(Insets.right(2)))
            row.child(Components.label(Component.literal("-")).margins(Insets.right(2)))
            row.child(maxBox.margins(Insets.right(5)))
            row.child(delBtn)
            flow.child(row)
        }

        val addBtn = Components.button(Component.translatable("gui.cdde.config.add_item")) {
            config.itemSpawns.add(ItemSpawnEntry())
            rebuildContent()
        }
        flow.child(addBtn)
        return flow
    }

    private fun createLabelInput(translatableKey: String, value: String, onChanged: (String) -> Unit): io.wispforest.owo.ui.core.Component {
        val row = Containers.horizontalFlow(Sizing.content(), Sizing.content())
        row.margins(Insets.bottom(5))
        
        val label = Components.label(Component.translatable(translatableKey))
        val box = Components.textBox(Sizing.fixed(100))
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
                BuiltInRegistries.BLOCK.keySet().toList().filter { id ->
                    val block = BuiltInRegistries.BLOCK.get(id)
                    val isStair = block is net.minecraft.world.level.block.StairBlock
                    val isAir = block === net.minecraft.world.level.block.Blocks.AIR
                    if (id.namespace == "cdde") false
                    else if (isAir) false
                    else if (requireStairs) isStair
                    else if (!allowStairs && isStair) false
                    else {
                        val state = block.defaultBlockState()
                        net.minecraft.world.level.block.Block.isShapeFullBlock(state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO))
                    }
                }.map { ResourceSelectorScreen.ResourceEntry(it.toString(), ItemStack(BuiltInRegistries.BLOCK.get(it).asItem())) }
            }
            openResourceSelector(Component.translatable("gui.cdde.config.select_block_for", Component.translatable(translatableKey).string), validBlocks, onSelect)
        }
        row.child(btn)
        return row
    }

    private fun openResourceSelector(title: Component, entries: List<ResourceSelectorScreen.ResourceEntry>, onSelect: (String) -> Unit) {
        net.minecraft.client.Minecraft.getInstance().setScreen(ResourceSelectorScreen(this, entries, title, onSelect))
    }
}
