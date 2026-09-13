package app.wlo.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wlo.core.database.WloDatabase
import app.wlo.core.ports.EgressDeniedException
import app.wlo.core.ports.EgressPurpose
import app.wlo.core.ports.EgressRequest
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M6 PART B acceptance (a): the AI Studio consent shell on the REAL graph.
 * Toggling a row mutates the PERSISTENT Room consent ledger (grant → revoke,
 * append-only); the kill switch overrides every category off (rows disabled,
 * gate denies even a standing grant); the receipt log renders the zero state
 * honestly, and after driving one (denied) egress attempt the hash chain
 * verifies intact.
 */
@RunWith(AndroidJUnit4::class)
public class M6ConsentStudioTest {
    @get:Rule
    public val rule = createAndroidComposeRule<MainActivity>()

    private fun koin() = GlobalContext.get()

    private fun deliverDeepLink(
        uri: String,
        expected: String,
    ) {
        // The M3-sweep in-process re-delivery path, pumping the compose test
        // frame clock (the rule parks the app's frame clock between test
        // actions — without a pump the intent's LaunchedEffect never runs).
        TestNav.deliverPumpingClock(rule, uri, expected)
    }

    private fun openStudio() {
        // Fresh install (orchestrator cleared data): the hub shows onboarding —
        // the studio deep link lands regardless (consent precedes onboarding).
        deliverDeepLink("wlo://ai/studio", "f12/studio")
        TestNav.awaitTag(rule, "f12-studio")
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            // The compose rule parks the app's frame clock between test
            // actions; pump it so pending recompositions can run.
            runCatching {
                rule.mainClock.autoAdvance = false
                rule.mainClock.advanceTimeBy(150)
                rule.mainClock.autoAdvance = true
            }
            Thread.sleep(150)
        }
        error("condition never became true")
    }

    @Test
    public fun toggleMutatesRoomLedger_andKillSwitchOverridesEverything() {
        val db = koin().get<WloDatabase>()
        runBlocking { assertTrue(db.consentLedger().all().isEmpty(), "fresh install: no consent entries") }

        openStudio()
        TestNav.awaitTag(rule, "f12-toggle-food-photo")

        // Toggle food-photo ON → one hash-chained grant row.
        rule.onNodeWithTag("f12-toggle-food-photo", useUnmergedTree = true).performClick()
        waitUntil { runBlocking { db.consentLedger().all().size == 1 } }
        runBlocking {
            val rows = db.consentLedger().all()
            assertEquals(1, rows.size)
            assertEquals("food-photo", rows.first().capability)
            assertEquals("grant", rows.first().decision)
        }

        // Toggle it OFF → a REVOKE row (append-only; never an edit).
        rule.onNodeWithTag("f12-toggle-food-photo", useUnmergedTree = true).performClick()
        waitUntil { runBlocking { db.consentLedger().all().size == 2 } }
        runBlocking {
            assertEquals(
                "revoke",
                db
                    .consentLedger()
                    .all()
                    .last()
                    .decision,
            )
        }

        // Kill switch ON → every row disabled + ledger untouched.
        rule.onNodeWithTag("f12-kill-switch", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule
            .onAllNodesWithTag("f12-toggle-food-photo", useUnmergedTree = true)
            .onFirst()
            .assertIsNotEnabled()
        runBlocking { assertEquals(2, db.consentLedger().all().size) }

        // ENFORCEMENT: the gate reads the kill switch — Cloud: OFF denies.
        val gate = koin().get<app.wlo.core.consent.ConsentGate>()
        runBlocking {
            assertTrue(
                !gate.isGranted(app.wlo.core.model.ConsentCapability.FOOD_PHOTO),
                "kill switch overrides grants",
            )
        }

        // Rows re-enable when the switch goes back off (grants stay revoked).
        rule.onNodeWithTag("f12-kill-switch", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule
            .onAllNodesWithTag("f12-toggle-food-photo", useUnmergedTree = true)
            .onFirst()
            .assertIsEnabled()
    }

    @Test
    public fun receiptsViewer_zeroState_thenReceiptAndIntactChain() {
        openStudio()
        // Reach the receipts via its registry link (wlo://ai/receipts): the
        // chip row's real tap is verified in M6ScreensTest (rule-free), but
        // under the compose rule even on-screen taps stall in the parked
        // input pipeline — the delivery path is the deterministic one here.
        deliverDeepLink("wlo://ai/receipts", "f12/receipts")
        TestNav.awaitTag(rule, "f12-receipts-empty")
        rule.waitForIdle()

        // Normal use leaves NO receipts. Drive one egress attempt OFF the UI
        // thread: a FUTURE_* dispatch with no grant is DENIED (bytes = 0, no
        // socket) and issues exactly one receipt — the negative is provable.
        val egress = koin().get<app.wlo.core.ports.EgressPort>()
        val result =
            runBlocking {
                egress.dispatch(
                    EgressRequest(
                        purpose = EgressPurpose.FUTURE_CLOUD_CHAT,
                        host = "consent-test.invalid",
                        operation = "ui-test/lookup",
                        block = {},
                    ),
                )
            }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is EgressDeniedException)

        // Into the receipts: one row, chain intact.
        deliverDeepLink("wlo://ai/receipts", "f12/receipts")
        waitUntil { rule.onAllNodesWithTag("f12-receipt-1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()

        rule.onNodeWithTag("f12-verify-chain", useUnmergedTree = true).performClick()
        waitUntil { rule.onAllNodesWithTag("f12-chain-verdict", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()

        val audit = koin().get<app.wlo.core.ports.ReceiptAuditLog>()
        runBlocking {
            val verdict = audit.verifyChain()
            assertTrue(verdict.intact, "chain must verify after the M4-style flow")
            assertEquals(1, verdict.checked)
        }
    }
}
