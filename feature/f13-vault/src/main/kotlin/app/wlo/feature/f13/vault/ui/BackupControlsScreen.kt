package app.wlo.feature.f13.vault.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
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
        Text(
            text = "Backup",
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
            modifier = Modifier.padding(top = WloSpacing.SCREEN).testTag("f13-backup-title"),
        )
        Text(
            text =
                "Backups are encrypted with your passphrase and written to a folder YOU own — point it at " +
                    "a synced drive and user-owned sync comes free. Rotation keeps the last 7.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Folder
        Surface(
            shape = WloShape.Card,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(WloSpacing.PAD_CARD),
                verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                Text(
                    text =
                        if (state.folderUri != null) {
                            "Folder: chosen ✓"
                        } else {
                            "Folder: not chosen yet"
                        },
                    style = wloType.statS,
                )
                Text(
                    text =
                        state.folderUri
                            ?: "The SAF folder is the destination of every backup — pick the Documents " +
                            "folder, a sync dir, anything you control.",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                )
                Button(
                    onClick = { folderPicker.launch(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("f13-pick-folder"),
                ) { Text(if (state.folderUri != null) "Change folder" else "Choose folder", style = wloType.label) }
            }
        }

        // Passphrase (set/change)
        Surface(
            shape = WloShape.Card,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().testTag("f13-passphrase-card"),
        ) {
            Column(
                Modifier.padding(WloSpacing.PAD_CARD),
                verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                Text(
                    text =
                        if (state.passphraseSet) {
                            "Passphrase: set — backups are encrypted"
                        } else {
                            "Passphrase: not set"
                        },
                    style = wloType.statS,
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
                Button(
                    onClick = {
                        viewModel.setPassphrase(passphrase.toCharArray())
                        passphrase = ""
                    },
                    enabled = passphrase.length >= 8,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("f13-set-passphrase"),
                ) { Text("Save passphrase", style = wloType.label) }
            }
        }

        // Backup now + auto
        Surface(
            shape = WloShape.Card,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(WloSpacing.PAD_CARD), verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
                ) {
                    Button(
                        onClick = { viewModel.backupNow(null) },
                        enabled = state.folderUri != null && !state.running && state.passphraseSet,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("f13-backup-now"),
                    ) {
                        Text(if (state.running) "Backing up…" else "Back up now", style = wloType.label)
                    }
                    if (state.running) {
                        Text(
                            "assembling · encrypting · writing",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(text = "Automatic daily backups", style = wloType.statS)
                        Text(
                            text =
                                "Runs once a day while the folder is reachable. A failed run raises " +
                                    "one quiet notification.",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                        )
                    }
                    Switch(
                        checked = state.autoEnabled,
                        onCheckedChange = viewModel::setAuto,
                        enabled = state.folderUri != null && state.passphraseSet,
                        colors =
                            SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedThumbColor = wloExtendedColors.surfaceSunken,
                            ),
                        modifier = Modifier.testTag("f13-auto-toggle"),
                    )
                }
                state.lastOutcome?.let { outcome ->
                    Text(
                        text =
                            "✓ ${outcome.fileName} · ${formatBytes(outcome.sizeBytes)}" +
                                (if (outcome.retired.isNotEmpty()) " · retired ${outcome.retired.size}" else ""),
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Text(
                    text = "In the folder (rotation keeps 7):",
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.files
                        .sortedByDescending { it.name }
                        .take(7)
                        .forEach { file ->
                            Text(
                                text = "${file.name} · ${formatBytes(file.sizeBytes)}",
                                style = wloType.receipt,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.heightIn(min = 20.dp),
                            )
                        }
                    if (state.files.isEmpty()) {
                        Text(
                            text = "nothing yet",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                            modifier = Modifier.testTag("f13-files-empty"),
                        )
                    }
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
