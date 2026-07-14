package net.drachi.cdde.client

import io.wispforest.owo.ui.base.BaseOwoScreen
import io.wispforest.owo.ui.component.Components
import io.wispforest.owo.ui.container.Containers
import io.wispforest.owo.ui.container.FlowLayout
import io.wispforest.owo.ui.core.*
import net.minecraft.network.chat.Component
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack

class ResourceSelectorScreen(
    private val parent: net.minecraft.client.gui.screens.Screen,
    private val entries: List<ResourceEntry>,
    private val title: Component,
    private val onSelect: (String) -> Unit
) : BaseOwoScreen<FlowLayout>() {

    data class ResourceEntry(val id: String, val icon: ItemStack? = null)

    private var searchQuery = ""
    private lateinit var contentContainer: FlowLayout

    override fun createAdapter(): OwoUIAdapter<FlowLayout> {
        return OwoUIAdapter.create(this, Containers::verticalFlow)
    }

    override fun build(rootComponent: FlowLayout) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT)
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER)
            .padding(Insets.of(20))

        val searchBox = Components.textBox(Sizing.fixed(200))
        searchBox.margins(Insets.bottom(10))
        searchBox.onChanged().subscribe { text ->
            searchQuery = text.lowercase()
            refreshGrid()
        }

        contentContainer = Containers.verticalFlow(Sizing.content(), Sizing.content())
        contentContainer.horizontalAlignment(HorizontalAlignment.LEFT)

        val scroll = Containers.verticalScroll(Sizing.fixed(320), Sizing.fixed(220), contentContainer)
        scroll.surface(Surface.DARK_PANEL)
        scroll.padding(Insets.of(5))
        scroll.margins(Insets.bottom(10))

        val cancelBtn = Components.button(Component.translatable("gui.cdde.config.cancel")) {
            net.minecraft.client.Minecraft.getInstance().setScreen(parent)
        }

        rootComponent.child(Components.label(title).margins(Insets.bottom(5)))
        rootComponent.child(searchBox)
        rootComponent.child(scroll)
        rootComponent.child(cancelBtn)

        refreshGrid()
    }

    private fun refreshGrid() {
        contentContainer.clearChildren()
        
        val sorted = entries.sortedBy { it.id }
        val filtered = sorted.filter { it.id.lowercase().contains(searchQuery) }.take(300)
        
        val itemsPerRow = 10
        val rows = filtered.chunked(itemsPerRow)

        for (rowItems in rows) {
            val rowFlow = Containers.horizontalFlow(Sizing.content(), Sizing.content())
            rowFlow.margins(Insets.bottom(2))
            
            for (entry in rowItems) {
                val element = if (entry.icon != null && !entry.icon.isEmpty) {
                    val itemComp = Components.item(entry.icon)
                    itemComp.sizing(Sizing.fixed(24))
                    itemComp.margins(Insets.of(2))
                    itemComp.tooltip(Component.literal(entry.id))
                    itemComp
                } else {
                    val label = Components.label(Component.literal(entry.id.split(":").lastOrNull()?.take(8) ?: entry.id.take(8)))
                    val wrapper = Containers.horizontalFlow(Sizing.fixed(30), Sizing.fixed(24))
                    wrapper.horizontalAlignment(HorizontalAlignment.CENTER)
                    wrapper.verticalAlignment(VerticalAlignment.CENTER)
                    wrapper.surface(Surface.flat(0x77000000.toInt()))
                    wrapper.tooltip(Component.literal(entry.id))
                    wrapper.child(label)
                    wrapper.margins(Insets.of(2))
                    wrapper
                }
                
                element.mouseDown().subscribe { _, _, button ->
                    if (button == 0) {
                        onSelect(entry.id)
                        net.minecraft.client.Minecraft.getInstance().setScreen(parent)
                        true
                    } else false
                }
                
                rowFlow.child(element)
            }
            contentContainer.child(rowFlow)
        }
    }
}
