package app.wlo.feature.f12.consent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloSettingsGroup
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloSwitchRow
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.feature.f12.consent.state.AiStudioViewModel

/** AI configuration keeps cloud permission distinct from local model readiness. */
@Composable
public fun AiStudioScreen(
    viewModel: AiStudioViewModel,
    onOpenReceipts: () -> Unit,
    onOpenModelManager: (() -> Unit)?,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(WloSpacing.SCREEN)
            .testTag("f12-studio"),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        WloSettingsGroup {
            onOpenModelManager?.let {
                WloListRow(
                    label = "On-device models",
                    secondary = "Downloads, installed models and storage",
                    chevron = true,
                    onClick = it,
                    modifier = Modifier.testTag("f12-model-manager"),
                )
            }
            WloListRow(
                label = "Activity history",
                secondary = "Requests sent or blocked",
                chevron = true,
                onClick = onOpenReceipts,
                modifier = Modifier.testTag("f12-open-receipts"),
            )
        }
        WloSettingsGroup("Cloud access") {
            WloSwitchRow(
                label = "Block cloud AI",
                checked = state.killSwitch,
                secondary =
                    if (state.killSwitch) {
                        "All categories blocked. Local models are unaffected."
                    } else {
                        "Cloud permission is managed separately for each category."
                    },
                onCheckedChange = viewModel::setKillSwitch,
                modifier = Modifier.testTag("f12-kill-switch"),
            )
        }
        Text(
            "These switches control cloud permission, not whether a local model is installed. " +
                "Cloud providers are not available in this version.",
            style = wloType.receipt,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WloSettingsGroup("Permission by capability") {
            state.rows.forEach { row ->
                WloSwitchRow(
                    label = row.title,
                    secondary =
                        if (state.killSwitch) {
                            if (row.granted) {
                                "Permission saved · blocked globally"
                            } else {
                                "Cloud access off · blocked globally"
                            }
                        } else if (row.granted) {
                            "Cloud permission saved · provider unavailable"
                        } else {
                            "Cloud access off"
                        },
                    checked = row.granted,
                    enabled = row.enabled,
                    onCheckedChange = { viewModel.toggle(row.capability) },
                    modifier = Modifier.testTag("f12-toggle-${row.capability.wireName}"),
                )
            }
        }
    }
}

/** Separate privacy setting; not an AI capability. */
@Composable
public fun DiagnosticsScreen(viewModel: AiStudioViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(WloSpacing.SCREEN)) {
        DiagnosticsSection(
            state.diagnosticsEnabled,
            state.diagnosticsEndpoint,
            viewModel::setDiagnostics,
            viewModel::setDiagnosticsEndpoint,
        )
    }
}

@Composable
private fun DiagnosticsSection(
    enabled: Boolean,
    endpoint: String,
    onToggle: (Boolean) -> Unit,
    onEndpointChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
        Text(
            text =
                "Crash reporting is ${if (enabled) "on" else "off"}. Reports contain app " +
                    "version, Android version, phone model, and a stack trace with only WLO's own " +
                    "frames kept — no logs, no data, no identifiers. It goes to an endpoint you " +
                    "choose (or a documented GitHub-Issues recipe). WLO runs no crash server.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WloSwitchRow(
            label = "Send content-free crash reports",
            checked = enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.testTag("f12-diagnostics-toggle"),
        )
        OutlinedTextField(
            value = endpoint,
            onValueChange = onEndpointChange,
            enabled = true,
            singleLine = true,
            label = { Text("Report endpoint (empty = reports go nowhere)") },
            placeholder = { Text("https://…") },
            modifier = Modifier.fillMaxWidth().testTag("f12-diagnostics-endpoint"),
        )
        Text(
            text = "Attempts are recorded in activity history.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
        )
    }
}
