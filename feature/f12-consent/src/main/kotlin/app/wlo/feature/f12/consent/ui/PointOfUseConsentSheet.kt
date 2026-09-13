package app.wlo.feature.f12.consent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType

/**
 * The point-of-use consent sheet (F12 §3.5) — the decision happens where the
 * data is, informed by what will ACTUALLY be sent: the exact payload preview
 * (rendered bytes, byte count, cost band), then three buttons with the
 * on-device answer as the default-highlighted one:
 *
 *  - "Just this once"  — a 10-minute grant (v1.x caller glue records it);
 *  - "Always for <category>" — the standing ledger grant;
 *  - "Keep it on-device" (default) — no grant; the flow continues degraded.
 *
 * v1 ships the COMPONENT (cloud callers are v1.x); the demo surface hosts it
 * with a synthetic preview so the interaction is inspectable now. The
 * callbacks return the decision; the CALLER persists it — this composable
 * never writes the ledger itself (single-writer discipline).
 */
public enum class ConsentSheetDecision {
    /** One 10-minute grant, never persisted as "always". */
    JUST_ONCE,

    /** The standing grant for the category. */
    ALWAYS,

    /** Default: no cloud; the on-device path continues. */
    ON_DEVICE,
}

public data class ConsentSheetPayload(
    /** What the feature wants, in plain words ("a cloud boost for this scan"). */
    public val request: String,
    /** The category's display name ("Food photo"). */
    public val categoryTitle: String,
    /**
     * The exact bytes that would leave — a downscaled image or transcript —
     * rendered in the preview panel; null renders the honest empty panel.
     */
    public val previewBytes: ByteArray?,
    /** Byte count of the preview (receipted later as the declared size). */
    public val previewByteCount: Long,
    /** Cost band, e.g. "≈ $0.004" (F12 §3.3 geek candy). */
    public val costBand: String,
)

@Composable
public fun PointOfUseConsentSheet(
    payload: ConsentSheetPayload,
    onDecision: (ConsentSheetDecision) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = WloShape.SheetTop,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth().testTag("f12-consent-sheet"),
    ) {
        Column(
            Modifier.padding(WloSpacing.SCREEN),
            verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                Text(text = payload.request, style = wloType.title)
                Text(
                    text = "Category: ${payload.categoryTitle} · payload ${formatBytes(
                        payload.previewByteCount,
                    )} · cost ${payload.costBand}",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
            }
            PayloadPreview(bytes = payload.previewBytes)
            Text(
                text =
                    "Stripped before sending: location, timestamps, filenames. Cropped and downscaled " +
                        "to what the model needs.",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                OutlinedButton(
                    onClick = { onDecision(ConsentSheetDecision.JUST_ONCE) },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("f12-consent-once"),
                ) {
                    Text("Just this once (10 minutes)", style = wloType.label)
                }
                OutlinedButton(
                    onClick = { onDecision(ConsentSheetDecision.ALWAYS) },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("f12-consent-always"),
                ) {
                    Text("Always for ${payload.categoryTitle}", style = wloType.label)
                }
                // The default-highlighted answer: keep it local (F12 §3.5).
                Button(
                    onClick = { onDecision(ConsentSheetDecision.ON_DEVICE) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("f12-consent-ondevice"),
                ) {
                    Text("Keep it on-device", style = wloType.label)
                }
            }
        }
    }
}

/** The honest preview: the real bytes, rendered — never a stock illustration. */
@Composable
private fun PayloadPreview(bytes: ByteArray?) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = wloExtendedColors.surfaceSunken,
        modifier = Modifier.fillMaxWidth().height(140.dp).testTag("f12-consent-preview"),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(WloSpacing.PAD_CARD)) {
            if (bytes == null) {
                Text(
                    text =
                        "The payload preview renders here — the actual downscaled image or transcript, " +
                            "before anything moves.",
                    style = wloType.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                    Text(
                        text = "The exact bytes, as they would travel:",
                        style = wloType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = bytes.copyOfRange(0, minOf(HEX_DUMP_BYTES, bytes.size)).toHexStringGrouped(),
                        style = wloType.receipt,
                        color = wloExtendedColors.textTertiary,
                    )
                }
            }
        }
    }
}

/** F12 §8 [v1] "the real bytes, rendered": a hex dump of the payload head. */
private const val HEX_DUMP_BYTES: Int = 48

private fun ByteArray.toHexStringGrouped(): String =
    this
        .joinToString(separator = " ") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
        .uppercase()
