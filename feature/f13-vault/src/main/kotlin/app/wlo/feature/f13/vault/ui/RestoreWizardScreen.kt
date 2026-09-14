package app.wlo.feature.f13.vault.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import app.wlo.core.designsystem.WloBadge
import app.wlo.core.designsystem.WloBadgeTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardAccent
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloProgress
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSecondaryButton
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
        WloScreenTitle(title = "Restore", modifier = Modifier.testTag("f13-restore-title"))
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
                    WloButton(
                        label = "Pick a file",
                        onClick = { filePicker.launch(arrayOf("application/octet-stream", "*/*")) },
                        modifier = Modifier.testTag("f13-restore-pick"),
                    )
                }

            RestoreWizardUiState.Step.Passphrase ->
                Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                    Text(
                        text = "File: ${state.fileName} · ${formatBytes(state.byteCount)}",
                        style = wloType.receipt,
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
                        WloSecondaryButton(
                            label = "Back",
                            onClick = viewModel::backToPick,
                            modifier = Modifier.testTag("f13-restore-back"),
                        )
                        WloButton(
                            label = "Validate",
                            onClick = {
                                viewModel.submitPassphrase(passphrase.toCharArray())
                                passphrase = ""
                            },
                            enabled = passphrase.isNotEmpty(),
                            modifier = Modifier.testTag("f13-restore-unlock"),
                        )
                    }
                }

            RestoreWizardUiState.Step.Report -> {
                val staged = state.staged
                Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                    WloCard(
                        accent = WloCardAccent.Primary,
                        modifier = Modifier.fillMaxWidth().testTag("f13-restore-report"),
                    ) {
                        WloCardHeader(
                            title = "Nothing applied yet",
                            provenance = { WloBadge(text = "Validated", tone = WloBadgeTone.Accent) },
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
                            color = wloExtendedColors.textTertiary,
                            modifier = Modifier.testTag("f13-restore-schema"),
                        )
                        HorizontalDivider()
                        staged?.sections?.forEach { section ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = section.name, style = wloType.receipt)
                                Text(
                                    text = "${section.rows} rows",
                                    style = wloType.receipt,
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
                                text = warning,
                                style = wloType.receipt,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("f13-restore-warning"),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                        WloSecondaryButton(
                            label = "Back",
                            onClick = viewModel::backToPick,
                            modifier = Modifier.testTag("f13-restore-back"),
                        )
                        WloButton(
                            label = "Continue",
                            onClick = viewModel::confirmCommit,
                            modifier = Modifier.testTag("f13-restore-continue"),
                        )
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
                        WloSecondaryButton(
                            label = "Back",
                            onClick = viewModel::backToPick,
                            modifier = Modifier.testTag("f13-restore-back"),
                        )
                        WloButton(
                            label = "Apply restore",
                            onClick = viewModel::confirmCommit,
                            modifier = Modifier.testTag("f13-restore-confirm"),
                        )
                    }
                }

            RestoreWizardUiState.Step.Applying -> {
                WloProgress(
                    progress = null,
                    label = "Applying…",
                    modifier = Modifier.testTag("f13-restore-applying"),
                )
                Text(
                    text = "One transaction, all-or-nothing — nothing is half-applied.",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
            }

            RestoreWizardUiState.Step.Done ->
                WloCard(
                    accent = WloCardAccent.Primary,
                    modifier = Modifier.fillMaxWidth().testTag("f13-restore-done"),
                ) {
                    WloCardHeader(
                        title = "Restore complete",
                        provenance = { WloBadge(text = "Restored", tone = WloBadgeTone.Accent) },
                    )
                    Text(
                        text =
                            "${state.commitInserted} rows added · ${state.commitSkipped} kept " +
                                "as-is (already present).",
                        style = wloType.body,
                        modifier = Modifier.testTag("f13-restore-counts"),
                    )
                    state.commitWarnings.forEach {
                        Text(
                            text = it,
                            style = wloType.receipt,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    WloButton(label = "Done", onClick = onDone)
                }

            RestoreWizardUiState.Step.Failed ->
                WloCard(
                    accent = WloCardAccent.Warning,
                    modifier = Modifier.fillMaxWidth().testTag("f13-restore-failed"),
                ) {
                    WloCardHeader(
                        title = "Restore stopped",
                        provenance = { WloBadge(text = "Failed", tone = WloBadgeTone.Held) },
                    )
                    Text(
                        text = failureTitle(state.failure),
                        style = wloType.title,
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
                            color = wloExtendedColors.textTertiary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                        WloSecondaryButton(
                            label = "Try another file",
                            onClick = viewModel::backToPick,
                            modifier = Modifier.testTag("f13-restore-retry"),
                        )
                        WloSecondaryButton(label = "Done", onClick = onDone)
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
        RestoreWizardUiState.Step.Pick -> "Pick a file"
        RestoreWizardUiState.Step.Passphrase -> "Passphrase"
        RestoreWizardUiState.Step.Report -> "Validation report"
        RestoreWizardUiState.Step.Confirm -> "Confirm"
        RestoreWizardUiState.Step.Applying -> "Applying"
        RestoreWizardUiState.Step.Done -> "Done"
        RestoreWizardUiState.Step.Failed -> "Stopped safely"
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
