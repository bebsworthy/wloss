package app.wlo.feature.f13.vault.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.common.formatBytes
import app.wlo.core.designsystem.WloBadge
import app.wlo.core.designsystem.WloBadgeTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloProgress
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloSwitchRow
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.feature.f13.vault.state.BackupControlsViewModel

/**
 * Backup controls (F13 §3, R-U5): choose the SAF folder once (persistable
 * grant), set the passphrase (encrypted by default; the auto-backup key is
 * device-bound so scheduled runs never re-prompt), backup-now with visible
 * progress, the auto toggle, and the rotation ledger (keep 7).
 */
@Composable
public fun BackupControlsScreen(viewModel: BackupControlsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var passphrase by rememberSaveable { mutableStateOf("") }

    // The REAL user flow: ACTION_OPEN_DOCUMENT_TREE + takePersistableUriPermission.
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                context.contentResolver
                    .takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                viewModel.onFolderPicked(uri.toString())
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .testTag("f13-backup"),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        WloScreenTitle(title = "Backup", modifier = Modifier.testTag("f13-backup-title"))
        Text(
            text =
                "Backups are encrypted with your passphrase and written to a folder you own — point it " +
                    "at a synced drive and user-owned sync comes free. Rotation keeps the last 7.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Folder
        WloCard(modifier = Modifier.fillMaxWidth()) {
            WloCardHeader(
                title = "Folder",
                provenance = {
                    WloBadge(
                        text = if (state.folderUri != null) "Chosen" else "Not set",
                        tone = if (state.folderUri != null) WloBadgeTone.Accent else WloBadgeTone.Held,
                    )
                },
            )
            Text(
                text =
                    state.folderUri
                        ?: "The folder you pick is where every backup lands — Documents, a synced " +
                        "drive, anything you control.",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
                maxLines = 2,
            )
            WloButton(
                label = if (state.folderUri != null) "Change folder" else "Choose folder",
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.testTag("f13-pick-folder"),
            )
        }

        // Passphrase (set/change)
        WloCard(modifier = Modifier.fillMaxWidth().testTag("f13-passphrase-card")) {
            WloCardHeader(
                title = "Passphrase",
                provenance = {
                    WloBadge(
                        text = if (state.passphraseSet) "Set" else "Not set",
                        tone = if (state.passphraseSet) WloBadgeTone.Accent else WloBadgeTone.Held,
                    )
                },
                modifier = Modifier.testTag("f13-passphrase-state"),
            )
            Text(
                text =
                    "This passphrase decrypts your backup on ANY device. It is never stored — lose " +
                        "it and no one, including WLO, can read the backup. " +
                        "(Automatic backups re-use it via a device-locked key, so they run without " +
                        "asking.)",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                singleLine = true,
                label = {
                    Text(
                        if (state.passphraseSet) {
                            "New passphrase (replaces the old)"
                        } else {
                            "Backup passphrase"
                        },
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("f13-passphrase-field"),
            )
            WloButton(
                label = "Save passphrase",
                onClick = {
                    viewModel.setPassphrase(passphrase.toCharArray())
                    passphrase = ""
                },
                enabled = passphrase.length >= 8,
                modifier = Modifier.testTag("f13-set-passphrase"),
            )
        }

        // Backup now + auto + rotation ledger
        WloCard(modifier = Modifier.fillMaxWidth()) {
            WloCardHeader(title = "Backups")
            WloButton(
                label = if (state.running) "Backing up…" else "Back up now",
                onClick = { viewModel.backupNow(null) },
                enabled = state.folderUri != null && !state.running && state.passphraseSet,
                modifier = Modifier.testTag("f13-backup-now"),
            )
            if (state.running) {
                WloProgress(progress = null, label = "Backing up…")
            }
            // Real preconditions (WLO-0032): folder AND passphrase must exist
            // — the row renders disabled instead of silently refusing taps.
            WloSwitchRow(
                label = "Automatic daily backups",
                checked = state.autoEnabled,
                onCheckedChange = viewModel::setAuto,
                enabled = state.folderUri != null && state.passphraseSet,
                secondary =
                    "Runs once a day while the folder is reachable. " +
                        "A failed run raises one quiet notification.",
                modifier = Modifier.testTag("f13-auto-toggle"),
            )
            state.lastOutcome?.let { outcome ->
                Text(
                    text =
                        "${outcome.fileName} · ${formatBytes(outcome.sizeBytes)}" +
                            (if (outcome.retired.isNotEmpty()) " · ${outcome.retired.size} older rotated out" else ""),
                    style = wloType.receipt,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("f13-backup-outcome"),
                )
            }
            state.failure?.let {
                Text(
                    text = it,
                    style = wloType.body,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("f13-backup-failure"),
                )
            }
            HorizontalDivider()
            Text(
                text = "In the folder (latest 7):",
                style = wloType.label,
                color = wloExtendedColors.textTertiary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                state.files
                    .sortedByDescending { it.name }
                    .take(7)
                    .forEach { file ->
                        WloListRow(
                            label = file.name,
                            secondary = formatBytes(file.sizeBytes),
                        )
                    }
                if (state.files.isEmpty()) {
                    Text(
                        text = "Nothing yet.",
                        style = wloType.receipt,
                        color = wloExtendedColors.textTertiary,
                        modifier = Modifier.testTag("f13-files-empty"),
                    )
                }
            }
        }
        Text(
            text =
                "Passphrase backups are the recovery path of record. Test a restore before you " +
                    "need one — it takes two minutes.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
            modifier = Modifier.padding(bottom = WloSpacing.SCREEN),
        )
    }
}
