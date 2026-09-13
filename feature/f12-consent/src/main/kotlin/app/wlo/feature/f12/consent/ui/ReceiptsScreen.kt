package app.wlo.feature.f12.consent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.WloShape
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
 * outcome), a running byte total ("38 KB left your phone this month"), and
 * the "verify chain" action: the hash-chain walk that proves nothing was
 * edited after the fact. Zero receipts is rendered as the GOOD state it is.
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
            Column(Modifier.padding(top = WloSpacing.SCREEN)) {
                Text(
                    text = "AI receipts",
                    style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
                )
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
                    text = "${formatBytes(state.totalBytes)} left this device · ${state.lines.size} receipt(s) shown",
                    style = wloType.statS,
                )
                if (state.countByPurpose.isNotEmpty()) {
                    Text(
                        text =
                            state.countByPurpose.entries
                                .sortedBy { it.key }
                                .joinToString(" · ") { "${it.key}: ${it.value}" },
                        style = wloType.receipt,
                        color = wloExtendedColors.textTertiary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        item(key = "verify") {
            Column(
                Modifier.padding(vertical = WloSpacing.CARD),
                verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                Button(
                    onClick = viewModel::verifyChain,
                    enabled = !state.verifying,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("f12-verify-chain"),
                ) {
                    Text(text = if (state.verifying) "Verifying…" else "Verify chain", style = wloType.label)
                }
                val verdict = state.chainVerdict
                if (verdict != null) {
                    Surface(
                        shape = WloShape.Card,
                        color = MaterialTheme.colorScheme.surface,
                        border =
                            BorderStroke(
                                1.dp,
                                if (verdict.intact) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            ),
                        modifier = Modifier.fillMaxWidth().testTag("f12-chain-verdict"),
                    ) {
                        Column(
                            Modifier.padding(WloSpacing.PAD_CARD),
                            verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                        ) {
                            Text(
                                text =
                                    if (verdict.intact) {
                                        "Chain intact — all ${verdict.checked} receipt(s) verified"
                                    } else {
                                        "Chain BROKEN at receipt #${verdict.brokenAtSeq ?: "?"} — " +
                                            "this log was edited or corrupted"
                                    },
                                style = wloType.title,
                                color =
                                    if (verdict.intact) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
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
    Column(Modifier.fillMaxWidth().padding(vertical = WloSpacing.TIGHT).testTag("f12-receipt-${line.seq}")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        ) {
            Surface(shape = WloShape.Chip, color = wloExtendedColors.surfaceSunken) {
                Text(
                    text = line.purposeWire,
                    style = wloType.label,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(
                text = line.host,
                style = wloType.statS,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "#${line.seq} · ${line.operation} · ${line.outcomeWire} · ${formatBytes(
                line.bytes,
            )} · ${at.date} ${at.time.toString().substringBefore('.')}",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
            fontFamily = FontFamily.Monospace,
        )
        HorizontalDivider(
            Modifier.padding(top = WloSpacing.TIGHT),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        )
    }
}

/** Byte counts the way a geek reads them (B → KB → MB, one decimal under 10). */
internal fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_000_000 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
