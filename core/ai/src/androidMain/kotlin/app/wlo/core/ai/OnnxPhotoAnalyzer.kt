package app.wlo.core.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.wlo.core.model.Analysis
import app.wlo.core.model.HoldReason
import app.wlo.core.model.Provenance
import app.wlo.core.ports.CapturedFrame
import app.wlo.core.ports.FoodSuggestion
import app.wlo.core.ports.ModelManager
import app.wlo.core.ports.PhotoAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import java.nio.FloatBuffer
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The ADR-006/ADR-007 food-photo analyzer: ONNX Runtime Mobile executing the
 * R-S14 zoo model `food-classifier/1` (MobileNetV2 Food-101, ONNX fp32).
 *
 * WHY an android-only class (not expect/actual): the analyzer is intrinsically
 * Android (Bitmap decode + the ORT Android AAR); the JVM purity target keeps
 * the pure half — [SuggestionSelector], [Food101Catalog] — and runs it in CI
 * against synthetic logits. Nothing in commonMain references this class; the
 * composition root (:app) binds it, so the jvm target compiles untouched.
 *
 * Preprocessing is torchvision's EVAL transform, pinned by ADR-007:
 * resize short side 256 (aspect kept) → center crop 224×224 → /255 →
 * ImageNet normalize (mean .485/.456/.406, std .229/.224/.225) → float32 NCHW.
 * EXIF/GPS/filename never exist here: [CapturedFrame] is a bare pixel buffer
 * (the R-U14 pipeline hands memory-only JPEG bytes it authored itself), and
 * BitmapFactory decodes pixels only.
 *
 * Threading: ONE [OrtSession] guarded by a mutex, intra-op threads pinned to
 * 1 (R-S13 audit card: small model, phone-sized footprint); inference runs on
 * [Dispatchers.Default]. A session that failed to open once is not retried
 * per-frame — [sessionBroken] short-circuits into the held path until
 * [resetForTesting] or process restart.
 *
 * Failure posture (R-U15): missing model, unverified artifact, decode error,
 * ORT exception — EVERY failure returns a held, EMPTY analysis; the UI says
 * "model not downloaded" (from the zoo state) and keeps the manual twin one
 * tap away. This method never throws and never guesses.
 */
