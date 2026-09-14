package app.wlo.app.ui.zoo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.ai.ZooModelState
import app.wlo.core.designsystem.WloBadge
import app.wlo.core.designsystem.WloBadgeTone
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloProgress
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSecondaryButton
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
            Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                WloScreenTitle(title = "Model manager", modifier = Modifier.testTag("zoo-title"))
                Text(
                    text =
                        "No model ships in the APK — each downloads on first use " +
                            "from a hash-pinned URL, and works offline forever after.",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
                Text(
                    text = "Storage: ${formatBytes(state.storageBytes)}",
                    style = wloType.statM,
                    modifier = Modifier.testTag("zoo-storage"),
                )
                Text(
                    text = "In airplane mode nothing downloads, but anything already here keeps working.",
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
                WloBanner(
                    text = notice,
                    tone = WloBannerTone.Info,
                    actionLabel = "Dismiss",
                    action = { viewModel.onEvent(ZooEvent.DismissNotice) },
                    modifier = Modifier.testTag("zoo-notice"),
                )
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
                Text(modelDisplayName(model.id), style = wloType.title)
                Text(
                    "Serves: ${model.purpose} — on-device, consent-free",
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                )
            }
            StateBadge(card.state)
        }
        WloListRow(
            label = "Size",
            value = { Text(text = "${formatBytes(model.sizeBytes)} · ${model.format}", style = wloType.receipt) },
        )
        WloListRow(
            label = "License",
            value = { Text(text = model.license, style = wloType.receipt) },
        )
        WloListRow(
            label = "Trained on",
            value = { Text(text = model.provenance, style = wloType.receipt) },
        )
        WloListRow(
            label = "Verified by checksum",
            secondary = model.sha256Hex,
        )

        when (val zooState = card.state) {
            is ZooModelState.Downloading -> {
                val fraction =
                    if (zooState.totalBytes > 0) {
                        zooState.bytesSoFar.toFloat() / zooState.totalBytes.toFloat()
                    } else {
                        0f
                    }
                WloProgress(
                    progress = fraction,
                    label = "${formatBytes(zooState.bytesSoFar)} / ${formatBytes(zooState.totalBytes)}",
                    modifier = Modifier.testTag("zoo-progress-${model.id.replace("/", "-")}"),
                )
            }

            is ZooModelState.DownloadedVerified -> {
                Text("On device · ${formatBytes(zooState.sizeBytes)} · verified", style = wloType.receipt)
                WloSecondaryButton(
                    label = "Reclaim ${formatBytes(zooState.sizeBytes)}",
                    onClick = { onEvent(ZooEvent.Reclaim(model.id)) },
                    modifier = Modifier.fillMaxWidth().testTag("zoo-reclaim-${model.id.replace("/", "-")}"),
                    enabled = busyModelId == null,
                )
            }

            is ZooModelState.Corrupted ->
                Text(
                    "The downloaded file failed verification and was discarded — a re-download heals it.",
                    style = wloType.caption,
                    color = wloExtendedColors.held,
                )

            ZooModelState.NotDownloaded ->
                WloButton(
                    label = "Download (${formatBytes(model.sizeBytes)})",
                    onClick = { onEvent(ZooEvent.Download(model.id)) },
                    modifier = Modifier.fillMaxWidth().testTag("zoo-download-${model.id.replace("/", "-")}"),
                    enabled = busyModelId == null,
                )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
    }
}

/** The model card's live state as a status pill — color carries no alarm; words do. */
@Composable
private fun StateBadge(state: ZooModelState) {
    val (text, tone) =
        when (state) {
            is ZooModelState.DownloadedVerified -> "Ready" to WloBadgeTone.Accent
            is ZooModelState.Downloading -> "Downloading" to WloBadgeTone.Info
            is ZooModelState.Corrupted -> "Verify failed" to WloBadgeTone.Held
            ZooModelState.NotDownloaded -> "Not downloaded" to WloBadgeTone.Neutral
        }
    WloBadge(text = text, tone = tone, modifier = Modifier.testTag("zoo-state-text"))
}

/**
 * Human card title: the manifest's [app.wlo.core.ai.ZooModel] carries no
 * display-name field, so Title-Case the id's segments (`food-classifier/1` →
 * "Food Classifier 1").
 */
private fun modelDisplayName(id: String): String =
    id
        .split('/', '-', '_', '.')
        .filter { it.isNotBlank() }
        .joinToString(" ") { segment -> segment.replaceFirstChar { it.uppercaseChar() } }

/**
 * Byte counts, decimal (B → KB → MB, one decimal under 10) — the same math as
 * f12's and f13's internal formatters, so every screen reads bytes one way.
 * (The one shared home in :core is the follow-up; until then this file keeps
 * the identical convention — never the binary 1024 ladder used before
 * WLO-0031.)
 */
internal fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_000_000 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
