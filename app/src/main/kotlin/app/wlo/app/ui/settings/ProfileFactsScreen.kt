package app.wlo.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.ProfileRepository
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.Sex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** Profile-facts intents (MVI-lite). */
public sealed interface ProfileFactsEvent {
    public data class SexChange(
        public val sex: Sex?,
    ) : ProfileFactsEvent

    public data class BirthYearChange(
        public val text: String,
    ) : ProfileFactsEvent

    public data class HeightChange(
        public val text: String,
    ) : ProfileFactsEvent

    public data class ActivityChange(
        public val level: ActivityLevel,
    ) : ProfileFactsEvent

    public data object Save : ProfileFactsEvent
}

/** The editable facts (WLO-0035 W4): what onboarding collected, now correctable. */
public data class ProfileFactsUi(
    public val loading: Boolean = true,
    public val sex: Sex? = null,
    public val birthYearText: String = "",
    public val heightText: String = "",
    public val activityLevel: ActivityLevel = ActivityLevel.SEDENTARY,
    public val notice: String? = null,
    public val saved: Boolean = false,
)

/**
 * The profile facts' state holder: sex, birth year, height, activity level —
 * the measured facts the engines read (RFM/Navy/BMR/floors). A mistyped
 * onboarding answer must be correctable forever (WLO-0035 W4); the goal is
 * NOT here (Targets store, R-B2 — the Goals editor owns it).
 */
public class ProfileFactsViewModel(
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileFactsUi())

    /** Renderable state. */
    public val uiState: StateFlow<ProfileFactsUi> = mutableState

    init {
        viewModelScope.launch {
            profiles.active().getOrNull()?.let { profile ->
                mutableState.value =
                    mutableState.value.copy(
                        loading = false,
                        sex = profile.sex,
                        birthYearText = profile.birthYear.toString(),
                        heightText =
                            profile.heightCm.let { cm ->
                                val tenths = (cm * 10).toLong()
                                "${tenths / 10}.${tenths % 10}"
                            },
                        activityLevel = profile.activityLevel,
                    )
            } ?: run { mutableState.value = mutableState.value.copy(loading = false) }
        }
    }

    /** MVI-lite intent entry point. */
    public fun onEvent(event: ProfileFactsEvent) {
        when (event) {
            is ProfileFactsEvent.SexChange -> mutableState.value = mutableState.value.copy(sex = event.sex, saved = false)
            is ProfileFactsEvent.BirthYearChange -> mutableState.value = mutableState.value.copy(birthYearText = event.text, saved = false)
            is ProfileFactsEvent.HeightChange -> mutableState.value = mutableState.value.copy(heightText = event.text, saved = false)
            is ProfileFactsEvent.ActivityChange -> mutableState.value = mutableState.value.copy(activityLevel = event.level, saved = false)
            ProfileFactsEvent.Save -> save()
        }
    }

    private fun save() {
        val current = mutableState.value
        val birthYear = current.birthYearText.toIntOrNull()
        if (birthYear == null || birthYear !in 1900..2100) {
            mutableState.value = mutableState.value.copy(notice = "check the birth year — a four-digit year")
            return
        }
        val height = current.heightText.toDoubleOrNull()
        if (height == null || height < 50.0 || height > 250.0) {
            mutableState.value = mutableState.value.copy(notice = "check the height — cm, 50–250")
            return
        }
        viewModelScope.launch {
            val profile = profiles.active().getOrNull() ?: return@launch
            when (profiles.updateFacts(profile.id, current.sex, birthYear, height, current.activityLevel)) {
                is WloResult.Ok -> mutableState.value = mutableState.value.copy(saved = true, notice = null)
                is WloResult.Err -> mutableState.value = mutableState.value.copy(notice = "that didn't save — nothing changed")
            }
        }
    }
}

/**
 * The profile-facts editor (Settings → Profile): the onboarding answers,
 * correctable in place — the engines' math reads these on every estimate.
 */
@Composable
public fun ProfileFactsScreen(
    viewModel: ProfileFactsViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        WloScreenTitle(title = "Profile", modifier = Modifier.testTag("profile-title"))

        state.notice?.let {
            WloBanner(text = it, tone = WloBannerTone.Warning, modifier = Modifier.testTag("profile-notice"))
        }
        if (state.saved) {
            Text(
                text = "saved — the estimates recompute from these facts immediately",
                style = wloType.caption,
                color = wloExtendedColors.held,
                modifier = Modifier.testTag("profile-saved"),
            )
        }

        WloCard(modifier = Modifier.testTag("profile-card")) {
            WloCardHeader(title = "Facts")
            Text(
                text = "Recorded sex (floors and tape formulas use it; undisclosed is allowed).",
                style = wloType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                for (sex in listOf<Sex?>(Sex.FEMALE, Sex.MALE, Sex.OTHER, null)) {
                    SelectChip(
                        label = sexLabel(sex),
                        selected = state.sex == sex,
                        onClick = { viewModel.onEvent(ProfileFactsEvent.SexChange(sex)) },
                        modifier = Modifier.testTag("profile-sex-${sex?.wireName ?: "undisclosed"}"),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                OutlinedTextField(
                    value = state.birthYearText,
                    onValueChange = { viewModel.onEvent(ProfileFactsEvent.BirthYearChange(it)) },
                    modifier = Modifier.weight(1f).testTag("profile-birth-year"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = wloType.body,
                    placeholder = { Text("birth year", style = wloType.caption, color = wloExtendedColors.textTertiary) },
                )
                OutlinedTextField(
                    value = state.heightText,
                    onValueChange = { viewModel.onEvent(ProfileFactsEvent.HeightChange(it)) },
                    modifier = Modifier.weight(1f).testTag("profile-height"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = wloType.body,
                    placeholder = { Text("height cm", style = wloType.caption, color = wloExtendedColors.textTertiary) },
                )
            }
            Text(text = "Activity level (the formula multiplier).", style = wloType.caption)
            Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                for (level in listOf(
                    ActivityLevel.SEDENTARY to "Sedentary",
                    ActivityLevel.LIGHT to "Light",
                    ActivityLevel.MODERATE to "Moderate",
                    ActivityLevel.ACTIVE to "Active",
                    ActivityLevel.VERY_ACTIVE to "Very active",
                )) {
                    SelectChip(
                        label = level.second,
                        selected = state.activityLevel == level.first,
                        onClick = { viewModel.onEvent(ProfileFactsEvent.ActivityChange(level.first)) },
                        modifier = Modifier.testTag("profile-activity-${level.first.wireName}"),
                    )
                }
            }
            WloButton(
                label = "Save facts",
                onClick = { viewModel.onEvent(ProfileFactsEvent.Save) },
                modifier = Modifier.fillMaxWidth().testTag("profile-save"),
            )
        }
    }
}

private fun sexLabel(sex: Sex?): String =
    when (sex) {
        Sex.FEMALE -> "Female"
        Sex.MALE -> "Male"
        Sex.OTHER -> "Other"
        null -> "Undisclosed"
    }
