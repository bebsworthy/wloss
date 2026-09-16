package app.wlo.core.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/**
 * The screen-level title (DESIGN-SYSTEM.md §2 `title-l` slot, WLO-0031): one
 * per screen, [WloTypography.titleL] at the standard top inset. The old
 * `wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f)` arithmetic
 * is gone — this is the only sanctioned screen-title.
 *
 * Sits inside the screen's scroll/padding layout; horizontal margins stay the
 * caller's (some screens inset more for the hub rail). Extra [modifier]
 * chaining (testTag, alignment) composes on top of the standard top padding.
 */
@Composable
public fun WloScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
): Unit =
    Text(
        text = title,
        style = wloType.titleL,
        modifier = modifier.semantics { heading() }.padding(top = WloSpacing.SCREEN),
    )
