package dev.sadeghtahani.rhinochat.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.sadeghtahani.rhinochat.demo.data.DemoNetworkSettings
import dev.sadeghtahani.rhinochat.demo.data.CacheResetStatus
import dev.sadeghtahani.rhinochat.demo.data.PersistenceProbeStatus
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme
import org.jetbrains.compose.resources.stringResource
import rhinochat.shared.generated.resources.*

@Composable
fun DemoControlsScreen(
    settings: DemoNetworkSettings,
    onSettingsChanged: (DemoNetworkSettings) -> Unit,
    onStart: () -> Unit,
    onResetCache: () -> Unit,
    cacheResetStatus: CacheResetStatus = CacheResetStatus.Idle,
    modifier: Modifier = Modifier,
    persistenceProbeStatus: PersistenceProbeStatus = PersistenceProbeStatus.Idle,
    onPrepareDraftProbe: () -> Unit = {},
    onScheduleRetryProbe: () -> Unit = {},
    onStopAppProcess: () -> Unit = {},
) {
    var advancedToolsExpanded by remember { mutableStateOf(false) }
    var confirmProcessStop by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.weight(1f).widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(Res.string.demo_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text(stringResource(Res.string.demo_controls), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
                        FaultToggle(stringResource(Res.string.demo_offline), stringResource(Res.string.demo_offline_detail), settings.offline) { onSettingsChanged(settings.copy(offline = it)) }
                        FaultToggle(stringResource(Res.string.demo_fail_sends), stringResource(Res.string.demo_fail_sends_detail), settings.failSends) { onSettingsChanged(settings.copy(failSends = it)) }
                        FaultToggle(stringResource(Res.string.demo_fail_history), stringResource(Res.string.demo_fail_history_detail), settings.failHistory) { onSettingsChanged(settings.copy(failHistory = it)) }
                        FaultToggle(stringResource(Res.string.demo_slow), stringResource(Res.string.demo_slow_detail), settings.slowNetwork) { onSettingsChanged(settings.copy(slowNetwork = it)) }
                        FaultToggle(stringResource(Res.string.demo_lost_reply), stringResource(Res.string.demo_lost_reply_detail), settings.loseFirstReply) { onSettingsChanged(settings.copy(loseFirstReply = it)) }
                        FaultToggle(stringResource(Res.string.demo_random), stringResource(Res.string.demo_random_detail), settings.randomFailures) { onSettingsChanged(settings.copy(randomFailures = it)) }
                    }
                }
                TextButton(onClick = { onSettingsChanged(DemoNetworkSettings()) }) { Text(stringResource(Res.string.demo_reset)) }
                OutlinedCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.demo_reset_cache_detail), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onResetCache, enabled = cacheResetStatus != CacheResetStatus.Working) {
                            Text(stringResource(Res.string.demo_reset_cache))
                        }
                        val statusLabel = when (cacheResetStatus) {
                            CacheResetStatus.Idle -> null
                            CacheResetStatus.Working -> Res.string.demo_cache_working
                            CacheResetStatus.Done -> Res.string.demo_cache_done
                            CacheResetStatus.Busy -> Res.string.demo_cache_busy
                            CacheResetStatus.Failed -> Res.string.demo_cache_failed
                        }
                        statusLabel?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
                    }
                }
                OutlinedCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { advancedToolsExpanded = !advancedToolsExpanded },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(stringResource(Res.string.demo_advanced_tools))
                        }
                        Text(
                            stringResource(Res.string.demo_advanced_tools_detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (advancedToolsExpanded) {
                            OutlinedButton(
                                onClick = onPrepareDraftProbe,
                                enabled = persistenceProbeStatus != PersistenceProbeStatus.Working,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(Res.string.demo_prepare_draft_probe)) }
                            Text(
                                stringResource(Res.string.demo_prepare_draft_probe_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = onScheduleRetryProbe,
                                enabled = persistenceProbeStatus != PersistenceProbeStatus.Working,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(Res.string.demo_schedule_retry_probe)) }
                            Text(
                                stringResource(Res.string.demo_schedule_retry_probe_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = { confirmProcessStop = true },
                                enabled = persistenceProbeStatus != PersistenceProbeStatus.Working,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(Res.string.demo_kill_process_probe)) }
                            Text(
                                stringResource(Res.string.demo_kill_process_probe_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val probeStatusLabel = when (persistenceProbeStatus) {
                                PersistenceProbeStatus.Idle -> null
                                PersistenceProbeStatus.Working -> Res.string.demo_probe_working
                                PersistenceProbeStatus.DraftPrepared -> Res.string.demo_probe_draft_ready
                                PersistenceProbeStatus.DraftAlreadyExists -> Res.string.demo_probe_draft_exists
                                PersistenceProbeStatus.RetriesScheduled -> Res.string.demo_probe_retries_scheduled
                                PersistenceProbeStatus.NoRetries -> Res.string.demo_probe_no_retries
                                PersistenceProbeStatus.Failed -> Res.string.demo_probe_failed
                            }
                            probeStatusLabel?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                Text(stringResource(Res.string.demo_return_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onStart, enabled = cacheResetStatus != CacheResetStatus.Working, modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(vertical = 16.dp).heightIn(min = 56.dp)) {
                Text(stringResource(Res.string.demo_start))
            }
        }
    }
    if (confirmProcessStop) {
        AlertDialog(
            onDismissRequest = { confirmProcessStop = false },
            title = { Text(stringResource(Res.string.demo_kill_process_title)) },
            text = { Text(stringResource(Res.string.demo_kill_process_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmProcessStop = false
                    onStopAppProcess()
                }) { Text(stringResource(Res.string.demo_kill_process_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmProcessStop = false }) {
                    Text(stringResource(Res.string.demo_kill_process_cancel))
                }
            },
        )
    }
}

@Composable
private fun FaultToggle(title: String, detail: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChecked).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Preview(name = "Test controls", widthDp = 390, heightDp = 840, showBackground = true)
@Composable
private fun DemoControlsPreview() {
    RhinoChatTheme { DemoControlsScreen(DemoNetworkSettings(), {}, {}, {}) }
}

@Preview(name = "Test controls · Dark", widthDp = 390, heightDp = 840, showBackground = true)
@Composable
private fun DemoControlsDarkPreview() {
    RhinoChatTheme(darkTheme = true) { DemoControlsScreen(DemoNetworkSettings(offline = true, loseFirstReply = true), {}, {}, {}) }
}
