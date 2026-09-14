package app.wlo.feature.f12.consent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.common.formatBytes
import app.wlo.core.designsystem.WloBadge
import app.wlo.core.designsystem.WloBadgeTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardAccent
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.feature.f12.consent.state.ReceiptLine
import app.wlo.feature.f12.consent.state.ReceiptsViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The AI receipt log (F12 §3.6) — the user-facing sibling of the debug egress
 * monitor: one ledger line per departure (purpose, host, bytes, time,
 * outcome), a running total ("N receipts left this device"), and the "verify
 * chain" action: the hash-chain walk that proves nothing was edited after the
 * fact. Zero receipts is rendered as the GOOD state it is.
 */
@Composable
public fun ReceiptsScreen(viewModel: ReceiptsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = WloSpacing.SCREEN)
                .testTag("f12-receipts"),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
    ) {
        item(key = "header") {
            Column {
                WloScreenTitle(title = "AI receipts")
                Text(
                    text = "Every byte's paperwork. Summaries only — the payloads themselves never enter this log.",
                    style = wloType.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "totals") {
            Column(Modifier.padding(top = WloSpacing.TIGHT).testTag("f12-receipts-totals")) {
                Text(
                    text = "${state.lines.size} receipts left this device",
                    style = wloType.statS,
                )
                if (state.countByPurpose.isNotEmpty()) {
                    Text(
                        text =
                            state.countByPurpose.entries
                                .sortedBy { it.key }
                                .joinToString(" · ") { "${purposeLabel(it.key)}: ${it.value}" },
                        style = wloType.receipt,
                        color = wloExtendedColors.textTertiary,
                    )
                }
            }
        }
        item(key = "verify") {
            Column(
                Modifier.padding(vertical = WloSpacing.CARD),
                verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                WloButton(
                    label = if (state.verifying) "Verifying…" else "Verify chain",
                    onClick = viewModel::verifyChain,
                    enabled = !state.verifying,
                    modifier = Modifier.testTag("f12-verify-chain"),
                )
                val verdict = state.chainVerdict
                if (verdict != null) {
                    WloCard(
                        modifier = Modifier.testTag("f12-chain-verdict"),
                        accent = if (verdict.intact) WloCardAccent.Primary else WloCardAccent.Warning,
                    ) {
                        Text(
                            text =
                                if (verdict.intact) {
                                    "Chain intact — all ${verdict.checked} receipt(s) verified"
                                } else {
                                    "Chain broken at receipt #${verdict.brokenAtSeq ?: "?"} — " +
                                        "this log was edited or corrupted"
                                },
                            style = wloType.title,
                            color =
                                if (verdict.intact) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    wloExtendedColors.held
                                },
                        )
                        Text(
                            text =
                                if (verdict.intact) {
                                    "Each receipt embeds the previous one's hash back to the very first " +
                                        "entry. Recomputing every hash reproduces the chain exactly, so " +
                                        "nothing in this ledger was changed after the fact."
                                } else {
                                    "A link or hash failed to reproduce. Either the file was tampered " +
                                        "with, or storage corrupted a row. Treat everything after the " +
                                        "break as unproven."
                                },
                            style = wloType.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider()
        }
        if (state.lines.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.padding(vertical = WloSpacing.SCREEN).testTag("f12-receipts-empty")) {
                    Text(text = "No receipts — nothing has left this device.", style = wloType.title)
                    Text(
                        text =
                            "That is the normal state, not a missing feature. When a model download, a " +
                                "food-database lookup, or a consented cloud call runs, its paperwork " +
                                "appears here.",
                        style = wloType.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(state.lines.asReversed(), key = { it.seq }) { line ->
            ReceiptRow(line)
        }
        item(key = "footer") {
            Text(
                text =
                    "Append-only and hash-chained. WLO cannot quietly rewrite this history — and " +
                        "neither could anyone else.",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.padding(bottom = WloSpacing.SCREEN),
            )
        }
    }
}

@Composable
private fun ReceiptRow(line: ReceiptLine) {
    val at =
        Instant
            .fromEpochMilliseconds(line.atEpochMs)
            .toLocalDateTime(TimeZone.currentSystemDefault())
    Column(Modifier.fillMaxWidth().testTag("f12-receipt-${line.seq}")) {
        ListItem(
            leadingContent = {
                WloBadge(text = purposeLabel(line.purposeWire), tone = WloBadgeTone.Neutral)
            },
            headlineContent = { Text(text = line.host, style = wloType.statS) },
            supportingContent = {
                Text(
                    text =
                        "#${line.seq} · ${line.operation} · ${outcomeLabel(line.outcomeWire)} · ${formatBytes(
                            line.bytes,
                        )} · ${at.date} ${at.time.toString().substringBefore('.')}",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
            },
        )
        HorizontalDivider(
            Modifier.padding(top = WloSpacing.TIGHT),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        )
    }
}

/** Wire purpose → user words (F12 §3.6: paperwork reads like sentences, not wire). */
internal fun purposeLabel(purposeWire: String): String =
    when (purposeWire) {
        "zoo-download" -> "Model download"
        "off-lookup" -> "Address lookup"
        "diagnostics" -> "Crash report"
        else ->
            if (purposeWire.startsWith("future-cloud-")) {
                "Cloud " + wireWords(purposeWire.removePrefix("future-cloud-"))
            } else {
                wireWords(purposeWire)
            }
    }

/** Wire outcome → user words; "ok" keeps the geek's OK. */
internal fun outcomeLabel(outcomeWire: String): String =
    when (outcomeWire) {
        "ok" -> "OK"
        "denied" -> "Denied"
        "failed" -> "Failed"
        "cache-hit" -> "Cached"
        else -> wireWords(outcomeWire)
    }

/** `hash-pinned-url` → "Hash Pinned Url" — the fallback for unseen wire values. */
private fun wireWords(wire: String): String =
    wire
        .split('-', '_', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
