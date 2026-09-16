package app.wlo.core.designsystem

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One destination of [WloBottomBar]. */
public data class WloTabItem(
    /** Route key matched against [WloBottomBar]'s `selected`. */
    val route: String,
    /** Resting glyph. */
    val icon: ImageVector,
    /** Optional selected-state glyph; falls back to [icon]. */
    val selectedIcon: ImageVector? = null,
    /** Tab copy — short, sentence case. */
    val label: String,
)

/**
 * The app bottom bar (WLO-0031), re-based on Material 3
 * [NavigationBar]/[NavigationBarItem] — kills the hand-rolled clickable
 * Columns in the shell. Selection color follows the chip convention (primary
 * on a 0.12 wash); haptics stay caller-side ([WloHaptics]).
 *
 * @param selected route of the active tab
 * @param onSelect called with the tapped tab's route; navigation stays the
 *   caller's business
 * @param items destinations in display order
 */
@Composable
public fun WloBottomBar(
    selected: String,
    onSelect: (String) -> Unit,
    items: List<WloTabItem>,
    modifier: Modifier = Modifier,
): Unit =
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        items.forEach { item ->
            val isSelected: Boolean = item.route == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(item.route) },
                icon = {
                    Icon(
                        imageVector =
                            if (isSelected) {
                                item.selectedIcon ?: item.icon
                            } else {
                                item.icon
                            },
                        contentDescription = item.label,
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = wloType.label,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ),
            )
        }
    }

/** Expanded-window counterpart to [WloBottomBar], using Material 3 navigation rail anatomy. */
@Composable
public fun WloNavigationRail(
    selected: String,
    onSelect: (String) -> Unit,
    items: List<WloTabItem>,
    modifier: Modifier = Modifier,
): Unit =
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        items.forEach { item ->
            val isSelected = item.route == selected
            NavigationRailItem(
                selected = isSelected,
                onClick = { onSelect(item.route) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon ?: item.icon else item.icon,
                        contentDescription = item.label,
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = wloType.label,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
