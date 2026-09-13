package app.wlo.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.wlo.core.common.MassUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.Path

/**
 * Example Preferences DataStore store (ADR-003: KV/settings layer). One store
 * per concern; keys are private, the API is typed. Suspend get/set + Flow
 * reads — never blocking getters.
 *
 * Store keys mirror [SettingsDocument]-style house defaults (metric units,
 * R-D10; dark-first, R-D1).
 */
public class SettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    /** Active mass unit for every render (settings-driven, never copy-hardcoded). */
    public val massUnit: Flow<MassUnit> =
        dataStore.data.map { prefs ->
            when (prefs[KEY_UNIT_SYSTEM]) {
                MassUnit.POUND.name -> MassUnit.POUND
                else -> MassUnit.KILOGRAM
            }
        }

    public val consentIntroSeen: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_CONSENT_INTRO_SEEN] ?: false
        }

    /**
     * Pantry partial-stock deduction (R-S5): OFF by default globally; the
     * first generation prompts once and this remembers the choice. When ON,
     * staples are deducted from generated lists with partial-stock math.
     */
    public val pantryDeductionEnabled: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_PANTRY_DEDUCTION_ENABLED] ?: false
        }

    /** Whether the one-time R-S5 prompt has been answered/shown. */
    public val pantryDeductionPromptSeen: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_PANTRY_DEDUCTION_PROMPT_SEEN] ?: false
        }

    public suspend fun massUnitOnce(): MassUnit = massUnit.first()

    public suspend fun setMassUnit(unit: MassUnit) {
        dataStore.edit { it[KEY_UNIT_SYSTEM] = unit.name }
    }

    public suspend fun setConsentIntroSeen(seen: Boolean) {
        dataStore.edit { it[KEY_CONSENT_INTRO_SEEN] = seen }
    }

    public suspend fun setPantryDeductionEnabled(enabled: Boolean) {
        dataStore.edit {
            it[KEY_PANTRY_DEDUCTION_ENABLED] = enabled
            it[KEY_PANTRY_DEDUCTION_PROMPT_SEEN] = true
        }
    }

    public suspend fun setPantryDeductionPromptSeen(seen: Boolean) {
        dataStore.edit { it[KEY_PANTRY_DEDUCTION_PROMPT_SEEN] = seen }
    }

    private companion object {
        val KEY_UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val KEY_CONSENT_INTRO_SEEN = booleanPreferencesKey("consent_intro_seen")
        val KEY_PANTRY_DEDUCTION_ENABLED = booleanPreferencesKey("pantry_deduction_enabled")
        val KEY_PANTRY_DEDUCTION_PROMPT_SEEN = booleanPreferencesKey("pantry_deduction_prompt_seen")
    }
}

/**
 * Factory: DataStore needs a file path per platform (expect/actual paths per
 * ADR-003); okio [Path] keeps that plumbing KMP-clean. Production wiring in
 * `:app` passes the files-dir path; tests pass a temp dir.
 */
public object SettingsStoreFactory {
    public fun create(file: Path): SettingsStore =
        SettingsStore(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.createWithPath(
                produceFile = { file },
            ),
        )
}
