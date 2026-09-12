package app.wlo.feature.f06.weight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.wlo.core.designsystem.ProvenanceChip
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.Provenance

/**
 * The in-app math documentation (F06 §3: "the math is in-app documentation",
 * Happy-Scale-FAQ style): each smoother's formula, its plain-language terms,
 * and its honestly documented failure modes — plus the outlier guard and the
 * lowest-of-day rule. Formula versions come from the constants registry, the
 * same strings the provenance chips resolve to.
 */
@Composable
public fun MathDocsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
    ) {
        Text(
            text = "How the trend math works",
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
            modifier = Modifier.padding(top = WloSpacing.SCREEN).testTag("f06-math-title"),
        )
        Text(
            text =
                "Every daily weight is the lowest reading of that day. The trend line then " +
                    "smooths those daily numbers with one of the methods below — yours is switchable " +
                    "any time, and every value on the chart carries the method that produced it.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MethodDoc(
            name = "Trend (trailing average with memory)",
            formula = "sₜ = α·xₜ + (1−α)·sₜ₋₁,   s₀ = x₀",
            terms =
                listOf(
                    "xₜ — today's lowest-of-day weight",
                    "sₜ₋₁ — yesterday's trend",
                    "α — how much today moves the line (your slider, default ${ConstantsRegistry.EWMA_ALPHA_DEFAULT})",
                ),
            plain =
                "Each day blends the new reading into the line: a low α keeps the line calm and " +
                    "slow, a high α lets it react. It only ever looks backward, so it lags a real " +
                    "change by a few days — that lag is the price of the stability.",
            formulaVersion = ConstantsRegistry.EWMA_FORMULA_VERSION,
            testTag = "f06-math-ewma",
        )

        MethodDoc(
            name = "Zero-phase",
            formula = "s = EWMA(EWMA_reversed(x)), α both passes",
            terms =
                listOf(
                    "the same filter run forward, then backward over its own output",
                    "α — your slider, same meaning as above",
                ),
            plain =
                "Runs the filter over the past and the future, so the line sits on top of the " +
                    "dots with almost no lag. The honest cost: the most recent days revise slightly " +
                    "as new readings land, and during a plateau the line can briefly settle a hair " +
                    "below any weight you actually reached. Nothing is hidden — the line just " +
                    "re-settles where the evidence is.",
            formulaVersion = ConstantsRegistry.EWMA_ZERO_PHASE_FORMULA_VERSION,
            testTag = "f06-math-zerophase",
        )

        MethodDoc(
            name = "7-day average",
            formula = "sₜ = mean(xₜ₋₆ … xₜ)",
            terms = listOf("a plain mean over the last seven daily numbers"),
            plain =
                "The simplest possible smoother: the week's average. Maximum transparency, " +
                    "maximum lag — a change takes about half a week to fully show up. The first " +
                    "days of a new series are means over whatever exists so far.",
            formulaVersion = ConstantsRegistry.MA7_FORMULA_VERSION,
            testTag = "f06-math-ma7",
        )

        MethodDoc(
            name = "The outlier flag",
            formula = "flag when |xₜ − trend| > 3σ of recent residuals",
            terms =
                listOf(
                    "residual — how far a reading sat from the trend that day",
                    "σ — the typical size of those recent wobbles",
                ),
            plain =
                "A reading far outside your usual day-to-day wobble gets one question — keep or " +
                    "correct — and is kept either way. A typo is data you fix; a real swing is data " +
                    "you keep. Nothing is ever dropped silently.",
            formulaVersion = ConstantsRegistry.OUTLIER_FORMULA_VERSION,
            testTag = "f06-math-outlier",
        )

        Text(
            text =
                "Recomputed on your device, from your data. Constants and formula versions live " +
                    "in one versioned registry, so any value on any chart can be traced to exactly " +
                    "what ran.",
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
            color = wloExtendedColors.textTertiary,
        )
    }
}

@Composable
private fun MethodDoc(
    name: String,
    formula: String,
    terms: List<String>,
    plain: String,
    formulaVersion: String,
    testTag: String,
): Unit =
    WloCard(modifier = Modifier.testTag(testTag)) {
        Text(text = name, style = wloType.title)
        Text(
            text = formula,
            style = wloType.statM.copy(fontSize = wloType.statS.fontSize),
            color = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
            for (term in terms) {
                Text(
                    text = term,
                    style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(text = plain, style = wloType.body)
        ProvenanceChip(
            value =
                DerivedValue(
                    0.0,
                    Provenance.Derived(formulaVersion = formulaVersion, inputs = emptyList()),
                ),
            format = { formulaVersion },
        )
    }
