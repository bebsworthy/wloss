package app.wlo.app.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import app.wlo.core.common.getOrNull
import app.wlo.core.data.ProfileRepository
import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.feature.f01.onboarding.domain.FinishOnboarding
import kotlinx.coroutines.runBlocking

/**
 * Debug-only demo seeder (WLO-0036): `just demo` broadcasts [ACTION_SEED] at
 * the explicit component, and this receiver drops the full demo dataset
 * ([DemoSeed]: onboard + diary week + weigh-in series + dealt week plan) into
 * a FRESH install through the real repositories. Never clobbers: with
 * onboarding complete or an active profile present it logs + toasts and exits
 * without writing. Runs in the app process after Application.onCreate, so the
 * Koin graph is up — the same access the instrumented robot uses. All writes
 * anchor to the app's frozen demo clock (FixedClock.DEMO_NOW).
 *
 * Announces completion on the `wlo-demo` logcat tag (the justfile recipe waits
 * on that line), then toasts the outcome.
 */
public class DemoSeedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        // The seeding does real Room work — never block the main thread; the
        // logcat line + finish() tell `am broadcast` / the justfile we're done.
        Thread {
            try {
                seedOrSkip(context)
            } catch (t: Throwable) {
                Log.e(TAG, "failed: $t")
                toast(context, "WLO demo seed failed: $t")
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun seedOrSkip(context: Context): Unit =
        runBlocking {
            val koin =
                org.koin.core.context.GlobalContext
                    .get()
            val documents = koin.get<JsonDocumentStore>()
            val profiles = koin.get<ProfileRepository>()

            val hasData =
                documents.readFlag(FinishOnboarding.FLAG_COMPLETE) ||
                    profiles.active().getOrNull() != null
            if (hasData) {
                Log.i(TAG, "skipped (data present)")
                toast(context, "WLO demo: data already present — skipped")
                return@runBlocking
            }

            val seeded = DemoSeed.onboardAndSeedWeek()
            DemoSeed.dealWeekPlan(
                profileId = seeded.profileId,
                startDayEpochDay = seeded.today,
                seed = PLAN_SEED,
            )
            Log.i(TAG, "seeded")
            toast(context, "WLO demo data seeded")
        }

    /** Toasts must be shown on a Looper thread; the seeder runs on a worker. */
    private fun toast(
        context: Context,
        message: String,
    ) {
        Handler(Looper.getMainLooper())
            .post {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
    }

    public companion object {
        /** The logcat tag the `just demo` recipe polls for. */
        public const val TAG: String = "wlo-demo"

        /** The intent action (declared in the debug manifest). */
        public const val ACTION_SEED: String = "app.wlo.demo.SEED"

        /**
         * The dealt week plan's seed — the same value F10HubMealsTest uses, so
         * the Hub's meals card matches the instrumented expectations.
         */
        public const val PLAN_SEED: Long = 42L
    }
}
