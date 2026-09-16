package app.wlo.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A diet-template gallery card (F01 §3 step 4): the card names its rules in
 * plain language on its face; press springs to 0.94× (F01 §4 motion), and
 * selection lights the accent hairline + the ideal-halo ring. No template is
 * privileged — the gallery is data, the card renders data.
 */
@Composable
public fun WloTemplateCard(
    name: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    macroDots: List<WloMacroDot> = emptyList(),
    ruleChips: List<String> = emptyList(),
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = WloMotion.Springs.Snap,
        label = "template-press",
    )
    val selectionBorder =
        if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    val description = "$name. $summary"

    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.semantics {
                    contentDescription = description
                    this.selected = selected
                },
        shape = WloShape.Card,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        border = selectionBorder,
    ) {
        Column(Modifier.padding(horizontal = WloSpacing.PAD_CARD, vertical = WloSpacing.CARD)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
            ) {
                Text(
                    text = name,
                    style = wloType.title.copy(fontSize = wloType.title.fontSize * 0.94f),
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Text(
                        text = "selected",
                        style = wloType.label,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(WloSpacing.TIGHT))
            Text(
                text = summary,
                style = wloType.body.copy(fontSize = 12.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (macroDots.isNotEmpty()) {
                Spacer(Modifier.height(WloSpacing.TIGHT))
                Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                    macroDots.forEach { dot ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(9.dp)
                                        .background(dot.color, CircleShape),
                            )
                            Spacer(Modifier.width(WloSpacing.TIGHT))
                            Text(
                                text = dot.label,
                                style = wloType.receipt,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (ruleChips.isNotEmpty()) {
                Spacer(Modifier.height(WloSpacing.TIGHT))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ruleChips.forEach { chip ->
                        Surface(
                            shape = WloShape.Chip,
                            color = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            Text(
                                text = chip,
                                style = wloType.label,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One macro-series dot: series color + label ("P 30 %"). */
public data class WloMacroDot(
    public val label: String,
    public val color: Color,
)

private const val PRESSED_SCALE = 0.94f
