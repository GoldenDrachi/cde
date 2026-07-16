package net.drachi.cde.dungeonsengine.network

import net.drachi.cde.config.*

@JvmRecord
data class OpenConfigScreenPacket(val configId: String, val configJson: String)

@JvmRecord
data class SaveConfigPacket(val configJson: String)
