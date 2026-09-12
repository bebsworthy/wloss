package app.wlo.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * THE shared month heatmap (R-D4: one implementation, reused by F05/F09/F11;
 * the diary-density instance lives on the Hub's diary card). Sequential tints
 * only — a viridis-family 5-step ramp, monotonic lightness so it survives
 * grayscale, color-blind safe, and containing no red (§1.3: sequential scales
 * are viridis/magma-family; §1.2: red does not exist in WLO).
 *
 * Month-paginated (§6 chart rules); gaps render as faint dots (R-U13 —
 * missing data is never a filled zero); an all-zero day is a hairline cell.
 * Each cell announces purpose + result ("September 8, logged 1,900 kcal").
 */
@Composable
public fun WloMonthHeatmap(
    values: Map<Long, Double>,
    initialMonth: LocalDate,
    describeCell: (LocalDate, Double?) -> String,
    modifier: Modifier = Modifier,
    onDayClick: ((LocalDate) -> Unit)? = null,
    /** Days after this epoch day render empty (the future is not a gap). */
    upTo: Long? = null,
) {
    var month by remember(initialMonth) { mutableStateOf(initialMonth) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chevron(text = "‹", description = "previous month") { month = month.minus(1, DateTimeUnit.MONTH) }
            Text(
                text =
                    month.month.name
                        .lowercase()
                        .replaceFirstChar { it.uppercase() } + " " + month.year,
                style = wloType.label,
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Chevron(text = "›", description = "next month") { month = month.plus(1, DateTimeUnit.MONTH) }
        }

        WeekdayHeader()

        val firstOfGrid = month.firstDayOfGrid()
        val weeks = weeksInGrid(month)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(weeks) { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { weekday ->
                        val date = firstOfGrid.plus(week * 7 + weekday, DateTimeUnit.DAY)
                        val epochDay = date.toEpochDays().toLong()
                        val inMonth = date.month == month.month && (upTo == null || epochDay <= upTo)
                        val raw = if (date.month == month.month) values[epochDay] else null
                        HeatCell(
                            date = date,
                            intensity = raw,
                            inMonth = inMonth,
                            describe = describeCell,
                            onClick = if (inMonth) onDayClick?.let { cb -> { cb(date) } } else null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Chevron(
    text: String,
    description: String,
    onClick: () -> Unit,
): Unit =
    Text(
        text = text,
        style = wloType.title,
        color = wloExtendedColors.textTertiary,
        modifier =
            Modifier
                .padding(horizontal = WloSpacing.CARD)
                .clickable(onClick = onClick)
                .semantics { contentDescription = description },
    )

@Composable
private fun WeekdayHeader(): Unit =
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        for (day in WEEKDAY_LETTERS) {
            Text(
                text = day,
                style = wloType.label.copy(fontSize = 10.sp),
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.weight(1f),
            )
        }
    }

@Composable
private fun HeatCell(
    date: LocalDate,
    intensity: Double?,
    inMonth: Boolean,
    describe: (LocalDate, Double?) -> String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val fill =
        when {
            !inMonth -> Color.Transparent
            intensity == null -> Color.Transparent
            intensity <= 0.0 -> MaterialTheme.colorScheme.surface
            else -> RAMP[cellLevel(intensity)]
        }
    val shape = WloShape.Chip
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .background(fill, shape)
                .then(
                    if (inMonth && intensity == null) {
                        val faint = wloExtendedColors.textTertiary.copy(alpha = 0.35f)
                        Modifier.padding(7.dp).background(faint, CircleShape)
                    } else {
                        Modifier
                    },
                ).then(
                    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
                ).semantics { contentDescription = describe(date, if (inMonth) intensity else null) },
    )
}

/** Ramp index for an intensity in 0..1 — steps, not a continuous blend (§7.1: 4–5 steps). */
internal fun cellLevel(intensity: Double): Int {
    val scaled = intensity.coerceIn(0.0, 1.0) * RAMP.size
    return scaled.toInt().coerceIn(0, RAMP.size - 1)
}

/**
 * Viridis-family 5 steps (dark → light), the §1.3 sequential ramp instance:
 * monotonic lightness, no red channel spike, CVD-safe.
 */
private val RAMP: List<Color> =
    listOf(
        Color(0xFF3B1F5E),
        Color(0xFF453781),
        Color(0xFF3B6E8F),
        Color(0xFF2E918C),
        Color(0xFF52C569),
    )

private val WEEKDAY_LETTERS: List<String> = listOf("M", "T", "W", "T", "F", "S", "S")

/** Monday-first grid origin (Plan week grid convention, IA §2). */
private fun LocalDate.firstDayOfGrid(): LocalDate {
    val iso = dayOfWeek.isoDayNumber // 1 = Monday .. 7 = Sunday
    return this.minus(iso - 1, DateTimeUnit.DAY)
}

private fun weeksInGrid(month: LocalDate): Int {
    val first = month.firstDayOfGrid()
    val span = first.daysUntil(month.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)) + 1
    return (span + 6) / 7
}
