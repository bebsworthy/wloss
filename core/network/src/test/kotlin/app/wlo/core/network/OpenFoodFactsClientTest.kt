package app.wlo.core.network

import app.wlo.core.consent.ConsentGate
import app.wlo.core.consent.ConsentTimeSource
import app.wlo.core.ports.EgressPurpose
import app.wlo.core.ports.MissReason
import app.wlo.core.ports.OffLookupPolicy
import app.wlo.core.ports.OffLookupResult
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The OFF v2 client over the REAL dispatcher + a localhost wire: field
 * projection, nutriment mapping (incl. kJ fallback), the ODbL attribution
 * riding the product, cache honesty (second lookup = served-from-cache with a
 * CACHE_HIT receipt and zero server requests), and the R-U15 degrade reasons.
 */
public class OpenFoodFactsClientTest {
    private lateinit var server: MockWebServer
    private lateinit var dispatcher: NetworkDispatcher
    private lateinit var client: OpenFoodFactsClient
    private val ledger = RecordingLedger()

    private val gate =
        object : ConsentGate {
            override suspend fun isGranted(capability: app.wlo.core.model.ConsentCapability): Boolean = false

            override suspend fun currentGrants(): Set<app.wlo.core.model.ConsentCapability> = emptySet()
        }

    @Before
    public fun setUp() {
        server = MockWebServer()
        server.start()
        dispatcher =
            NetworkDispatcher(
                consentGate = gate,
                offLookupPolicy = OffLookupPolicy { true },
                ledger = ledger,
                timeSource = ConsentTimeSource { 1_760_000_000_000L },
            )
        client = OpenFoodFactsClient(egress = dispatcher, baseUrl = server.url("/").toString())
    }

    @After
    public fun tearDown() {
        dispatcher.close()
        server.close()
    }

