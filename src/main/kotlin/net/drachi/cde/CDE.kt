package net.drachi.cde

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory
import net.drachi.cde.config.ConfigManager

object CDE : ModInitializer {
    val logger = LoggerFactory.getLogger("cde")

    override fun onInitialize() {
        logger.info("Initializing CDE (Cobblemon Dungeon Engine) Base!")
        
        // 1. Initialize Global Config first
        ConfigManager.init()
        val modules = ConfigManager.globalConfig.modules

        // 1.5 Register Unified Network Payloads
        net.drachi.cde.network.NetworkHandler.registerPayloads()

        // 2. Validate Dependencies
        if (modules.aiEnabled && !modules.battleEngineEnabled) {
            logger.error("CRITICAL ERROR: AI Module is enabled, but Battle Engine is disabled!")
            logger.error("The AI Module requires the Battle Engine to function.")
            throw IllegalStateException("Invalid Module Configuration: AI Module requires Battle Engine.")
        }

        // 3. Initialize Enabled Modules
        if (modules.dungeonsEnabled) {
            logger.info("Module 'Dungeons Engine' is ENABLED. Loading...")
            net.drachi.cde.dungeonsengine.DungeonsEngineModule.init()
        } else {
            logger.info("Module 'Dungeons Engine' is DISABLED.")
        }

        if (modules.battleEngineEnabled) {
            logger.info("Module 'Battle Engine' is ENABLED. Loading...")
            net.drachi.cde.battleengine.BattleEngineModule.init()
        } else {
            logger.info("Module 'Battle Engine' is DISABLED.")
        }

        if (modules.aiEnabled) {
            logger.info("Module 'AI' is ENABLED. Loading...")
            net.drachi.cde.ai.AiModule.init()
        } else {
            logger.info("Module 'AI' is DISABLED.")
        }
    }
}
