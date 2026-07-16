package net.drachi.cde

import net.fabricmc.api.ClientModInitializer
import net.drachi.cde.config.ConfigManager

object CDEClient : ClientModInitializer {
    override fun onInitializeClient() {
        CDE.logger.info("Initializing CDE (Cobblemon Dungeon Engine) Client!")
        
        // We assume ConfigManager.init() was already called on the main initialize side,
        // but it doesn't hurt to ensure we have access to it, though GlobalConfig should be ready.
        // Wait, ClientModInitializer runs AFTER ModInitializer on the physical client, 
        // so ConfigManager.globalConfig is already loaded.
        
        val modules = ConfigManager.globalConfig.modules

        if (modules.dungeonsEnabled) {
            CDE.logger.info("Initializing Dungeons Engine Client...")
            net.drachi.cde.dungeonsengine.client.DungeonsEngineModuleClient.init()
        }

        if (modules.battleEngineEnabled) {
            CDE.logger.info("Initializing Battle Engine Client...")
            net.drachi.cde.battleengine.BattleEngineModuleClient.init()
        }
    }
}
