package app.wlo.feature.f13.vault.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import app.wlo.core.ports.VaultFailure
import app.wlo.feature.f13.vault.state.RestoreWizardUiState
import app.wlo.feature.f13.vault.state.RestoreWizardViewModel

/**
 * The staged-restore wizard (F13 §4 flow 1/3). Steps render one at a time:
 * pick → passphrase → STAGED REPORT (per-section counts, schema migrated-from
 * → to, warnings — nothing applied) → explicit confirm → applying → done.
 * The failure states are the feature: a hostile file gets a clean verdict and
 * the standing copy that NOTHING was changed — data loss by import is
 * structurally impossible (F13 §1, openScale's staged-and-validated restore).
 */
@Composable
public fun RestoreWizardScreen(
    viewModel: RestoreWizardViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var passphrase by rememberSaveable { mutableStateOf("") }

    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val bytes =
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
                    }.getOrNull()
                if (bytes != null) {
                    val name =
                        uri.lastPathSegment
                            ?.substringAfterLast('/')
                            ?.substringBefore('?') ?: "backup.wlo"
                    viewModel.onFilePicked(name, bytes)
                }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .testTag("f13-restore"),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(
            text = "Restore",
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
            modifier = Modifier.padding(top = WloSpacing.SCREEN).testTag("f13-restore-title"),
        )
        Text(
            text = "Step ${stepNumber(state.step)} of 4 — ${stepName(state.step)}",
            style = wloType.label,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider()

        when (state.step) {
            RestoreWizardUiState.Step.Pick ->
                Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                    Text(
                        text =
                            "Pick a WLO backup file (.wlo). Restore appends and reconciles — it never " +
                                "deletes: rows you already have stay, missing rows come back.",
                        style = wloType.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { filePicker.launch(arrayOf("application/octet-stream", "*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("f13-restore-pick"),
                    ) { Text("Choose backup file", style = wloType.label) }
                }

            RestoreWizardUiState.Step.Passphrase ->
                Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                    Text(
                        text = "File: ${state.fileName} · ${formatBytes(state.byteCount)}",
                        style = wloType.receipt,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text =
                            "The backup is passphrase-encrypted. Enter the passphrase you set where the " +
                                "backup was made — it is the only key, anywhere.",
                        style = wloType.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        singleLine = true,
                        label = { Text("Backup passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = state.passphraseError,
                        supportingText = {
                            if (state.passphraseError) {
                                Text(
                                    "Wrong passphrase — or the file is corrupted. Try again; nothing has been touched.",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.testTag("f13-restore-passphrase-error"),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("f13-restore-passphrase"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                        OutlinedButton(
                            onClick = viewModel::backToPick,
                            modifier = Modifier.testTag("f13-restore-back"),
                        ) {
                            Text("Back", style = wloType.label)
                        }
                        Button(
                            onClick = {
                                viewModel.submitPassphrase(passphrase.toCharArray())
                                passphrase = ""
                            },
                            enabled = passphrase.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("f13-restore-unlock"),
                        ) { Text("Validate", style = wloType.label) }
                    }
                }

            RestoreWizardUiState.Step.Report -> {
                val staged = state.staged
                Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                    Surface(
                        shape = WloShape.Card,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().testTag("f13-restore-report"),
                    ) {
                        Column(
                            Modifier.padding(WloSpacing.PAD_CARD),
                            verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                        ) {
                            Text(
                                "Validated ✓ — nothing applied yet",
                                style = wloType.title,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text =
                                    "Schema v${staged?.schemaVersionFrom} → v${staged?.schemaVersionTo}" +
                                        (
                                            if ((staged?.schemaVersionFrom ?: 0) < (staged?.schemaVersionTo ?: 0)) {
                                                " (migrated forward on apply)"
                                            } else {
                                                ""
                                            }
                                        ),
                                style = wloType.receipt,
                                fontFamily = FontFamily.Monospace,
                                color = wloExtendedColors.textTertiary,
                                modifier = Modifier.testTag("f13-restore-schema"),
                            )
                            HorizontalDivider()
                            staged?.sections?.forEach { section ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = section.name,
                                        style = wloType.receipt,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    Text(
                                        text = "${section.rows} rows",
                                        style = wloType.receipt,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.testTag("f13-restore-section-${section.name}"),
                                    )
                                }
                            }
                            HorizontalDivider()
                            Text(
                                text =
                                    "${staged?.totalRows ?: 0} rows total · every row re-validated " +
                                        "against its schema",
                                style = wloType.label,
                            )
                            staged?.warnings?.takeIf { it.isNotEmpty() }?.forEach { warning ->
                                Text(
                                    text = "⚠ $warning",
                                    style = wloType.receipt,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.testTag("f13-restore-warning"),
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                        OutlinedButton(
                            onClick = viewModel::backToPick,
                            modifier = Modifier.testTag("f13-restore-back"),
                        ) {
                            Text("Back", style = wloType.label)
                        }
                        Button(
                            onClick = viewModel::confirmCommit,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("f13-restore-continue"),
                        ) { Text("Continue", style = wloType.label) }
                    }
                }
            }

            RestoreWizardUiState.Step.Confirm ->
                Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                    Text(
                        text =
                            "Apply this restore? Your current data stays — restore adds and reconciles, " +
                                "it never deletes. The apply is one all-or-nothing transaction.",
                        style = wloType.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("f13-restore-confirm-copy"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                        OutlinedButton(
                            onClick = viewModel::backToPick,
                            modifier = Modifier.testTag("f13-restore-back"),
                        ) { Text("Back", style = wloType.label) }
                        Button(
                            onClick = viewModel::confirmCommit,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("f13-restore-confirm"),
                        ) { Text("Apply restore", style = wloType.label) }
                    }
                }

            RestoreWizardUiState.Step.Applying ->
                Text(
                    "Applying — one transaction, all-or-nothing…",
                    style = wloType.body,
                    modifier = Modifier.testTag("f13-restore-applying"),
                )

            RestoreWizardUiState.Step.Done ->
                Surface(
                    shape = WloShape.Card,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().testTag("f13-restore-done"),
                ) {
                    Column(
                        Modifier.padding(WloSpacing.PAD_CARD),
                        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                    ) {
                        Text("Restored ✓", style = wloType.title, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text =
                                "${state.commitInserted} rows added · ${state.commitSkipped} kept " +
                                    "as-is (already present).",
                            style = wloType.body,
                            modifier = Modifier.testTag("f13-restore-counts"),
                        )
                        state.commitWarnings.forEach {
                            Text(
                                text = "⚠ $it",
                                style = wloType.receipt,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Button(
                            onClick = onDone,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) { Text("Done", style = wloType.label) }
                    }
                }

            RestoreWizardUiState.Step.Failed ->
                Surface(
                    shape = WloShape.Card,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().testTag("f13-restore-failed"),
                ) {
                    Column(
                        Modifier.padding(WloSpacing.PAD_CARD),
                        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                    ) {
                        Text(
                            text = failureTitle(state.failure),
                            style = wloType.title,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("f13-restore-failure-title"),
                        )
                        Text(
                            text =
                                "Nothing was changed. Your data on this device is exactly as it was — " +
                                    "the file never reached it.",
                            style = wloType.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("f13-restore-untouched"),
                        )
                        state.failureDetail?.let {
                            Text(
                                text = it,
                                style = wloType.receipt,
                                fontFamily = FontFamily.Monospace,
                                color = wloExtendedColors.textTertiary,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                            OutlinedButton(
                                onClick = viewModel::backToPick,
                                modifier = Modifier.testTag("f13-restore-retry"),
                            ) {
                                Text("Try another file", style = wloType.label)
                            }
                            OutlinedButton(onClick = onDone) { Text("Done", style = wloType.label) }
                        }
                    }
                }
        }
        Text(
            text = "Restore validation runs entirely on this device.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
            modifier = Modifier.padding(bottom = WloSpacing.SCREEN),
        )
    }
}

private fun stepNumber(step: RestoreWizardUiState.Step): Int =
    when (step) {
        RestoreWizardUiState.Step.Pick -> 1
        RestoreWizardUiState.Step.Passphrase -> 2
        RestoreWizardUiState.Step.Report -> 3
        RestoreWizardUiState.Step.Confirm, RestoreWizardUiState.Step.Applying -> 4
        RestoreWizardUiState.Step.Done, RestoreWizardUiState.Step.Failed -> 4
    }

private fun stepName(step: RestoreWizardUiState.Step): String =
    when (step) {
        RestoreWizardUiState.Step.Pick -> "pick a file"
        RestoreWizardUiState.Step.Passphrase -> "passphrase"
        RestoreWizardUiState.Step.Report -> "the validation report"
        RestoreWizardUiState.Step.Confirm -> "confirm"
        RestoreWizardUiState.Step.Applying -> "applying"
        RestoreWizardUiState.Step.Done -> "done"
        RestoreWizardUiState.Step.Failed -> "stopped safely"
    }

private fun failureTitle(failure: VaultFailure?): String =
    when (failure) {
        VaultFailure.WRONG_FORMAT, VaultFailure.BAD_HEADER -> "That file is not a WLO backup"
        VaultFailure.TRUNCATED -> "The file is incomplete"
        VaultFailure.UNSUPPORTED_FORMAT -> "This backup format is too old or too new"
        VaultFailure.FUTURE_VERSION -> "This file was made by a newer version of WLO"
        VaultFailure.SCHEMA_INVALID -> "The file's contents fail validation"
        VaultFailure.REFERENCE_BROKEN -> "The file references data that doesn't exist"
        else -> "This file could not be restored"
    }
