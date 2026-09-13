package app.wlo.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The WLO modal bottom sheet (WLO-0031): Material 3 [ModalBottomSheet] with
 * the §1.4 `SheetTop` 28 dp radius, the `surface` container (no extra tint),
 * and the standard inner padding — [WloSpacing.SCREEN] horizontal and bottom,
 * [WloSpacing.CARD] between children. Features stop hand-applying the sheet
 * shape and re-deriving the insets per screen.
 *
 * The default M3 drag handle stays. Dismissal (scrim tap, back, drag) flows
 * through [onDismissRequest]. The wrapper carries the
 * [ExperimentalMaterial3Api] opt-in so callers never see it.
 *
 * @param onDismissRequest called when the user dismisses the sheet
 * @param content sheet body, [ColumnScope] so callers can weight/space
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun WloSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
): Unit =
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = WloShape.SheetTop,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WloSpacing.SCREEN)
                    .padding(bottom = WloSpacing.SCREEN),
            verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
            content = content,
        )
    }
