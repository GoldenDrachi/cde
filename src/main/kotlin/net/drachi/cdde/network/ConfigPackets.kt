package net.drachi.cdde.network

@JvmRecord
data class OpenConfigScreenPacket(val configId: String, val configJson: String)

@JvmRecord
data class SaveConfigPacket(val configJson: String)
