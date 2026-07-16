package net.drachi.cde.dungeonsengine.client

import io.wispforest.owo.ui.base.BaseOwoScreen
import io.wispforest.owo.ui.component.Components
import io.wispforest.owo.ui.container.Containers
import io.wispforest.owo.ui.container.FlowLayout
import io.wispforest.owo.ui.core.*
import net.drachi.cde.network.DungeonResultPayload
import net.minecraft.network.chat.Component

class DungeonResultScreen(private val payload: DungeonResultPayload) : BaseOwoScreen<FlowLayout>() {

    override fun createAdapter(): OwoUIAdapter<FlowLayout> {
        return OwoUIAdapter.create(this, Containers::verticalFlow)
    }

    override fun build(rootComponent: FlowLayout) {
        rootComponent
            .surface(Surface.VANILLA_TRANSLUCENT)
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER)

        val mainPanel = Containers.verticalFlow(Sizing.content(), Sizing.content())
        mainPanel.surface(Surface.DARK_PANEL)
        mainPanel.padding(Insets.of(20))
        mainPanel.horizontalAlignment(HorizontalAlignment.CENTER)
        mainPanel.verticalAlignment(VerticalAlignment.CENTER)

        // Title
        mainPanel.child(
            Components.label(Component.translatable("gui.cde.result.title"))
                .shadow(true)
                .margins(Insets.bottom(20))
        )

        // Place
        mainPanel.child(
            Components.label(Component.translatable("gui.cde.result.place", payload.dungeonName))
                .margins(Insets.bottom(10))
        )

        // Party
        mainPanel.child(
            Components.label(Component.literal(payload.partyName))
                .margins(Insets.bottom(10))
        )

        // Message
        mainPanel.child(
            Components.label(Component.translatable(payload.messageKey))
                .margins(Insets.bottom(20))
        )

        // Close Button
        val closeBtn = Components.button(Component.translatable("gui.cde.result.close")) {
            net.minecraft.client.Minecraft.getInstance().setScreen(null)
        }.margins(Insets.top(10))

        mainPanel.child(closeBtn)

        rootComponent.child(mainPanel)
    }
}
