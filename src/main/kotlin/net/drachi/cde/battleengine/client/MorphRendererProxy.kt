package net.drachi.cde.battleengine.client

import net.minecraft.world.entity.Entity

object MorphRendererProxy {
    var fakeEntityProvider: (Entity) -> Entity? = { null }
}
