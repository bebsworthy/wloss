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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DecimalInput
import app.wlo.core.common.LengthUnit
import app.wlo.core.common.MassUnit
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.ProfileRepository
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.Sex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import org.koin.core.annotation.Provided

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
    public val heightUnit: LengthUnit = LengthUnit.CENTIMETER,
    public val activityLevel: ActivityLevel = ActivityLevel.SEDENTARY,
    public val notice: String? = null,
    public val saved: Boolean = false,
    public val saving: Boolean = false,
    public val birthYearError: String? = null,
    public val heightError: String? = null,
)

/**
 * The profile facts' state holder: sex, birth year, height, activity level —
 * the measured facts the engines read (RFM/Navy/BMR/floors). A mistyped
 * onboarding answer must be correctable forever (WLO-0035 W4); the goal is
 * NOT here (Targets store, R-B2 — the Goals editor owns it).
 */
public class ProfileFactsViewModel(
    private val profiles: ProfileRepository,
    private val settings: SettingsStore,
    private val clock: ClockPort,
    @Provided private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileFactsUi())

    /** Renderable state. */
    public val uiState: StateFlow<ProfileFactsUi> = mutableState

    init {
        viewModelScope.launch {
            val unit =
                if (settings.massUnit.first() == MassUnit.POUND) LengthUnit.INCH else LengthUnit.CENTIMETER
            profiles.active().getOrNull()?.let { profile ->
                val restoredProfile = savedStateHandle.get<String>(PROFILE_KEY) == profile.id
                mutableState.value =
                    mutableState.value.copy(
                        loading = false,
                        sex = if (restoredProfile) savedStateHandle.get<Sex?>(SEX_KEY) else profile.sex,
                        birthYearText =
                            if (restoredProfile) {
                                savedStateHandle.get<String>(BIRTH_KEY).orEmpty()
                            } else {
                                profile.birthYear?.toString().orEmpty()
                            },
                        heightText =
                            if (restoredProfile) {
                                savedStateHandle.get<String>(HEIGHT_KEY).orEmpty()
                            } else {
                                profile.heightCm?.let { format1(unit.fromCentimeters(it)) }.orEmpty()
                            },
                        heightUnit = unit,
                        activityLevel = profile.activityLevel,
                    )
                persist(profile.id)
            } ?: run { mutableState.value = mutableState.value.copy(loading = false) }
        }
    }

    /** MVI-lite intent entry point. */
    public fun onEvent(event: ProfileFactsEvent) {
        if (mutableState.value.saving) return
        when (event) {
            is ProfileFactsEvent.SexChange ->
                mutableState.value = mutableState.value.copy(sex = event.sex, saved = false)
            is ProfileFactsEvent.BirthYearChange ->
                mutableState.value =
                    mutableState.value.copy(birthYearText = event.text, saved = false, birthYearError = null)
            is ProfileFactsEvent.HeightChange ->
                mutableState.value = mutableState.value.copy(heightText = event.text, saved = false, heightError = null)
            is ProfileFactsEvent.ActivityChange ->
                mutableState.value = mutableState.value.copy(activityLevel = event.level, saved = false)
            ProfileFactsEvent.Save -> save()
        }
        if (event != ProfileFactsEvent.Save) {
            viewModelScope.launch { profiles.active().getOrNull()?.let { persist(it.id) } }
        }
    }

    private fun save() {
        val current = mutableState.value
        val thisYear = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        if (!isBirthYearSupported(current.birthYearText, thisYear)) {
            mutableState.value =
                mutableState.value.copy(birthYearError = "Enter a year from 1900 to $thisYear.")
            return
        }
        val birthYear = current.birthYearText.toInt()
        val height = DecimalInput.parse(current.heightText)?.let(current.heightUnit::toCentimeters)
        if (height == null || height < 50.0 || height > 250.0) {
            mutableState.value =
                mutableState.value.copy(heightError = "Enter a height from 50 to 250 cm (equivalent).")
            return
        }
        viewModelScope.launch {
            val profile = profiles.active().getOrNull() ?: return@launch
            mutableState.value = mutableState.value.copy(saving = true, notice = null)
            when (profiles.updateFacts(profile.id, current.sex, birthYear, height, current.activityLevel)) {
                is WloResult.Ok -> {
                    val readback = profiles.byId(profile.id).getOrNull()
                    if (readback?.birthYear == birthYear && readback.heightCm == height) {
                        savedStateHandle.remove<String>(PROFILE_KEY)
                        mutableState.value =
                            mutableState.value.copy(saved = true, saving = false, notice = null)
                    } else {
                        mutableState.value =
                            mutableState.value.copy(
                                saving = false,
                                notice = "That save could not be verified. Try again.",
                            )
                    }
                }
                is WloResult.Err ->
                    mutableState.value =
                        mutableState.value.copy(
                            saving = false,
                            notice = "That didn't save — your edits are still here.",
                        )
            }
        }
    }

    private fun persist(profileId: String) {
        savedStateHandle[PROFILE_KEY] = profileId
        savedStateHandle[SEX_KEY] = mutableState.value.sex
        savedStateHandle[BIRTH_KEY] = mutableState.value.birthYearText
        savedStateHandle[HEIGHT_KEY] = mutableState.value.heightText
    }

    private fun format1(value: Double): String {
        val tenths = (value * 10).toLong()
        return "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
    }

    internal companion object {
        fun isBirthYearSupported(
            text: String,
            currentYear: Int,
        ): Boolean = text.toIntOrNull() in 1900..currentYear

        const val PROFILE_KEY: String = "profileFacts.profile"
        const val SEX_KEY: String = "profileFacts.sex"
        const val BIRTH_KEY: String = "profileFacts.birth"
        const val HEIGHT_KEY: String = "profileFacts.height"
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
            Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                OutlinedTextField(
                    value = state.birthYearText,
                    onValueChange = { viewModel.onEvent(ProfileFactsEvent.BirthYearChange(it)) },
                    modifier = Modifier.fillMaxWidth().testTag("profile-birth-year"),
                    singleLine = true,
                    enabled = !state.saving,
                    label = { Text("Birth year") },
                    isError = state.birthYearError != null,
                    supportingText = state.birthYearError?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = wloType.body,
                    placeholder = {
                        Text("For example, 1990", style = wloType.caption, color = wloExtendedColors.textTertiary)
                    },
                )
                OutlinedTextField(
                    value = state.heightText,
                    onValueChange = { viewModel.onEvent(ProfileFactsEvent.HeightChange(it)) },
                    modifier = Modifier.fillMaxWidth().testTag("profile-height"),
                    singleLine = true,
                    enabled = !state.saving,
                    label = { Text("Height (${state.heightUnit.symbol})") },
                    isError = state.heightError != null,
                    supportingText = state.heightError?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = wloType.body,
                    placeholder = {
                        Text("For example, 175", style = wloType.caption, color = wloExtendedColors.textTertiary)
                    },
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
                label = if (state.saving) "Saving…" else "Save facts",
                onClick = { viewModel.onEvent(ProfileFactsEvent.Save) },
                enabled = !state.saving,
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
