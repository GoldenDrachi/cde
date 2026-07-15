package net.drachi.cdde.client

import io.wispforest.owo.ui.base.BaseOwoScreen
import io.wispforest.owo.ui.component.Components
import io.wispforest.owo.ui.container.Containers
import io.wispforest.owo.ui.container.FlowLayout
import io.wispforest.owo.ui.core.*
import net.minecraft.network.chat.Component
import net.drachi.cdde.network.NetworkHandler
import net.drachi.cdde.network.JoinDungeonRequestPayload

class DungeonJoinScreen(private val unlockedDungeons: List<String>) : BaseOwoScreen<FlowLayout>() {

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

        val title = Components.label(Component.literal("Select a Dungeon to Join").withStyle(net.minecraft.ChatFormatting.AQUA))
        title.margins(Insets.bottom(10))
        
        val searchBox = Components.textBox(Sizing.fixed(200))
        searchBox.margins(Insets.bottom(10))
        searchBox.onChanged().subscribe { text ->
            searchQuery = text.lowercase()
            refreshGrid()
        }

        contentContainer = Containers.verticalFlow(Sizing.content(), Sizing.content())
        contentContainer.horizontalAlignment(HorizontalAlignment.CENTER)

        val scroll = Containers.verticalScroll(Sizing.fixed(360), Sizing.fixed(220), contentContainer)
        scroll.surface(Surface.DARK_PANEL)
        scroll.padding(Insets.of(5))
        scroll.margins(Insets.bottom(10))

        val cancelBtn = Components.button(Component.translatable("gui.cdde.config.cancel")) {
            net.minecraft.client.Minecraft.getInstance().setScreen(null)
        }

        rootComponent.child(title)
        rootComponent.child(searchBox)
        rootComponent.child(scroll)
        rootComponent.child(cancelBtn)

        refreshGrid()
    }

    private fun refreshGrid() {
        contentContainer.clearChildren()
        
        val filtered = unlockedDungeons.filter { it.lowercase().contains(searchQuery) }
        
        if (filtered.isEmpty()) {
            val emptyLabel = Components.label(Component.literal("No unlocked dungeons found.").withStyle(net.minecraft.ChatFormatting.GRAY))
            emptyLabel.margins(Insets.top(10))
            contentContainer.child(emptyLabel)
            return
        }

        for (dungeonId in filtered) {
            val label = Components.label(Component.literal(dungeonId))
            val wrapper = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(24))
            wrapper.horizontalAlignment(HorizontalAlignment.CENTER)
            wrapper.verticalAlignment(VerticalAlignment.CENTER)
            wrapper.surface(Surface.flat(0x77000000.toInt()))
            wrapper.tooltip(Component.literal("Click to join $dungeonId"))
            wrapper.padding(Insets.of(0, 0, 10, 10))
            wrapper.child(label)
            wrapper.margins(Insets.bottom(4))
            
            wrapper.mouseDown().subscribe { _, _, button ->
                if (button == 0) {
                    NetworkHandler.CHANNEL.clientHandle().send(JoinDungeonRequestPayload(dungeonId))
                    net.minecraft.client.Minecraft.getInstance().setScreen(null)
                    true
                } else false
            }
            
            contentContainer.child(wrapper)
        }
    }
}
