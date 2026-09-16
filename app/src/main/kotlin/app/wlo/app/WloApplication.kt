package app.wlo.app

import android.app.Application
import android.util.Log
import app.wlo.app.di.appModule
import app.wlo.app.di.platformModule
import app.wlo.core.vault.RestoreCommitter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Composition root (D1): Koin starts here and binds the real platform
 * implementations (DataStore settings, the Room database builder) that no
 * other module may see.
 */
public class WloApplication : Application() {
    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val koin =
            startKoin {
                androidContext(this@WloApplication)
                modules(appModule, platformModule)
            }.koin
        // WLO-0061: a process death between restore stores leaves an atomic
        // journal. Replaying is idempotent, so cold start can always roll it
        // forward without asking the user to reselect the source file.
        recoveryScope.launch {
            recoverPendingRestore(koin.get())
        }
    }

    /**
     * Recovery spans Room, two DataStores, and projection code, so no narrower
     * exception family represents all retryable failures. Keep the journal for
     * the next launch, log the cause, and never consume coroutine cancellation.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun recoverPendingRestore(committer: RestoreCommitter) {
        try {
            committer.recoverPending()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Log.e(TAG, "Restore recovery remains pending for the next launch", failure)
        }
    }

    private companion object {
        const val TAG: String = "WloApplication"
    }
}
