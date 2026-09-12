package app.wlo.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.Path

/**
 * Key-addressed JSON document drafts on top of one Preferences DataStore file
 * (ADR-003 KV/settings layer). Serialization lives with the DOCUMENT type
 * (`:core:documents` or the owning feature); this store is intentionally a
 * dumb, typed string/flag byte — the versioned-document house rules (schema
 * envelope, defaults, migration funnel) apply at the document layer, and a
 * corrupt payload decodes to `null` at the reader, never a crash.
 *
 * One store file per concern (same rule as [SettingsStore]); production wiring
 * in `:app` passes the files-dir path, tests pass a temp dir.
 */
public class JsonDocumentStore private constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** Emits the stored text for [key], or null when absent. */
    public fun observeText(key: String): Flow<String?> = dataStore.data.map { prefs -> prefs[textKey(key)] }

    /** One-shot read of [key]; null when absent. */
    public suspend fun readText(key: String): String? = dataStore.data.first()[textKey(key)]

    /** Writes [key]; the write is atomic (DataStore edit) — a kill mid-write
     * lands on the previous complete value, never a torn one. */
    public suspend fun writeText(
        key: String,
        value: String,
    ) {
        dataStore.edit { it[textKey(key)] = value }
    }

    /** Removes [key] entirely (draft retirement). */
    public suspend fun remove(key: String) {
        dataStore.edit { it.remove(textKey(key)) }
    }

    /** Boolean flag flow (default false) — e.g. the onboarding-complete mark. */
    public fun observeFlag(key: String): Flow<Boolean> = dataStore.data.map { prefs -> prefs[flagKey(key)] ?: false }

    public suspend fun readFlag(key: String): Boolean = dataStore.data.first()[flagKey(key)] ?: false

    public suspend fun writeFlag(
        key: String,
        value: Boolean,
    ) {
        dataStore.edit { it[flagKey(key)] = value }
    }

    private fun textKey(key: String): Preferences.Key<String> = stringPreferencesKey("doc/$key")

    private fun flagKey(key: String): Preferences.Key<Boolean> = booleanPreferencesKey("flag/$key")

    public companion object {
        /** Factory: one store per file path (DataStore forbids two instances on one file). */
        public fun create(file: Path): JsonDocumentStore =
            JsonDocumentStore(
                androidx.datastore.preferences.core.PreferenceDataStoreFactory.createWithPath(
                    produceFile = { file },
                ),
            )
    }
}
