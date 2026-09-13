package app.wlo.app.ui.zoo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.ai.ZooModelState
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import org.koin.androidx.compose.koinViewModel

/**
 * The model manager screen (`wlo://ai/models`, F12 §3.2): one card per zoo
 * model — purpose, size BEFORE download, license, training-data provenance
 * (R-S12), live state, storage — with download/reclaim wired to the
 * ZooManager and an airplane-mode note. The capture flow's missing-model
 * state deep-links here.
 */
@Composable
public fun ZooScreen(viewModel: ZooViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        item(key = "header") {
            Column(Modifier.padding(top = WloSpacing.SCREEN)) {
                Text(
                    text = "Model manager",
                    style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
                    modifier = Modifier.testTag("zoo-title"),
                )
                Text(
                    text =
                        "no model ships in the APK — each downloads on first use " +
                            "from a hash-pinned URL, and works offline forever after",
                    style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                    color = wloExtendedColors.textTertiary,
                )
                Text(
                    text = "zoo storage: ${formatBytes(state.storageBytes)}",
                    style = wloType.statM,
                    modifier = Modifier.testTag("zoo-storage"),
                )
                Text(
                    text = "in airplane mode nothing downloads, but anything already here keeps working",
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                    modifier = Modifier.testTag("zoo-airplane-note"),
                )
            }
        }

        items(state.cards, key = { it.model.id }) { card ->
            ModelCardRow(card, state.busyModelId) { event -> viewModel.onEvent(event) }
        }

        state.notice?.let { notice ->
            item(key = "notice") {
                Surface(
                    onClick = { viewModel.onEvent(ZooEvent.DismissNotice) },
                    modifier = Modifier.fillMaxWidth().testTag("zoo-notice"),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        notice,
                        style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                        modifier = Modifier.padding(WloSpacing.CARD),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelCardRow(
    card: ZooViewModel.ModelCard,
    busyModelId: String?,
    onEvent: (ZooEvent) -> Unit,
) {
    val model = card.model
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = WloSpacing.TIGHT)
                .testTag("zoo-card-${model.id.replace("/", "-")}"),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(model.id, style = wloType.title)
                Text(
                    "serves: ${model.purpose} (on-device, consent-free)",
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                )
            }
            StateBadge(card.state)
        }
        Text("size: ${formatBytes(model.sizeBytes)} · ${model.format}", style = wloType.receipt)
        Text("license: ${model.license}", style = wloType.receipt)
        Text("trained on: ${model.provenance}", style = wloType.receipt, color = wloExtendedColors.textTertiary)
        Text(
            "sha256 ${model.sha256Hex.take(16)}… — verified before install",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
        )

        when (val zooState = card.state) {
            is ZooModelState.Downloading -> {
                val fraction =
                    if (zooState.totalBytes > 0) {
                        zooState.bytesSoFar.toFloat() / zooState.totalBytes.toFloat()
                    } else {
                        0f
                    }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().testTag("zoo-progress-${model.id.replace("/", "-")}"),
                )
                Text(
                    "${formatBytes(zooState.bytesSoFar)} / ${formatBytes(zooState.totalBytes)}",
                    style = wloType.receipt,
                )
            }

            is ZooModelState.DownloadedVerified -> {
                Text("on device · ${formatBytes(zooState.sizeBytes)} · verified", style = wloType.receipt)
                ActionButton(
                    label = "reclaim ${formatBytes(zooState.sizeBytes)}",
                    enabled = busyModelId == null,
                    tag = "zoo-reclaim-${model.id.replace("/", "-")}",
                ) { onEvent(ZooEvent.Reclaim(model.id)) }
            }

            is ZooModelState.Corrupted ->
                Text(
                    "the downloaded file failed verification and was discarded — a re-download heals it",
                    style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                    color = wloExtendedColors.held,
                )

            ZooModelState.NotDownloaded ->
                ActionButton(
                    label = "download (${formatBytes(model.sizeBytes)})",
                    enabled = busyModelId == null,
                    tag = "zoo-download-${model.id.replace("/", "-")}",
                ) { onEvent(ZooEvent.Download(model.id)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
    }
}

@Composable
private fun StateBadge(state: ZooModelState) {
    val (text, color) =
        when (state) {
            is ZooModelState.DownloadedVerified -> "ready" to MaterialTheme.colorScheme.primary
            is ZooModelState.Downloading -> "downloading" to MaterialTheme.colorScheme.primary
            is ZooModelState.Corrupted -> "failed verify" to wloExtendedColors.held
            ZooModelState.NotDownloaded -> "not downloaded" to wloExtendedColors.textTertiary
        }
    Text(
        text,
        style = wloType.label,
        color = color,
        modifier = Modifier.testTag("zoo-state-text"),
    )
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            label,
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 0.85f),
            modifier = Modifier.padding(horizontal = WloSpacing.CARD, vertical = WloSpacing.CARD),
        )
    }
}

internal fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1 shl 20 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1 shl 10 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
