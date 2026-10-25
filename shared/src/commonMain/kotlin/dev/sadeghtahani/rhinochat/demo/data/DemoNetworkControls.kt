package dev.sadeghtahani.rhinochat.demo.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DemoNetworkSettings(
    val offline: Boolean = false,
    val failSends: Boolean = false,
    val failHistory: Boolean = false,
    val slowNetwork: Boolean = false,
    val loseFirstReply: Boolean = false,
    val randomFailures: Boolean = false,
)

enum class CacheResetStatus { Idle, Working, Done, Busy, Failed }

/** Process-owned controls; changing them affects the next request, never clears saved messages. */
class DemoNetworkControls {
    private val mutableSettings = MutableStateFlow(DemoNetworkSettings())
    val settings = mutableSettings.asStateFlow()
    fun update(settings: DemoNetworkSettings) { mutableSettings.value = settings }
}
