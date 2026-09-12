package app.wlo.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Compile-safety for the composition root: every definition's dependencies
 * must be resolvable inside the graph. External inputs (the Android context
 * from `androidContext()`; the DataStore instance SettingsStore wraps) are
 * declared explicitly.
 */
public class KoinModuleVerifyTest {
    @Test
    public fun appModuleIsInternallyConsistent() {
        appModule.verify()
    }

    @Test
    public fun platformModuleDeclaresItsExternalInputs() {
        platformModule.verify(listOf(Context::class, DataStore::class))
    }
}
