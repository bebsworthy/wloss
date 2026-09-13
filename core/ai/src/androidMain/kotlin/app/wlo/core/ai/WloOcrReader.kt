package app.wlo.core.ai

import android.graphics.BitmapFactory
import app.wlo.core.model.Analysis
import app.wlo.core.model.HoldReason
import app.wlo.core.model.Provenance
import app.wlo.core.ports.CapturedFrame
import app.wlo.core.ports.OcrReader
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Instant
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * The `OcrReader` port implementation (F02 §3 rung 3, label path): ML Kit
 * text recognition v2, LATIN script, bundled (audit card:
 * docs/tech/audit/mlkit-text-recognition.md — R-S13 on-device disclosure;
 * R-S14 tracked-distribution exception for the bundled model). Runs entirely
 * on-device against the memory-only ≤1024 px frame; nothing is saved without
 * the user confirming the parsed draft (R-U15: the manual custom-food form is
 * the SAME screen, empty).
 */
public class WloOcrReader(
    private val now: () -> Instant = { Instant.fromEpochMilliseconds(Clock.systemUTC().millis()) },
) : OcrReader {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val busy = AtomicBoolean(false)

    override suspend fun read(frame: CapturedFrame): Analysis<String> {
        val bitmap =
            BitmapFactory.decodeByteArray(frame.bytes, 0, frame.bytes.size)
                ?: return held()
        if (!busy.compareAndSet(false, true)) return held()
        return try {
            val text =
                suspendCancellableCoroutine { continuation ->
                    client
                        .process(InputImage.fromBitmap(bitmap, 0))
                        .awaitQuietly(
                            onSuccess = { text -> continuation.resume(text.text) },
                            onFailure = { _ -> continuation.resume(null) },
                        )
                }
            if (text.isNullOrBlank()) {
                held()
            } else {
                Analysis(
                    value = text,
                    confidence = null,
                    provenance = Provenance.Measured(at = now(), instrument = INSTRUMENT),
                    held = false,
                )
            }
        } finally {
            busy.set(false)
            bitmap.recycle()
        }
    }

    private fun held(): Analysis<String> =
        Analysis(
            value = "",
            confidence = null,
            provenance = Provenance.Held(HoldReason.INSUFFICIENT_DATA),
            held = true,
        )

    public companion object {
        /** Provenance instrument line (the "how we got here" sheet shows this). */
        public const val INSTRUMENT: String = "on-device OCR (latin)"
    }
}

/** Never-throwing Task bridge for the one-shot OCR call. */
private inline fun <T : Any> Task<T>.awaitQuietly(
    crossinline onSuccess: (T) -> Unit,
    crossinline onFailure: (Exception) -> Unit,
) {
    addOnSuccessListener { value -> if (value != null) onSuccess(value) }
    addOnFailureListener { failure -> onFailure(failure) }
    addOnCanceledListener { }
}
