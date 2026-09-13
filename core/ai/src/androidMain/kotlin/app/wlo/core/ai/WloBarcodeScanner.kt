package app.wlo.core.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.wlo.core.model.Analysis
import app.wlo.core.model.Provenance
import app.wlo.core.ports.Barcode
import app.wlo.core.ports.BarcodeScanner
import app.wlo.core.ports.CapturedFrame
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import com.google.mlkit.vision.barcode.common.Barcode as MlKitBarcode

/**
 * The `BarcodeScanner` port implementation (F02 §3 rung 3): ML Kit bundled
 * barcode scanning (audit card: docs/tech/audit/mlkit-barcode.md — R-S13
 * on-device disclosure; R-S14 tracked-distribution exception) as the PRIMARY
 * engine, Apache-2.0 ZXing (via `zxing-android-embedded`, which carries zxing
 * core) as the FALLBACK behind the same port.
 *
 * FALLBACK SELECTION (documented per the port contract): the first ML Kit
 * engine ERROR — not a clean "no barcode in frame" miss — flips [mlKitHealthy]
 * permanently and every later frame is decoded by ZXing (`MultiFormatReader`
 * over the same memory-only JPEG frames). A clean miss never flips the flag;
 * a degraded engine is invisible until it actually fails.
 *
 * Frames arrive via [observe] at analysis rate; each is one in-memory JPEG
 * buffer (R-U14 pipeline — nothing here touches disk). Decodes are strided
 * (a frame is skipped while one is in flight). Results queue in a CONFLATED
 * channel: [scanNext] awaits the next decode, and rapid re-scans replace the
 * stale result rather than queueing ghosts.
 */
public class WloBarcodeScanner(
    private val now: () -> Instant = { Instant.fromEpochMilliseconds(Clock.systemUTC().millis()) },
) : BarcodeScanner {
    private val options: BarcodeScannerOptions =
        BarcodeScannerOptions
            .Builder()
            .setBarcodeFormats(
                MlKitBarcode.FORMAT_EAN_13,
                MlKitBarcode.FORMAT_EAN_8,
                MlKitBarcode.FORMAT_UPC_A,
                MlKitBarcode.FORMAT_UPC_E,
                MlKitBarcode.FORMAT_CODE_128,
                MlKitBarcode.FORMAT_CODE_39,
                MlKitBarcode.FORMAT_QR_CODE,
                MlKitBarcode.FORMAT_DATA_MATRIX,
                MlKitBarcode.FORMAT_ITF,
                MlKitBarcode.FORMAT_CODABAR,
            ).build()

    private val client by lazy { BarcodeScanning.getClient(options) }
    private val zxingReader by lazy { MultiFormatReader() }
    private val fallbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val results = Channel<Analysis<Barcode>>(Channel.CONFLATED)
    private val busy = AtomicBoolean(false)

    /** Flips to false on the first ML Kit engine error → ZXing takes over. */
    public var mlKitHealthy: Boolean = true
        private set

    /** Test seam: force the fallback engine (the audit's fallback path). */
    public fun useZxingFallbackForTesting() {
        mlKitHealthy = false
    }

    override fun observe(frame: CapturedFrame) {
        if (!busy.compareAndSet(false, true)) return // stride: skip while decoding
        if (mlKitHealthy) {
            decodeWithMlKit(frame)
        } else {
            decodeWithZxing(frame)
        }
    }

    override suspend fun scanNext(): Analysis<Barcode>? = results.receive()

    private fun decodeWithMlKit(frame: CapturedFrame) {
        val bitmap = decode(frame)
        if (bitmap == null) {
            busy.set(false)
            return
        }
        client
            .process(InputImage.fromBitmap(bitmap, 0))
            .awaitQuietly(
                onSuccess = { barcodes ->
                    bitmap.recycle()
                    busy.set(false)
                    barcodes.firstOrNull()?.rawValue?.let { value ->
                        publish(value, formatName(barcodes.first().format))
                    }
                },
                onFailure = { _ ->
                    bitmap.recycle()
                    // Engine ERROR (not a miss): the ZXing fallback takes over.
                    mlKitHealthy = false
                    decodeWithZxing(frame)
                },
            )
    }

    private fun decodeWithZxing(frame: CapturedFrame) {
        fallbackScope.launch {
            val decoded = runCatching { zxingDecode(frame) }.getOrNull()
            busy.set(false)
            decoded?.let { publish(it.value, it.format) }
        }
    }

    private fun zxingDecode(frame: CapturedFrame): Barcode? {
        val bitmap = decode(frame) ?: return null
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        val source = RGBLuminanceSource(width, height, pixels)
        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
        return try {
            val result = zxingReader.decode(BinaryBitmap(HybridBinarizer(source)), hints)
            Barcode(value = result.text, format = result.barcodeFormat.name)
        } catch (notFound: NotFoundException) {
            null // a clean miss — the engine stays healthy
        } finally {
            zxingReader.reset()
        }
    }

    private fun publish(
        value: String,
        format: String,
    ) {
        results.trySend(
            Analysis(
                value = Barcode(value = value, format = format),
                confidence = null,
                provenance = Provenance.Measured(at = now(), instrument = ENGINE_INSTRUMENT),
                held = false,
            ),
        )
    }

    public companion object {
        /** Provenance instrument line (engine resolved at decode time). */
        public const val ENGINE_INSTRUMENT: String = "on-device barcode"

        private fun decode(frame: CapturedFrame): Bitmap? = BitmapFactory.decodeByteArray(frame.bytes, 0, frame.bytes.size)

        private fun formatName(format: Int): String =
            when (format) {
                MlKitBarcode.FORMAT_EAN_13 -> "EAN_13"
                MlKitBarcode.FORMAT_EAN_8 -> "EAN_8"
                MlKitBarcode.FORMAT_UPC_A -> "UPC_A"
                MlKitBarcode.FORMAT_UPC_E -> "UPC_E"
                MlKitBarcode.FORMAT_CODE_128 -> "CODE_128"
                MlKitBarcode.FORMAT_CODE_39 -> "CODE_39"
                MlKitBarcode.FORMAT_QR_CODE -> "QR_CODE"
                MlKitBarcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
                MlKitBarcode.FORMAT_ITF -> "ITF"
                MlKitBarcode.FORMAT_CODABAR -> "CODABAR"
                else -> "UNKNOWN"
            }
    }
}

/** Never-throwing Task bridge: ML Kit failures are values here, never crashes. */
private inline fun <T : Any> Task<T>.awaitQuietly(
    crossinline onSuccess: (T) -> Unit,
    crossinline onFailure: (Exception) -> Unit,
) {
    addOnSuccessListener { value -> if (value != null) onSuccess(value) }
    addOnFailureListener { failure -> onFailure(failure) }
    addOnCanceledListener { /* a cancelled decode is a miss */ }
}
