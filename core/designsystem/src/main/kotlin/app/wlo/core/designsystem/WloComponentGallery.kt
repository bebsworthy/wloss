package app.wlo.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * Interactive preview gallery for WLO's Material 3 delegates (WLO-0091).
 * Android Studio's interactive preview supplies pressed and focused states;
 * this content keeps selected, disabled, busy, and error states visible at rest.
 */
@Composable
internal fun WloComponentGallery() {
    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        WloScreenTitle("Material components")
        WloButton(label = "Save this reading", onClick = {}, modifier = Modifier.fillMaxWidth())
        WloSecondaryButton(label = "Secondary action with a long label", onClick = {})
        WloButton(label = "Unavailable", onClick = {}, enabled = false)
        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
            SelectChip(label = "Selected", selected = true, onClick = {})
            SelectChip(label = "Filter", selected = false, onClick = {})
        }
        WloSwitchRow(
            label = "Weekly reminder",
            checked = true,
            onCheckedChange = {},
            secondary = "A long supporting line wraps without hiding the switch or its label.",
        )
        WloListRow(
            label = "Imported reading with a deliberately long source description",
            secondary = "82.4 lb · measured · today at 07:10",
            chevron = true,
            onClick = {},
        )
        OutlinedTextField(
            value = "not a number",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Weight") },
            supportingText = { Text("Enter a weight from 66.1 to 661.4 lb.") },
            isError = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
            CircularProgressIndicator()
            Text("Saving…", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Preview(name = "WLO components · dark", widthDp = 360, showBackground = true)
@Composable
internal fun WloComponentGalleryDarkPreview() {
    WloTheme(darkTheme = true) { WloComponentGallery() }
}

@Preview(name = "WLO components · light", widthDp = 360, showBackground = true)
@Composable
internal fun WloComponentGalleryLightPreview() {
    WloTheme(darkTheme = false) { WloComponentGallery() }
}

@Preview(name = "WLO components · 320dp 200%", widthDp = 320, fontScale = 2f, showBackground = true)
@Composable
internal fun WloComponentGalleryLargeTextPreview() {
    WloTheme(darkTheme = true) { WloComponentGallery() }
}
