package app.wlo.core.network

import app.wlo.core.ports.EgressPort
import app.wlo.core.ports.EgressPurpose
import app.wlo.core.ports.EgressRequest

/**
 * T-K4 spike (q-000026) — the CONTENT-FREEING pipeline between ACRA and the
 * single socket door. ACRA 5.13.1 (`ch.acra:acra-http`, Maven Central, verified
 * 2026-09-12) is the candidate reporter: Apache-2.0, source-available, and its
 * `HttpSender` can be pointed at any HTTP endpoint — but ACRA's collector model
 * assumes a backend, and WLO has none by constitution (F13 §9). This skeleton
 * is the shape of the v1 answer: ACRA collects, WE scrub + dispatch.
 *
 * ## Pipeline (what a v1 implementation does)
 *
 * 1. ACRA (acra-core only — no acra-dialog/notification extras) builds its
 *    report; our custom `ReportSender` (registered via `@AutoService`) receives
 *    the `ReportBuilder` product and immediately reduces it to a whitelist:
 *      - APP_VERSION_CODE / APP_VERSION_NAME
 *      - ANDROID_VERSION, PHONE_MODEL (generic device, not user-identifying)
 *      - STACK_TRACE — rebuilt: frames whose class starts with `app.wlo.` are
 *        kept verbatim; ALL other frames are reduced to
 *        `at <class>: <REDACTED>` — enough to see where our code was entered
 *        from, nothing about the user's environment or data;
 *      - CRASH_CONFIGURATION is dropped; USER_* fields dropped; LOGCAT
 *        dropped; any custom data dropped (WLO attaches none by policy).
 *    The scrubbed JSON is the ENTIRE payload — there is no user content in it,
 *    no logs, no identifiers (no device id, no install id, no account).
 * 2. The scrubbed bytes dispatch through [EgressPort.dispatch] with
 *    [EgressPurpose.DIAGNOSTICS] — the toggle gate, the receipt and the audit
 *    card come for free, and a packet analyzer sees the same whitelist.
 * 3. Failure handling: send-once, never queue-and-retry (a crash report is
 *    not worth a background egress loop); the receipt proves the attempt
 *    either way.
 *
 * ## Endpoint recommendation (for ADR-008)
 *
 *   v1: **user-configured webhook** (default empty = feature unusable until
 *   set), with a **documented GitHub-Issues recipe** as the zero-infra
 *   default (a personal-access-token webhook into
 *   `github.com/<user>/wlo-crashes` via the Issues API). Rationale: no WLO
 *   backend exists or is wanted (F13 §9); self-hosted fugue/ACRA-collector
 *   stays the power-user option (a single Docker image, BYO-server, same
 *   wire format). The toggle ships OFF; the endpoint field ships empty;
 *   both live in Settings → Diagnostics (PART B surface).
 *
 * ## Status
 *
 * SKELETON per T-K4 ("decision recorded; implementation optional"): the
 * scrubbing function is complete and tested; ACRA is not yet a dependency
 * (its init + ReportSender glue is the v1 follow-up). The dispatcher will
 * DENY these dispatches today unless the settings toggle is on — correct by
 * construction.
 */
public class AcraReportSender(
    private val egress: EgressPort,
    /** The user-configured report collector (empty = dispatch denies itself). */
    private val endpointUrl: String,
) {
    /**
     * Reduces a raw ACRA-style report to the content-free whitelist. Pure and
     * unit-tested — the CI pins that framework frames never survive and app
     * frames do.
     */
    public fun scrub(rawReport: String): String {
        val lines = rawReport.lines()
        val out = StringBuilder()
        for (line in lines) {
            when {
                line.startsWith("APP_VERSION") ||
                    line.startsWith("ANDROID_VERSION") ||
                    line.startsWith("PHONE_MODEL") ||
                    line.startsWith("STACK_TRACE") -> out.appendLine(line)

                // Keep only app frames verbatim; everything else redacts to a marker.
                line.trimStart().startsWith("at ") ->
                    if (isAppFrame(line)) out.appendLine(line) else out.appendLine(REDACTED_FRAME)

                else -> {} // every other field class is dropped wholesale
            }
        }
        return out.toString().trimEnd()
    }

    /**
     * Dispatches a SCRUBBED report through the choke point. Denials are the
     * success path when the toggle is off — the receipt proves the negative.
     */
    public suspend fun send(scrubbedReport: String): Result<Unit> {
        if (endpointUrl.isBlank()) {
            return Result.failure(IllegalStateException("no diagnostics endpoint configured"))
        }
        val host =
            NetworkDispatcher.hostOf(endpointUrl)
                ?: return Result.failure(IllegalArgumentException("bad endpoint URL"))
        return egress
            .dispatch(
                EgressRequest(
                    purpose = EgressPurpose.DIAGNOSTICS,
                    host = host,
                    operation = "acra/crash-report",
                    expectedBytes = scrubbedReport.toByteArray(Charsets.UTF_8).size.toLong(),
                    userInitiated = false, // a crash is not a user action; the toggle is
                    block = { /* POST via the choke point's client — v1 glue */ },
                ),
            ).map { }
    }

    private fun isAppFrame(frameLine: String): Boolean = frameLine.contains(APP_FRAME_PREFIX)

    public companion object {
        /** Frames of OUR code — the only ones a content-free report keeps. */
        public const val APP_FRAME_PREFIX: String = "app.wlo."

        private const val REDACTED_FRAME: String = "    at (framework frame redacted)"
    }
}