    private fun enqueueProduct(body: String) {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(body)
                .addHeader("Content-Type", "application/json")
                .build(),
        )
    }

    private val productBody: String =
        """
        {
          "status": 1,
          "code": "3017620422003",
          "product": {
            "code": "3017620422003",
            "product_name": "Nutella",
            "brands": "Ferrero",
            "serving_size": "15 g",
            "nutriments": {
              "energy-kcal_100g": 539,
              "fat_100g": 30.9,
              "saturated-fat_100g": 10.6,
              "carbohydrates_100g": 57.5,
              "sugars_100g": 56.3,
              "proteins_100g": 6.3,
              "fiber_100g": 3.4
            },
            "ingredient_text": "sugar, palm oil, hazelnuts..."
          }
        }
        """.trimIndent()

    @Test
    public fun hitMapsNutrimentsBrandServingAndAttribution() =
        runTest {
            enqueueProduct(productBody)
            val result = client.lookup("3017620422003")
            assertTrue(result is OffLookupResult.Hit, "expected a hit, got $result")
            val product = (result as OffLookupResult.Hit).product
            assertEquals("Nutella", product.name)
            assertEquals("Ferrero", product.brand)
            assertEquals(539.0, product.kcalPer100g)
            assertEquals(30.9, product.fatGPer100g)
            assertEquals(57.5, product.carbGPer100g)
            assertEquals(6.3, product.proteinGPer100g)
            assertEquals(3.4, product.fiberGPer100g)
            assertEquals("15 g", product.servingSizeText)
            assertEquals("3017620422003", product.barcode)
            // The ODbL attribution MUST ride the result (UI footer + provenance).
            assertTrue(product.attribution.contains("ODbL"))
        }

    @Test
    public fun wireCarriesUserAgentAndFieldProjection() =
        runTest {
            enqueueProduct(productBody)
            client.lookup("3017620422003")
            val recorded = server.takeRequest()
            assertEquals(
                "WLO/${OpenFoodFactsClient.DEFAULT_USER_AGENT_VERSION} (${OpenFoodFactsClient.DEFAULT_CONTACT})",
                recorded.headers["User-Agent"],
            )
            val path = recorded.url?.encodedPath ?: ""
            assertTrue(path.contains("/api/v2/product/3017620422003.json"), path)
        }

    @Test
    public fun secondLookupIsHonestCacheHit_withZeroExtraRequests() =
        runTest {
            enqueueProduct(productBody)
            val first = client.lookup("3017620422003") as OffLookupResult.Hit
            assertEquals(false, first.servedFromCache)
            val second = client.lookup("3017620422003") as OffLookupResult.Hit
            assertEquals(true, second.servedFromCache, "the same barcode can only come back from the dispatcher cache")
            assertEquals(1, server.requestCount, "cache-first: one wire hit per distinct barcode")

            // And the ledger agrees: OK then CACHE_HIT, cache hit carries 0 bytes.
            assertEquals(listOf(EgressOutcome.OK, EgressOutcome.CACHE_HIT), ledger.outcomes)
            assertEquals(0L, ledger.bytes.last())
        }

    @Test
    public fun missingProductIsNotFoundMiss() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(404)
                    .body("{\"status\": 0, \"status_verbose\": \"product not found\"}")
                    .build(),
            )
            val result = client.lookup("0000000000000")
            assertEquals(OffLookupResult.Miss(MissReason.NOT_FOUND, "0000000000000"), result)
        }

    @Test
    public fun statusZeroBodyIsAlsoNotFound() =
        runTest {
            enqueueProduct("{\"status\": 0, \"status_verbose\": \"product not found\"}")
            val result = client.lookup("1111111111111")
            assertEquals(MissReason.NOT_FOUND, (result as OffLookupResult.Miss).reason)
        }

    @Test
    public fun transportFailureDegradesAsNetworkUnavailable() =
        runTest {
            // No enqueued response + immediate close = the offline path.
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(500)
                    .body("boom")
                    .build(),
            )
            val result = client.lookup("2222222222222")
            assertEquals(MissReason.NETWORK_UNAVAILABLE, (result as OffLookupResult.Miss).reason)
            assertEquals(EgressOutcome.FAILED, ledger.outcomes.last(), "failures are receipted too")
        }

    @Test
    public fun disabledToggleSurfacesAsDisabledMiss_withDeniedReceipt() =
        runTest {
            dispatcher.close()
            val strictLedger = RecordingLedger()
            dispatcher =
                NetworkDispatcher(
                    consentGate = gate,
                    offLookupPolicy = OffLookupPolicy { false },
                    ledger = strictLedger,
                    timeSource = ConsentTimeSource { 1_760_000_000_000L },
                )
            client = OpenFoodFactsClient(egress = dispatcher, baseUrl = server.url("/").toString())
            val result = client.lookup("3333333333333")
            assertEquals(MissReason.DISABLED, (result as OffLookupResult.Miss).reason)
            assertEquals(listOf(EgressOutcome.DENIED), strictLedger.outcomes)
            assertEquals(0, server.requestCount, "denied lookups never reach the wire")
        }

    @Test
    public fun malformedBarcodesMissWithoutAnyWire() =
        runTest {
            assertEquals(MissReason.NOT_FOUND, (client.lookup("abc") as OffLookupResult.Miss).reason)
            assertEquals(MissReason.NOT_FOUND, (client.lookup("42") as OffLookupResult.Miss).reason)
            assertEquals(0, server.requestCount)
        }

    @Test
    public fun kJOnlyNutrimentsConvertToKcal() =
        runTest {
            enqueueProduct(
                """
                {"status": 1, "code": "4444444444444", "product": {
                    "product_name": "KJ-only product",
                    "nutriments": {"energy_100g": 1000, "proteins_100g": 4.0}
                }}
                """.trimIndent(),
            )
            val product = (client.lookup("4444444444444") as OffLookupResult.Hit).product
            assertEquals(1000.0 / 4.184, product.kcalPer100g!!, 1e-9)
            assertEquals(4.0, product.proteinGPer100g)
        }

    @Test
    public fun sparseProductWithNoNameOrNutrimentsIsAMiss() =
        runTest {
            enqueueProduct("{\"status\": 1, \"code\": \"5555555555555\", \"product\": {\"code\": \"5555555555555\"}}")
            assertEquals(MissReason.NOT_FOUND, (client.lookup("5555555555555") as OffLookupResult.Miss).reason)
        }

    /** Records outcomes + bytes without touching a database. */
    private class RecordingLedger : EgressLedger {
        public val outcomes: MutableList<EgressOutcome> = mutableListOf()
        public val bytes: MutableList<Long> = mutableListOf()
        private val sealed = mutableListOf<EgressReceipt>()

        override suspend fun append(receipt: PendingReceipt): EgressReceipt {
            outcomes += receipt.outcome
            bytes += receipt.bytes
            val entry =
                EgressReceipt(
                    seq = (sealed.size + 1).toLong(),
                    purpose = receipt.purpose,
                    host = receipt.host,
                    operation = receipt.operation,
                    bytes = receipt.bytes,
                    outcome = receipt.outcome,
                    atEpochMs = receipt.atEpochMs,
                    prevHashHex = sealed.lastOrNull()?.hashHex ?: "genesis",
                    hashHex = "sealed",
                )
            sealed += entry
            return entry
        }

        override suspend fun recent(limit: Long): List<EgressReceipt> = sealed.takeLast(limit.toInt())

        override suspend fun countByPurpose(): Map<EgressPurpose, Long> =
            sealed.groupingBy { it.purpose }.eachCount().mapValues { it.value.toLong() }

        override suspend fun totalBytes(): Long = sealed.sumOf { it.bytes }

        override fun observe(): kotlinx.coroutines.flow.Flow<List<EgressReceipt>> = flowOf(sealed.toList())
    }
}
