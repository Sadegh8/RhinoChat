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

enum class PersistenceProbeStatus {
    Idle,
    Working,
    DraftPrepared,
    DraftAlreadyExists,
    RetriesScheduled,
    NoRetries,
    Failed,
}

/** App-owned controls; changing them affects the next request and never clears saved messages. */
class DemoNetworkControls(
    initialSettings: DemoNetworkSettings = DemoNetworkSettings(),
    private val persistSettings: (DemoNetworkSettings) -> Unit = {},
) {
    private val mutableSettings = MutableStateFlow(initialSettings)
    val settings = mutableSettings.asStateFlow()

    fun update(settings: DemoNetworkSettings) {
        mutableSettings.value = settings
        persistSettings(settings)
    }
}
