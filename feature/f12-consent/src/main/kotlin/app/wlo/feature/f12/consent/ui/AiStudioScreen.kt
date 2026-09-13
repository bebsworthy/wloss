package app.wlo.feature.f12.consent.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.feature.f12.consent.state.AiStudioViewModel
import app.wlo.feature.f12.consent.state.ConsentRowState

/**
 * The AI Studio (F12 §3.4): the global kill switch above the six frozen
 * consent rows (R-C1), then the BYOK provider shell (v1.x — structure and
 * honest badge only, R-C7: no nag, no "connect now!" prompts) and the ADR-008
 * diagnostics section. Micro-interactions per DESIGN-SYSTEM §6: the row
 * shield-closes with a short spring and the kill switch flips every glyph in
 * a staggered wave; reduced motion collapses to instant.
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
        Text(
            text = "AI Studio",
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
            modifier = Modifier.padding(top = WloSpacing.SCREEN).testTag("f12-title"),
        )
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
            text = "Revoking is instant and keeps working mid-call — the next rung is always the on-device path.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            modifier = Modifier.padding(top = WloSpacing.TIGHT),
        ) {
            Surface(
                shape = WloShape.Chip,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier =
                    Modifier
                        .heightIn(min = 40.dp)
                        .clickable(onClick = onOpenReceipts)
                        .padding(0.dp)
                        .testTag("f12-open-receipts"),
            ) {
                Text(
                    text = "Receipt log",
                    style = wloType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Surface(
                shape = WloShape.Chip,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier =
                    Modifier
                        .heightIn(min = 40.dp)
                        .clickable(onClick = onOpenConsentDemo)
                        .testTag("f12-open-consent-demo"),
            ) {
                Text(
                    text = "Preview the consent sheet",
                    style = wloType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
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
    Surface(
        shape = WloShape.Card,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag("f12-kill-switch-card"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(WloSpacing.PAD_CARD),
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = "Cloud: ${if (on) "OFF" else "per category"}", style = wloType.title)
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
            }
            Switch(
                checked = on,
                onCheckedChange = onToggle,
                colors =
                    SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = wloExtendedColors.surfaceSunken,
                    ),
                modifier = Modifier.testTag("f12-kill-switch"),
            )
        }
    }
}

/** One consent row: shield glyph, category, explainer, status chip, toggle. */
@Composable
private fun ConsentRow(
    row: ConsentRowState,
    onToggle: () -> Unit,
    onOpenModelCard: (() -> Unit)?,
) {
    // The staggered "glyph wave" when the kill switch lands: per-row delay.
    val target = if (row.granted) 1f else 0.35f
    val shield by animateFloatAsState(
        targetValue = target,
        animationSpec =
            spring(
                dampingRatio = 0.7f,
                stiffness = 380f,
            ),
        label = "shield",
    )
    val shieldColor by animateColorAsState(
        targetValue = if (row.granted) wloExtendedColors.accentDim else wloExtendedColors.textTertiary,
        label = "shieldColor",
    )
    Surface(
        shape = WloShape.Card,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag("f12-row-${row.capability.wireName}"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(WloSpacing.PAD_CARD),
        ) {
            // The shield: closed (filled, accent) = local-only; open (hollow)
            // = cloud allowed. Color carries no alarm — copy carries state.
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .background(shieldColor.copy(alpha = 0.18f), WloShape.Chip),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(16.dp)
                            .alpha(shield)
                            .background(shieldColor, WloShape.Pill),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = WloSpacing.CARD),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                ) {
                    Text(text = row.title, style = wloType.title)
                    if (row.zooLink && onOpenModelCard != null) {
                        Surface(
                            shape = WloShape.Pill,
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier =
                                Modifier
                                    .clickable(onClick = onOpenModelCard)
                                    .testTag("f12-model-card-${row.capability.wireName}"),
                        ) {
                            Text(
                                text = "model zoo",
                                style = wloType.label,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
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
                StatusChip(status = if (row.granted) row.status else "off")
            }
            Switch(
                checked = row.granted,
                onCheckedChange = { onToggle() },
                enabled = row.enabled,
                colors =
                    SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = wloExtendedColors.surfaceSunken,
                    ),
                modifier = Modifier.testTag("f12-toggle-${row.capability.wireName}"),
            )
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    Surface(
        shape = WloShape.Chip,
        color = wloExtendedColors.surfaceSunken,
        modifier = Modifier.padding(top = WloSpacing.TIGHT),
    ) {
        Text(
            text = status,
            style = wloType.label,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * The BYOK shell (F12 §3.3): provider list + key field structure, INERT in v1
 * (cloud AI is v1.x). The badge says so; there is deliberately no "set up
 * now" nag (R-C7).
 */
@Composable
private fun ByokSection() {
    Surface(
        shape = WloShape.Card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag("f12-byok"),
    ) {
        Column(Modifier.padding(WloSpacing.PAD_CARD), verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                Text(text = "Bring your own key", style = wloType.title)
                Surface(shape = WloShape.Chip, color = wloExtendedColors.surfaceSunken) {
                    Text(
                        text = "v1.x",
                        style = wloType.label,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
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
                    Surface(shape = WloShape.Chip, color = wloExtendedColors.surfaceSunken) {
                        Text(
                            text = provider,
                            style = wloType.label,
                            color = wloExtendedColors.textTertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
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
    Surface(
        shape = WloShape.Card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag("f12-diagnostics"),
    ) {
        Column(Modifier.padding(WloSpacing.PAD_CARD), verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = "Crash diagnostics", style = wloType.title)
                    Text(
                        text =
                            "Off. If you turn it on, a crash sends a scrubbed, content-free report: app " +
                                "version, Android version, phone model, and a stack trace with only WLO's own " +
                                "frames kept — no logs, no data, no identifiers. It goes to an endpoint YOU " +
                                "choose (or a documented GitHub-Issues recipe). WLO runs no crash server.",
                        style = wloType.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors =
                        SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = wloExtendedColors.surfaceSunken,
                        ),
                    modifier = Modifier.testTag("f12-diagnostics-toggle"),
                )
            }
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
}