public class OnnxPhotoAnalyzer(
    context: Context,
    private val modelManager: ModelManager,
    private val modelId: String = MODEL_ID,
    private val holdThreshold: Double = PhotoAnalyzer.DEFAULT_HOLD_CONFIDENCE,
    private val topK: Int = PhotoAnalyzer.TOP_K,
    private val now: () -> Instant = { Instant.fromEpochMilliseconds(Clock.systemUTC().millis()) },
) : PhotoAnalyzer {
    private val sessionMutex = Mutex()
    private var session: OrtSession? = null
    private val sessionBroken = AtomicBoolean(false)

    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    override suspend fun analyze(frame: CapturedFrame): Analysis<List<FoodSuggestion>> {
        val logits = runInference(frame)
        val (suggestions, held) =
            if (logits == null) {
                emptyList<FoodSuggestion>() to true
            } else {
                SuggestionSelector.select(logits, topK = topK, holdThreshold = holdThreshold)
            }
        return SuggestionSelector.toAnalysis(suggestions, held, modelId, now())
    }

    /** The held-empty result every failure mode collapses to (held, never guessed). */
    public fun heldAnalysis(): Analysis<List<FoodSuggestion>> =
        Analysis(
            value = emptyList(),
            confidence = null,
            provenance = Provenance.Held(HoldReason.INSUFFICIENT_DATA),
            held = true,
        )

    private suspend fun runInference(frame: CapturedFrame): FloatArray? =
        withContext(Dispatchers.Default) {
            if (sessionBroken.get()) return@withContext null
            val handle =
                modelManager.ensureAvailable(modelId).getOrElse {
                    // "model not downloaded" (or unverified): held, never a crash.
                    return@withContext null
                }
            val path = handle.filePath ?: return@withContext null
            val input = preprocess(frame) ?: return@withContext null
            sessionMutex.withLock {
                try {
                    val active = session ?: environment.createSession(path, sessionOptions()).also { session = it }
                    val inputName = active.inputNames.firstOrNull() ?: return@withLock null
                    OnnxTensor.createTensor(environment, FloatBuffer.wrap(input.data), INPUT_SHAPE).use { tensor ->
                        active.run(mapOf(inputName to tensor)).use { result ->
                            val output = result[0] as? OnnxTensor ?: return@withLock null
                            output.floatBuffer?.let { buffer ->
                                FloatArray(buffer.remaining()).also { buffer.get(it) }
                            }
                        }
                    }
                } catch (failure: Throwable) {
                    // Broken artifact / runtime failure: stop per-frame retries.
                    closeSessionQuietly()
                    sessionBroken.set(true)
                    null
                }
            }
        }

    /**
     * torchvision eval transform on the frame's pixels: decode → short-side
     * 256 → center-crop 224 → normalized NCHW floats. Public for instrumented
     * verification of the pinned math (ADR-007); memory-only, bitmaps recycled.
     */
    public fun preprocess(frame: CapturedFrame): FrameInput? {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val decoded =
            BitmapFactory.decodeByteArray(frame.bytes, 0, frame.bytes.size, options)
                ?: return null
        val scaled = scaleShortSide(decoded, SHORT_SIDE)
        val cropped = centerCrop(scaled, INPUT_SIZE, INPUT_SIZE)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        cropped.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (cropped !== scaled) cropped.recycle()
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return FrameInput(toNormalizedNchw(pixels))
    }

    private fun scaleShortSide(
        source: Bitmap,
        shortSide: Int,
    ): Bitmap {
        val minSide = minOf(source.width, source.height)
        if (minSide == shortSide) return source
        val ratio = shortSide.toFloat() / minSide.toFloat()
        val width = (source.width * ratio).toInt().coerceAtLeast(INPUT_SIZE)
        val height = (source.height * ratio).toInt().coerceAtLeast(INPUT_SIZE)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun centerCrop(
        source: Bitmap,
        width: Int,
        height: Int,
    ): Bitmap {
        val left = ((source.width - width) / 2).coerceAtLeast(0)
        val top = ((source.height - height) / 2).coerceAtLeast(0)
        if (left == 0 && top == 0 && source.width == width && source.height == height) return source
        return Bitmap.createBitmap(source, left, top, width, height)
    }

    /** RGB planes, /255, ImageNet mean/std — channel-major (NCHW, batch 1). */
    public fun toNormalizedNchw(pixels: IntArray): FloatArray {
        val plane = INPUT_SIZE * INPUT_SIZE
        val out = FloatArray(3 * plane)
        var i = 0
        while (i < pixels.size) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            out[i] = (r - MEAN_R) / STD_R
            out[plane + i] = (g - MEAN_G) / STD_G
            out[2 * plane + i] = (b - MEAN_B) / STD_B
            i++
        }
        return out
    }

    private fun closeSessionQuietly() {
        runCatching { session?.close() }
        session = null
    }

    /** Test seam: forget the cached session + broken flag (fresh artifact, etc.). */
    public suspend fun resetForTesting() {
        sessionMutex.withLock {
            closeSessionQuietly()
            sessionBroken.set(false)
        }
    }

    /** One frame's preprocessed tensor payload (224×224×3 normalized floats). */
    public data class FrameInput(
        public val data: FloatArray,
    ) {
        override fun equals(other: Any?): Boolean = other is FrameInput && data.contentEquals(other.data)

        override fun hashCode(): Int = data.contentHashCode()
    }

    public companion object {
        /** The zoo model this analyzer serves (ADR-007; the manifest's first entry). */
        public const val MODEL_ID: String = "food-classifier/1"

        public const val INPUT_SIZE: Int = 224

        public const val SHORT_SIDE: Int = 256

        private val INPUT_SHAPE = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())

        // ImageNet constants (torchvision defaults).
        private const val MEAN_R = 0.485f
        private const val MEAN_G = 0.456f
        private const val MEAN_B = 0.406f
        private const val STD_R = 0.229f
        private const val STD_G = 0.224f
        private const val STD_B = 0.225f

        /** Single-threaded session (R-S13 audit posture: bounded threads). */
        internal fun sessionOptions(): OrtSession.SessionOptions =
            OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(1)
                setInterOpNumThreads(1)
            }
    }
}
