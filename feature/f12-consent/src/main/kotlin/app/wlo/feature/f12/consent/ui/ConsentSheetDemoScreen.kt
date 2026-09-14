package app.wlo.feature.f12.consent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType

/**
 * The point-of-use consent sheet's demo surface (v1): the component is what
 * every future cloud call raises (BYOK is v1.x), so it ships inspectable —
 * a representative payload, the three-way decision, and the outcome line.
 * No ledger writes happen here: the demo renders the decision where a caller
 * would act on it.
 */
@Composable
public fun ConsentSheetDemoScreen(modifier: Modifier = Modifier) {
    var outcome by remember { mutableStateOf<String?>(null) }
    val payload =
        remember {
            ConsentSheetPayload(
                request = "Want a cloud boost for this scan?",
                categoryTitle = "Food photo",
                previewBytes =
                    run {
                        // A JPEG's magic + plausible downscaled head — a stand-in
                        // for the real downscaled crop a caller would render.
                        val head = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
                        head + ByteArray(40) { index -> ((index * 37) and 0xFF).toByte() }
                    },
                previewByteCount = 38_412,
                costBand = "≈ $0.004",
            )
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = WloSpacing.SCREEN)
                .testTag("f12-consent-demo"),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        WloScreenTitle(title = "Point-of-use consent")
        Text(
            text =
                "When a feature wants the cloud and consent is off, it asks here — in context, with " +
                    "the exact payload — never with a settings detour.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PointOfUseConsentSheet(
            payload = payload,
            onDecision = { decision ->
                outcome =
                    when (decision) {
                        ConsentSheetDecision.JUST_ONCE ->
                            "Access granted for 10 minutes — this scan uses the cloud and gets a receipt."
                        ConsentSheetDecision.ALWAYS ->
                            "Access granted — ${payload.categoryTitle} won't ask again until you turn it off."
                        ConsentSheetDecision.ON_DEVICE ->
                            "Staying on-device — the scan continues with the local estimate."
                    }
            },
        )
        outcome?.let {
            Text(
                text = "Decision: $it",
                style = wloType.body,
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.testTag("f12-consent-demo-outcome"),
            )
        }
    }
}
