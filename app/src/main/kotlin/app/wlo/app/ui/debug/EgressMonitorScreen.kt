package app.wlo.app.ui.debug

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.network.EgressReceipt
import org.koin.androidx.compose.koinViewModel

/**
 * The debug egress monitor (F12 §3.8: "a debug build renders a live egress
 * monitor"). Reads the PERSISTED receipt ledger + zoo storage — honest
 * paperwork, not an in-memory counter. DEBUG builds only: any other build
 * lands here and gets the honest "debug diagnostics" stub.
 *
 * Acceptance 5's evidence surface: exactly the model downloads and (once PART
 * B ships them) OFF lookups, each with a receipt row and a per-purpose count.
 */
@Composable
public fun EgressMonitorScreen(viewModel: EgressMonitorViewModel = koinViewModel()) {
    val context = LocalContext.current
    val debuggable =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!debuggable) {
        Text(
            text = "Debug diagnostics — the egress monitor ships in debug builds only.",
            style = wloType.body,
            modifier = Modifier.padding(WloSpacing.SCREEN).testTag("egress-monitor-release-gate"),
        )
        return
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
    ) {
        item(key = "header") {
            Column {
                WloScreenTitle(title = "Egress monitor")
                Text(
                    text = "Every packet's paperwork, read from the on-device receipt ledger.",
                    style = wloType.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "counters") {
            Column(Modifier.testTag("egress-counters")) {
                state.countByPurpose.entries
                    .sortedBy { it.key.wireName }
                    .forEach { (purpose, count) ->
                        CounterRow(tag = "egress-count-${purpose.wireName}", label = purpose.wireName, count = count)
                    }
                if (state.countByPurpose.isEmpty()) {
                    Text(
                        text = "no egress yet — the ledger is empty",
                        style = wloType.label,
                        color = wloExtendedColors.textTertiary,
                        modifier = Modifier.testTag("egress-count-empty"),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = WloSpacing.TIGHT))
                Text(
                    text = "receipted bytes total: ${state.totalBytes} B · zoo on disk: ${state.zooStorageBytes} B",
                    style = wloType.label,
                    modifier = Modifier.testTag("egress-totals"),
                )
                HorizontalDivider(Modifier.padding(vertical = WloSpacing.TIGHT))
            }
        }
        items(state.receipts, key = { it.seq }) { receipt ->
            ReceiptRow(receipt)
        }
        item(key = "footer") {
            Text(
                text = "receipts are append-only and hash-chained — editing history is detectable",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.padding(bottom = WloSpacing.SCREEN),
            )
        }
    }
}

@Composable
private fun CounterRow(
    tag: String,
    label: String,
    count: Int,
) {
    WloListRow(
        label = label,
        value = { Text(text = count.toString(), style = wloType.receipt, modifier = Modifier.testTag(tag)) },
    )
}

@Composable
private fun ReceiptRow(receipt: EgressReceipt) {
    ListItem(
        modifier = Modifier.testTag("egress-receipt-${receipt.seq}"),
        headlineContent = {
            Text(text = "#${receipt.seq} ${receipt.purpose.wireName} → ${receipt.host}", style = wloType.receipt)
        },
        supportingContent = {
            Text(
                text =
                    "${receipt.operation} · ${receipt.bytes} B · ${receipt.outcome.wireName} · " +
                        "${receipt.atEpochMs} · hash ${receipt.hashHex.take(12)}…",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        },
    )
}
