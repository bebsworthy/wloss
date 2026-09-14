package app.wlo.feature.f12.consent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import app.wlo.core.designsystem.WloBadge
import app.wlo.core.designsystem.WloBadgeTone
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardAccent
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloSwitchRow
import app.wlo.core.designsystem.WloTag
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.feature.f12.consent.state.AiStudioViewModel
import app.wlo.feature.f12.consent.state.ConsentRowState

/**
 * The AI Studio (F12 §3.4): the global kill switch above the six frozen
 * consent rows (R-C1), then the BYOK provider shell (v1.x — structure and
 * honest badge only, R-C7: no nag, no "connect now!" prompts) and the ADR-008
 * diagnostics section. State color lives in badges and the copy, never in an
 * alarm glyph: each row's status pill carries Off / On-device, per §8.
 */
@Composable
public fun AiStudioScreen(
    viewModel: AiStudioViewModel,
    onOpenReceipts: () -> Unit,
    onOpenModelManager: (() -> Unit)?,
    onOpenConsentDemo: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .testTag("f12-studio"),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        WloScreenTitle(title = "AI Studio", modifier = Modifier.testTag("f12-title"))
        Text(
            text =
                "Everything here works on this device by default. Nothing leaves your phone unless you " +
                    "switch it on — and every departure gets a receipt.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        KillSwitchCard(
            on = state.killSwitch,
            grantedCount = state.grantedCount,
            onToggle = viewModel::setKillSwitch,
        )

        Text(
            text = "Consent",
            style = wloType.title,
            modifier = Modifier.padding(top = WloSpacing.TIGHT),
        )
        state.rows.forEach { row ->
            ConsentRow(
                row = row,
                onToggle = { viewModel.toggle(row.capability) },
                onOpenModelCard = onOpenModelManager,
            )
        }
        Text(
            text = "Revoking is instant and keeps working mid-call.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            modifier = Modifier.padding(top = WloSpacing.TIGHT),
        ) {
            WloSecondaryButton(
                label = "Receipt log",
                onClick = onOpenReceipts,
                modifier = Modifier.testTag("f12-open-receipts"),
            )
            WloSecondaryButton(
                label = "Preview the consent sheet",
                onClick = onOpenConsentDemo,
                modifier = Modifier.weight(1f).testTag("f12-open-consent-demo"),
            )
        }

        ByokSection()
        DiagnosticsSection(
            enabled = state.diagnosticsEnabled,
            endpoint = state.diagnosticsEndpoint,
            onToggle = viewModel::setDiagnostics,
            onEndpointChange = viewModel::setDiagnosticsEndpoint,
        )
        Text(
            text = "WLO runs no servers and keeps no accounts. These switches only ever describe your device.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
            modifier = Modifier.padding(bottom = WloSpacing.SCREEN),
        )
    }
}

/**
 * The kill switch (F12 §3.4: "Cloud: OFF — nothing can leave the device").
 * Copy is the honest one: nothing transmits by default, so this is one-tap
 * insurance — never framed as urgent (R-C7, zero-guilt posture).
 */
@Composable
private fun KillSwitchCard(
    on: Boolean,
    grantedCount: Int,
    onToggle: (Boolean) -> Unit,
) {
    WloCard(
        modifier = Modifier.fillMaxWidth().testTag("f12-kill-switch-card"),
        accent = if (on) WloCardAccent.Primary else WloCardAccent.None,
        header = { WloCardHeader(title = if (on) "Cloud: off" else "Cloud: per category") },
    ) {
        Text(
            text =
                if (on) {
                    "Nothing can leave the device. Every AI capability runs on-device."
                } else {
                    "Nothing transmits by default — nothing has ever needed this switch. " +
                        if (grantedCount > 0) {
                            "$grantedCount categories may use the cloud."
                        } else {
                            "No category may use the cloud."
                        }
                },
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WloSwitchRow(
            label = "Keep everything on this device",
            checked = on,
            onCheckedChange = onToggle,
            modifier = Modifier.testTag("f12-kill-switch"),
        )
    }
}

/** One consent row: category header + status pill, explainer, zoo link, toggle. */
@Composable
private fun ConsentRow(
    row: ConsentRowState,
    onToggle: () -> Unit,
    onOpenModelCard: (() -> Unit)?,
) {
    WloCard(
        modifier = Modifier.fillMaxWidth().testTag("f12-row-${row.capability.wireName}"),
        header = {
            WloCardHeader(
                title = row.title,
                provenance = {
                    WloBadge(
                        text = consentStatusLabel(row),
                        tone = if (row.granted) WloBadgeTone.Accent else WloBadgeTone.Neutral,
                    )
                },
            )
        },
    ) {
        Text(
            text = row.oneLiner,
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = row.fallbackNote,
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
        )
        if (row.zooLink && onOpenModelCard != null) {
            WloListRow(
                label = "Model zoo",
                chevron = true,
                onClick = onOpenModelCard,
                modifier = Modifier.testTag("f12-model-card-${row.capability.wireName}"),
            )
        }
        WloSwitchRow(
            label = "Use the cloud for this category",
            checked = row.granted,
            onCheckedChange = { onToggle() },
            modifier = Modifier.testTag("f12-toggle-${row.capability.wireName}"),
        )
    }
}

/** Wire status → user words (the matrix reads like sentences, not wire). */
private fun consentStatusLabel(row: ConsentRowState): String =
    when {
        !row.granted -> "Off"
        row.status == "on-device" -> "On-device"
        row.status == "cloud" -> "On · cloud"
        else -> "On"
    }

/**
 * The BYOK shell (F12 §3.3): provider list + key field structure, INERT in v1
 * (cloud AI is v1.x). The tag says so; there is deliberately no "set up now"
 * nag (R-C7).
 */
@Composable
private fun ByokSection() {
    WloCard(
        modifier = Modifier.fillMaxWidth().testTag("f12-byok"),
        header = { WloCardHeader(title = "Bring your own key", provenance = { WloTag(text = "v1.x") }) },
    ) {
        Text(
            text =
                "Your key, your provider, your cost. When this lands you'll paste a key, run a " +
                    "connection test, and pick models per category — keys stay in the keystore and " +
                    "never enter exports.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
            for (provider in listOf("OpenAI-compatible", "Gemini", "Anthropic", "Ollama / llama.cpp")) {
                WloTag(text = provider)
            }
        }
        OutlinedTextField(
            value = "",
            onValueChange = {},
            enabled = false,
            singleLine = true,
            label = { Text("API key (inactive in this version)") },
            modifier = Modifier.fillMaxWidth().testTag("f12-byok-key-field"),
        )
    }
}

/**
 * Diagnostics (ADR-008 / T-K4): opt-in, CONTENT-FREE crash reports. Ships
 * OFF; the endpoint ships empty = unusable until set; the explainer states
 * exactly what a report contains — no content, no logs, no identifiers.
 */
@Composable
private fun DiagnosticsSection(
    enabled: Boolean,
    endpoint: String,
    onToggle: (Boolean) -> Unit,
    onEndpointChange: (String) -> Unit,
) {
    WloCard(
        modifier = Modifier.fillMaxWidth().testTag("f12-diagnostics"),
        header = { WloCardHeader(title = "Crash diagnostics") },
    ) {
        Text(
            text =
                "Off. If you turn it on, a crash sends a scrubbed, content-free report: app " +
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
            text = "Every attempt — sent or refused — gets a receipt you can audit below.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
        )
    }
}
