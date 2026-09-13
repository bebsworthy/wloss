package app.wlo.app.di

import app.wlo.core.consent.ConsentChain
import app.wlo.core.consent.ConsentDecision
import app.wlo.core.consent.ConsentEntry
import app.wlo.core.consent.ConsentGate
import app.wlo.core.consent.ConsentLedger
import app.wlo.core.data.RoomConsentLedger
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.model.ConsentCapability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The AI Studio's global kill switch, as ENFORCEMENT (not just UI): while the
 * settings switch is on, every capability reads as denied at the dispatcher's
 * gate — even a stale grant recorded before the switch flipped can never send
 * a byte. The switch never disables on-device inference (F12 §3.4: it gates
 * CLOUD only), and it blocks NEW grants at the UI layer (rows disabled).
 */
public class KillSwitchConsentGate(
    private val delegate: ConsentGate,
    private val settings: SettingsStore,
) : ConsentGate {
    override suspend fun isGranted(capability: ConsentCapability): Boolean =
        !settings.aiCloudKillSwitch.first() && delegate.isGranted(capability)

    override suspend fun currentGrants(): Set<ConsentCapability> =
        if (settings.aiCloudKillSwitch.first()) emptySet() else delegate.currentGrants()

    /** Live grant set for the AI Studio, kill switch honored. */
    public fun observeGrants(): Flow<Set<ConsentCapability>> =
        settings.aiCloudKillSwitch.map { kill ->
            if (kill) {
                emptySet()
            } else {
                delegate.currentGrants()
            }
        }
}

/**
 * The consent ledger's append-side companion to [KillSwitchConsentGate]: while
 * the kill switch is on, GRANTS are refused (the ledger records nothing —
 * recording a grant the gate would immediately override would be dishonest
 * paperwork). Revocations always pass: turning categories OFF is always legal.
 */
public class KillSwitchConsentLedger(
    private val delegate: RoomConsentLedger,
    private val settings: SettingsStore,
) : ConsentLedger {
    override suspend fun record(
        capability: ConsentCapability,
        decision: ConsentDecision,
    ): ConsentEntry {
        if (decision == ConsentDecision.GRANT && settings.aiCloudKillSwitch.first()) {
            // Fail closed WITHOUT polluting the hash chain: the switch blocks
            // the grant, the UI renders rows disabled — nothing is recorded.
            throw KillSwitchBlocksGrantException(capability)
        }
        return delegate.record(capability, decision)
    }

    override suspend fun entries(): List<ConsentEntry> = delegate.entries()

    override fun observe(): Flow<List<ConsentEntry>> = delegate.observe()

    /** The chain verifier stays available for the F12 history surfaces. */
    public suspend fun verify(): Boolean = ConsentChain.verify(delegate.entries())
}

/** Thrown when the kill switch refuses a GRANT (F12 §3.4 "blocks new grants"). */
public class KillSwitchBlocksGrantException(
    capability: ConsentCapability,
) : IllegalStateException("the kill switch is on — ${capability.wireName} cannot be granted while Cloud: OFF")
