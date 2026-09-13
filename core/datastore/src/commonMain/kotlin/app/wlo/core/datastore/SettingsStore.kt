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

    // --- F13 Data Vault (M6, WLO-0028 PART A) --------------------------------

    /** The SAF tree uri chosen for auto-backups (null = backup not set up). */
    public val backupFolderUri: Flow<String?> =
        dataStore.data.map { prefs -> prefs[KEY_BACKUP_FOLDER_URI] }

    /** Auto-backup runs only after a folder was chosen (F13 §3 default: on then). */
    public val backupAutoEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_BACKUP_AUTO_ENABLED] ?: false }

    /** Biometric app lock (F13 §3): off until the user arms it. */
    public val appLockEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_APP_LOCK_ENABLED] ?: false }

    /** Lock timeout wire name: "immediate" | "1min" | "5min" (default 1min). */
    public val lockTimeout: Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_LOCK_TIMEOUT] ?: LOCK_TIMEOUT_ONE_MIN }

    /** T-K4 diagnostics (opt-in crash reports): OFF by default, never a consent category. */
    public val diagnosticsCrashReports: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_DIAGNOSTICS_CRASH_REPORTS] ?: false }

    /** T-K4 collector endpoint (empty = diagnostics unusable even when toggled on). */
    public val diagnosticsEndpoint: Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_DIAGNOSTICS_ENDPOINT] ?: "" }

    /** R-C4 food-database integration toggle: default ON, verbatim ruling. */
    public val foodDbLookupsEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_FOOD_DB_LOOKUPS_ENABLED] ?: true }

    // --- F12 consent shell + F13 import wizard (M6 PART B, WLO-0028) ---------

    /**
     * The AI Studio's global kill switch (F12 §3.4): ON overrides every consent
     * category off and blocks new grants while set. Ships OFF — per R-C7 the
     * honest default is that nothing transmits anyway; the switch is one-tap
     * insurance, never a nag.
     */
    public val aiCloudKillSwitch: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_AI_CLOUD_KILL_SWITCH] ?: false }

    /**
     * R-S4: the CSV column-mapping wizard remembers its last mapping (one
     * JSON-encoded list of `VaultCsvMappingView`-shaped objects; null = none
     * remembered yet). One global memory is the v1 scope — per-source keys
     * arrive with the competitor converters.
     */
    public val rememberedCsvMapping: Flow<String?> =
        dataStore.data.map { prefs -> prefs[KEY_REMEMBERED_CSV_MAPPING] }

    public suspend fun setAiCloudKillSwitch(on: Boolean) {
        dataStore.edit { it[KEY_AI_CLOUD_KILL_SWITCH] = on }
    }

    public suspend fun setRememberedCsvMapping(json: String?) {
        dataStore.edit {
            if (json == null) it.remove(KEY_REMEMBERED_CSV_MAPPING) else it[KEY_REMEMBERED_CSV_MAPPING] = json
        }
    }

    public suspend fun backupFolderUriOnce(): String? = backupFolderUri.first()

    public suspend fun setBackupFolderUri(uri: String?) {
        dataStore.edit {
            if (uri == null) it.remove(KEY_BACKUP_FOLDER_URI) else it[KEY_BACKUP_FOLDER_URI] = uri
        }
    }

    public suspend fun setBackupAutoEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BACKUP_AUTO_ENABLED] = enabled }
    }

    public suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_APP_LOCK_ENABLED] = enabled }
    }

    public suspend fun setLockTimeout(wireName: String) {
        dataStore.edit { it[KEY_LOCK_TIMEOUT] = wireName }
    }

    public suspend fun setDiagnosticsCrashReports(enabled: Boolean) {
        dataStore.edit { it[KEY_DIAGNOSTICS_CRASH_REPORTS] = enabled }
    }

    public suspend fun setDiagnosticsEndpoint(url: String) {
        dataStore.edit { it[KEY_DIAGNOSTICS_ENDPOINT] = url }
    }

    public suspend fun setFoodDbLookupsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_FOOD_DB_LOOKUPS_ENABLED] = enabled }
    }

    /**
     * The known settings as a plain map — the backup "settings" section (M6).
     * SENSITIVE keys are excluded here AND at the export layer (defense in
     * depth): anything matching [SENSITIVE_SETTING_PREFIXES] never rides a
     * backup or export; v1 ships no such keys (BYOK keys live in Keystore-
     * wrapped storage, not in DataStore — F12 §3.3), the prefix list is the
     * invariant that keeps it true.
     */
    public suspend fun exportKnownSettings(): Map<String, String> {
        val prefs = dataStore.data.first()
        return buildMap {
            prefs[KEY_UNIT_SYSTEM]?.let { put(SETTING_UNIT_SYSTEM, it) }
            prefs[KEY_CONSENT_INTRO_SEEN]?.let { put(SETTING_CONSENT_INTRO_SEEN, it.toString()) }
            prefs[KEY_PANTRY_DEDUCTION_ENABLED]?.let { put(SETTING_PANTRY_DEDUCTION, it.toString()) }
            prefs[KEY_PANTRY_DEDUCTION_PROMPT_SEEN]?.let { put(SETTING_PANTRY_DEDUCTION_PROMPT, it.toString()) }
            prefs[KEY_BACKUP_FOLDER_URI]?.let { put(SETTING_BACKUP_FOLDER_URI, it) }
            prefs[KEY_BACKUP_AUTO_ENABLED]?.let { put(SETTING_BACKUP_AUTO, it.toString()) }
            prefs[KEY_APP_LOCK_ENABLED]?.let { put(SETTING_APP_LOCK_ENABLED, it.toString()) }
            prefs[KEY_LOCK_TIMEOUT]?.let { put(SETTING_LOCK_TIMEOUT, it) }
            prefs[KEY_FOOD_DB_LOOKUPS_ENABLED]?.let { put(SETTING_FOOD_DB_LOOKUPS, it.toString()) }
        }.filterKeys { key -> SENSITIVE_SETTING_PREFIXES.none(key::startsWith) }
    }

    /** Restores the settings section (unknown keys ignored; nulls remove). */
    public suspend fun importSettings(settings: Map<String, String>) {
        dataStore.edit { prefs ->
            settings[SETTING_UNIT_SYSTEM]?.let { v -> prefs[KEY_UNIT_SYSTEM] = v }
            settings[SETTING_CONSENT_INTRO_SEEN]?.toBooleanStrictOrNull()?.let { v -> prefs[KEY_CONSENT_INTRO_SEEN] = v }
            settings[SETTING_PANTRY_DEDUCTION]?.toBooleanStrictOrNull()?.let { v -> prefs[KEY_PANTRY_DEDUCTION_ENABLED] = v }
            settings[SETTING_PANTRY_DEDUCTION_PROMPT]?.toBooleanStrictOrNull()?.let { v -> prefs[KEY_PANTRY_DEDUCTION_PROMPT_SEEN] = v }
            settings[SETTING_BACKUP_FOLDER_URI]?.let { v -> prefs[KEY_BACKUP_FOLDER_URI] = v }
            settings[SETTING_BACKUP_AUTO]?.toBooleanStrictOrNull()?.let { v -> prefs[KEY_BACKUP_AUTO_ENABLED] = v }
            settings[SETTING_APP_LOCK_ENABLED]?.toBooleanStrictOrNull()?.let { v -> prefs[KEY_APP_LOCK_ENABLED] = v }
            settings[SETTING_LOCK_TIMEOUT]?.let { v -> prefs[KEY_LOCK_TIMEOUT] = v }
            settings[SETTING_FOOD_DB_LOOKUPS]?.toBooleanStrictOrNull()?.let { v -> prefs[KEY_FOOD_DB_LOOKUPS_ENABLED] = v }
        }
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
        val KEY_BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val KEY_BACKUP_AUTO_ENABLED = booleanPreferencesKey("backup_auto_enabled")
        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val KEY_LOCK_TIMEOUT = stringPreferencesKey("lock_timeout")
        val KEY_DIAGNOSTICS_CRASH_REPORTS = booleanPreferencesKey("diagnostics_crash_reports")
        val KEY_DIAGNOSTICS_ENDPOINT = stringPreferencesKey("diagnostics_endpoint")
        val KEY_FOOD_DB_LOOKUPS_ENABLED = booleanPreferencesKey("food_db_lookups_enabled")
        val KEY_AI_CLOUD_KILL_SWITCH = booleanPreferencesKey("ai_cloud_kill_switch")
        val KEY_REMEMBERED_CSV_MAPPING = stringPreferencesKey("remembered_csv_mapping")

        // Section keys (backup/export "settings" map, F13 §3).
        const val SETTING_UNIT_SYSTEM = "unit_system"
        const val SETTING_CONSENT_INTRO_SEEN = "consent_intro_seen"
        const val SETTING_PANTRY_DEDUCTION = "pantry_deduction_enabled"
        const val SETTING_PANTRY_DEDUCTION_PROMPT = "pantry_deduction_prompt_seen"
        const val SETTING_BACKUP_FOLDER_URI = "backup_folder_uri"
        const val SETTING_BACKUP_AUTO = "backup_auto_enabled"
        const val SETTING_APP_LOCK_ENABLED = "app_lock_enabled"
        const val SETTING_LOCK_TIMEOUT = "lock_timeout"
        const val SETTING_FOOD_DB_LOOKUPS = "food_db_lookups_enabled"

        /** Default lock timeout (F13 §3 configurable; v1 default = 1 minute). */
        const val LOCK_TIMEOUT_ONE_MIN = "1min"

        /**
         * F13 §3 export hygiene: settings whose key starts with any of these
         * NEVER enter exports or backups (Waistline's BACKUP_KEYS rule). BYOK
         * provider keys must not live in DataStore at all (F12 §3.3 —
         * Keystore-wrapped storage); the prefix list makes that auditable.
         */
        val SENSITIVE_SETTING_PREFIXES = listOf("byok", "apiKey", "providerSecret", "token")
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
