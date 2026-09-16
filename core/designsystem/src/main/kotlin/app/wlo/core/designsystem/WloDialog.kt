package app.wlo.core.designsystem

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Thin Material 3 [AlertDialog] wrapper (WLO-0031): WLO card shape and
 * surface, ramp type, and the destructive-confirm convention (held amber —
 * no alarm-red exists, R-D1). Unconfirmed destructive actions are a defect;
 * route them through here.
 *
 * @param title dialog title
 * @param text optional body copy
 * @param confirmLabel confirm copy ("Remove", sentence case); null = no confirm
 * @param onConfirm confirm handler; invoked only via [confirmLabel]
 * @param dismissLabel dismiss copy; null = single-action dialog
 * @param onDismiss dismissal handler (scrim, back, dismiss button)
 * @param destructive true tints the confirm action [WloExtendedColors.held]
 */
@Composable
public fun WloDialog(
    title: String,
    text: String? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
): Unit =
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = title, style = wloType.title) },
        text =
            text?.let { copyLine ->
                {
                    Text(text = copyLine, style = wloType.body)
                }
            },
        confirmButton = {
            if (confirmLabel != null) {
                TextButton(onClick = { onConfirm?.invoke() }) {
                    Text(
                        text = confirmLabel,
                        color =
                            if (destructive) {
                                wloExtendedColors.held
                            } else {
                                Color.Unspecified
                            },
                    )
                }
            }
        },
        dismissButton = {
            if (dismissLabel != null) {
                TextButton(onClick = { onDismiss?.invoke() }) {
                    Text(text = dismissLabel)
                }
            }
        },
    )
