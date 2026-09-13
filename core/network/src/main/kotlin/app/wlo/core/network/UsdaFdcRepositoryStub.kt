package app.wlo.core.network

import app.wlo.core.ports.MissReason
import app.wlo.core.ports.OffLookupResult
import app.wlo.core.ports.OffRepository

/**
 * DOCUMENTED STUB (F02 §3 rung 3: "Barcode → OFF lookup (USDA FDC second)").
 * The USDA FDC backend lands later; the port is already backend-agnostic, so
 * this implementation only ever reports [MissReason.NETWORK_UNAVAILABLE]-free
 * honest misses — it opens NO socket and writes NO receipt, because it never
 * dispatches. Bound NOWHERE in the composition root until the real client
 * replaces it; kept in-tree so the second-backend slot is explicit rather
 * than implied.
 */
public class UsdaFdcRepositoryStub : OffRepository {
    override suspend fun lookup(barcode: String): OffLookupResult = OffLookupResult.Miss(MissReason.NOT_FOUND, barcode)

    public companion object {
        public const val STUB_NOTE: String = "USDA FDC lookup lands later — see F02 §3 rung 3."
    }
}
