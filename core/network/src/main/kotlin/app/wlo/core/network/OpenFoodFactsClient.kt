package app.wlo.core.network

import app.wlo.core.ports.EgressDeniedException
import app.wlo.core.ports.EgressPort
import app.wlo.core.ports.EgressPurpose
import app.wlo.core.ports.EgressRequest
import app.wlo.core.ports.MissReason
import app.wlo.core.ports.OffLookupResult
import app.wlo.core.ports.OffProduct
import app.wlo.core.ports.OffRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/**
 * The Open Food Facts lookup (F02 §3 rung 3, R-C4) — the first real
 * OffRepository implementation, living in :core:network (D1: the only module
 * with a wire; the client NEVER opens its own socket, it dispatches through
 * the [EgressPort] choke point with `purpose = OFF_LOOKUP`).
 *
 * Wire contract:
 *  - `GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json` with
 *    an explicit `fields=` projection (ask for exactly the columns the R-A4
 *    v1 nutrient scope needs — nothing more leaves the response parser).
 *  - REQUIRED User-Agent `WLO/<version> (contact)` per OFF's API usage policy
 *    (app id + project contact; the contact is a PLACEHOLDER pending the
 *    owner's real address — OWNER FLAG: swap before first release).
 *  - Caching: R-C4 "aggressive cache" via the dispatcher's cache-first store
 *    (`cacheKey = off/v2/product/<barcode>`); the second lookup of a barcode
 *    is a dispatcher CACHE_HIT receipt with zero bytes on the wire, and this
 *    client reports `servedFromCache = true` honestly (a barcode already
 *    fetched this process can only come back from the dispatcher's cache).
 *  - Receipts: one per lookup — OK, CACHE_HIT, FAILED and DENIED alike.
 *  - Rate limits: OFF reads are 15/min per the API policy; the cache-first
 *    dispatcher is the throttle (each distinct barcode costs at most one wire
 *    hit per process lifetime; re-scans never re-hit).
 */
public class OpenFoodFactsClient(
    private val egress: EgressPort,
    userAgentVersion: String = DEFAULT_USER_AGENT_VERSION,
    /** Injectable for tests (localhost MockWebServer); production uses the pinned host. */
    private val baseUrl: String = BASE_URL,
) : OffRepository {
    private val userAgent = "WLO/$userAgentVersion ($DEFAULT_CONTACT)"
    private val host: String = NetworkDispatcher.hostOf(baseUrl) ?: HOST

    /** Barcodes this process has fetched over the wire (cache-hit honesty). */
    private val fetchedThisProcess = HashSet<String>()

    override suspend fun lookup(barcode: String): OffLookupResult {
        val cleaned = barcode.filter { it.isDigit() }
        if (cleaned.length !in 6..18) {
            return OffLookupResult.Miss(MissReason.NOT_FOUND, barcode)
        }
        val cacheKey = "off/v2/product/$cleaned"
        val sawBefore =
            synchronized(fetchedThisProcess) { !fetchedThisProcess.add(cleaned) }

        val request =
            EgressRequest(
                purpose = EgressPurpose.OFF_LOOKUP,
                host = host,
                operation = "off/v2/product",
                expectedBytes = EXPECTED_RESPONSE_BYTES,
                cacheKey = cacheKey,
                userInitiated = false, // the R-C4 allowance, not an AI consent gate
                block = { fetchProductJson(cleaned) },
            )
        val response = egress.dispatch(request)

        return response.fold(
            onSuccess = { parsed ->
                val product = parsed.toProduct(cleaned)
                if (product == null) {
                    OffLookupResult.Miss(MissReason.NOT_FOUND, cleaned)
                } else {
                    OffLookupResult.Hit(product = product, servedFromCache = sawBefore)
                }
            },
            onFailure = { failure ->
                val reason =
                    when {
                        failure is EgressDeniedException -> MissReason.DISABLED
                        failure is IOException && failure.message?.contains("HTTP 4") == true -> MissReason.NOT_FOUND
                        else -> MissReason.NETWORK_UNAVAILABLE
                    }
                OffLookupResult.Miss(reason, cleaned)
            },
        )
    }

    /**
     * One wire GET (runs INSIDE the dispatcher, so it is the only code whose
     * result ever rides a socket). HTTP 404 → parsed "not found" (v2 answers
     * 404 JSON for unknown barcodes); 429/5xx and transport errors → the
     * R-U15 degrade path.
     */
    private suspend fun fetchProductJson(barcode: String): OffV2Response {
        val response =
            client.get("$baseUrl/api/v2/product/$barcode.json?fields=$OFF_FIELDS") {
                header("User-Agent", userAgent)
                header("Accept", "application/json")
            }
        val status = response.status
        if (!status.isSuccess()) {
            if (status.value == 404) return notFoundResponse()
            throw IOException("OFF lookup HTTP ${status.value}")
        }
        val body = response.bodyAsText()
        return runCatching {
            houseJson.decodeFromString(OffV2Response.serializer(), body)
        }.getOrElse {
            // A non-JSON body on a 2xx is a broken response, not a miss on us.
            throw IOException("OFF lookup returned unparsable body", it)
        }
    }

    /** The client behind [EgressPurpose.OFF_LOOKUP] blocks (own stack, D9-legal here). */
    private val client: HttpClient by lazy { defaultEgressClient() }

    /** Release the lazy HTTP client (AutoCloseable-style; the dispatcher's is separate). */
    public fun close() {
        client.close()
    }

    public companion object {
        public const val HOST: String = "world.openfoodfacts.org"
        public const val BASE_URL: String = "https://$HOST"

        /**
         * OWNER FLAG: placeholder contact pending the owner's address; OFF's
         * usage policy requires a real contact in the User-Agent before
         * release.
         */
        public const val DEFAULT_CONTACT: String = "contact@wlo.app"

        public const val DEFAULT_USER_AGENT_VERSION: String = "0.1"

        /**
         * ODbL attribution line (the OFF database is ODbL-licensed): MUST
         * render wherever an OFF product's numbers show (product-screen
         * footer, provenance sheets) and rides each product as provenance.
         */
        public const val ATTRIBUTION: String =
            "Data from Open Food Facts, licensed under ODbL " +
                "(opendatacommons.org/licenses/odbl) — thanks to its contributors."

        /** Declared size attestation for the receipt (responses are small JSON). */
        public const val EXPECTED_RESPONSE_BYTES: Long = 64L * 1024

        /** The v2 fields projection — the minimum R-A4 v1 needs. */
        public val OFF_FIELDS: String =
            listOf("code", "product_name", "generic_name", "brands", "serving_size", "nutriments")
                .joinToString(",")

        private fun notFoundResponse(): OffV2Response = OffV2Response(status = 0)
    }
}

