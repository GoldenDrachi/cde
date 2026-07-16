package net.drachi.cde.dungeonsengine.compat

import net.minecraft.resources.ResourceLocation

object WeatherCompat {
    fun applyWeather(weatherId: String?) {
        val dimLoc = ResourceLocation.fromNamespaceAndPath("cde", "dungeon")
        if (weatherId != null) {
            net.drachi.cde.battleengine.api.BattleEngineApi.setDimensionWeather(dimLoc, weatherId)
        } else {
            net.drachi.cde.battleengine.api.BattleEngineApi.clearDimensionWeather(dimLoc)
        }
    }
}
