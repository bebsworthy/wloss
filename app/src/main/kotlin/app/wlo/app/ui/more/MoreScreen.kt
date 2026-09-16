package app.wlo.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
 * The fifth top-level destination from IA §1. It is intentionally a standard
 * Material 3 list: these are stable destinations, not dashboard cards or a
 * second navigation hierarchy.
 */
@Composable
public fun MoreScreen(
    onOpenArchive: () -> Unit,
    onOpenDigestion: () -> Unit,
    onOpenExercise: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenAi: () -> Unit,
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
        MoreDestination("Archive", "Private vector-outline history", "more-archive", onOpenArchive)
        HorizontalDivider()
        MoreDestination("Digestion", "Gut and fiber tracking", "more-digestion", onOpenDigestion)
        HorizontalDivider()
        MoreDestination("Exercise", "Sessions and movement", "more-exercise", onOpenExercise)
        HorizontalDivider()
        MoreDestination("Data Vault", "Backup, restore, import, and export", "more-vault", onOpenVault)
        HorizontalDivider()
        MoreDestination("AI", "On-device models and consent", "more-ai", onOpenAi)
        HorizontalDivider()
        MoreDestination("Settings", "Units, reminders, privacy, and profile", "more-settings", onOpenSettings)
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