// --- v2 wire types (ADR-004 house rules: unknown keys tolerated, defaults) ---

@Serializable
internal data class OffV2Response(
    internal val status: Int = 0,
    internal val code: String? = null,
    internal val product: OffV2Product? = null,
)

@Serializable
internal data class OffV2Product(
    internal val code: String? = null,
    @SerialName("product_name") internal val productName: String? = null,
    @SerialName("generic_name") internal val genericName: String? = null,
    internal val brands: String? = null,
    @SerialName("serving_size") internal val servingSize: String? = null,
    internal val nutriments: JsonObject? = null,
)

internal fun OffV2Response.toProduct(fallbackBarcode: String): OffProduct? {
    val product = product ?: return null
    val name = (product.productName ?: product.genericName)?.trim().orEmpty()
    if (name.isBlank() && product.nutriments == null) return null
    return OffProduct(
        barcode = product.code?.ifBlank { null } ?: fallbackBarcode,
        name = name.ifBlank { "Unnamed product" },
        brand = product.brands?.trim()?.ifBlank { null },
        kcalPer100g = product.nutriments.energyKcalPer100g(),
        proteinGPer100g = product.nutriments.per100g("proteins_100g"),
        carbGPer100g = product.nutriments.per100g("carbohydrates_100g"),
        fatGPer100g = product.nutriments.per100g("fat_100g"),
        fiberGPer100g = product.nutriments.per100g("fiber_100g"),
        servingSizeText = product.servingSize?.trim()?.ifBlank { null },
        attribution = OpenFoodFactsClient.ATTRIBUTION,
    )
}

/** `energy-kcal_100g` when present; falls back to kJ→kcal on `energy_100g`. */
private fun JsonObject?.energyKcalPer100g(): Double? {
    if (this == null) return null
    per100g("energy-kcal_100g")?.let { return it }
    return per100g("energy_100g")?.let { kilojoules -> kilojoules / KJ_PER_KCAL }
}

private fun JsonObject?.per100g(key: String): Double? {
    if (this == null) return null
    val element = this[key] ?: return null
    return (element as? JsonPrimitive)?.doubleOrNull ?: element.jsonPrimitive.doubleOrNull
}

private const val KJ_PER_KCAL: Double = 4.184
