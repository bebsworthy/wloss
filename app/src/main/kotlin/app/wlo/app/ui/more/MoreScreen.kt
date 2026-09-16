package app.wlo.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloIcons
import app.wlo.core.designsystem.WloSpacing

/**
 * The secondary feature directory from IA §1. It is intentionally a standard
 * Material 3 list: these are stable destinations, not dashboard cards or a
 * second navigation hierarchy.
 */
@Composable
public fun MoreScreen(
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    notice: String? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN),
    ) {
        notice?.let {
            WloBanner(text = it, tone = WloBannerTone.Info, modifier = Modifier.testTag("more-notice"))
        }
        app.wlo.core.designsystem.WloSettingsGroup {
            MoreDestination("Your profile", "Personal details used by WLO", "more-profile", onOpenProfile)
        }
        androidx.compose.foundation.layout
            .Spacer(Modifier.padding(top = WloSpacing.SCREEN))
        app.wlo.core.designsystem.WloSettingsGroup {
            MoreDestination("Settings", "Preferences, privacy and data", "more-settings", onOpenSettings)
        }
    }
}

@Composable
private fun MoreDestination(
    headline: String,
    supporting: String,
    tag: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting) },
        trailingContent = {
            Icon(
                imageVector = WloIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick).testTag(tag),
    )
}
