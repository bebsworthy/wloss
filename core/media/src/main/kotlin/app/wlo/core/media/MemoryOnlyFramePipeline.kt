package app.wlo.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import app.wlo.core.ports.CapturedFrame
import java.io.ByteArrayOutputStream

/**
 * The R-U14 frame pipeline (the F08 R-U16 memory-only contract is the
 * precedent — here the SAME architecture serves photo-discard-by-default):
 *
 *  - **Frames never touch disk.** CameraX hands us `ImageProxy` buffers; they
 *    are converted (YUV → JPEG) entirely in RAM via [ByteArrayOutputStream]
 *    and returned as a [CapturedFrame]. There is no output-file code path, no
 *    MediaStore insert, no cache file — retention decisions (R-U14's opt-in
 *    thumbnail mode, the per-capture "keep this one") belong to a LATER
 *    milestone's persistence work, which must ask FIRST. Until then
 *    discard-at-save is not a setting, it's the only behavior.
 *  - **No EXIF, no GPS, no filename.** The JPEG authored in memory carries no
 *    metadata block at all; analyzers decode pixels only. What the capture
 *    ladder consumes downstream can therefore never leak location or device
 *    metadata — F12 §3.7's EXIF-strip rule is satisfied by construction.
 *  - **≤1024 px before anything else.** [MAX_ANALYSIS_SIDE_PX] enforces the
 *    F12 §3.7 payload-ladder image ceiling for every frame leaving this
 *    module (the classifier needs 224 px; OCR reads comfortably at 1024).
 *
 * Silent shutter: NOT specced (F02 §3/§4 spec the haptic tick, no silence
 * rule); the viewfinder plays the DESIGN-SYSTEM §5 shutter tick and never
 * suppresses platform shutter behavior.
 */
public object MemoryOnlyFramePipeline {
    /** F12 §3.7: the payload ladder's image ceiling — nothing ships bigger. */
    public const val MAX_ANALYSIS_SIDE_PX: Int = 1024

    /** JPEG quality for preview/analysis frames (stills re-encode at 90). */
    private const val ANALYSIS_JPEG_QUALITY: Int = 70

    private const val STILL_JPEG_QUALITY: Int = 90

    /**
     * One analysis frame: YUV → NV21 → JPEG (in-memory) → rotate → ≤1024 px →
     * [CapturedFrame]. Returns null on any conversion failure — a dropped
     * frame is silence, never an error.
     */
    public fun analysisFrame(
        imageProxy: ImageProxy,
        maxSidePx: Int = MAX_ANALYSIS_SIDE_PX,
    ): CapturedFrame? =
        runCatching {
            val nv21 = nv21From(imageProxy)
            val fullJpeg = jpegFromNv21(nv21, imageProxy.width, imageProxy.height, ANALYSIS_JPEG_QUALITY)
            normalizedCapturedFrame(
                jpegBytes = fullJpeg,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                maxSidePx = maxSidePx,
                quality = ANALYSIS_JPEG_QUALITY,
            )
        }.getOrNull()

    /**
     * One STILL capture frame (the shutter): the same memory-only path at
     * higher quality. This is what photo/label analysis receives — and the
     * ONLY thing a future opt-in retention would ever receive. The capture
     * use case requests JPEG output ([ImageCapture.OUTPUT_IMAGE_FORMAT_JPEG]),
     * so the common case reads the encoded bytes directly (no YUV conversion;
     * some HALs expose degenerate chroma planes for stills).
     */
    public fun stillFrame(imageProxy: ImageProxy): CapturedFrame? =
        runCatching {
            if (imageProxy.format == android.graphics.ImageFormat.JPEG) {
                val buffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).also(buffer::get)
                normalizedCapturedFrame(
                    jpegBytes = bytes,
                    rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                    maxSidePx = MAX_ANALYSIS_SIDE_PX,
                    quality = STILL_JPEG_QUALITY,
                )
            } else {
                val nv21 = nv21From(imageProxy)
                val fullJpeg = jpegFromNv21(nv21, imageProxy.width, imageProxy.height, STILL_JPEG_QUALITY)
                normalizedCapturedFrame(
                    jpegBytes = fullJpeg,
                    rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                    maxSidePx = MAX_ANALYSIS_SIDE_PX,
                    quality = STILL_JPEG_QUALITY,
                )
            }
        }.getOrNull()

    /** Decode → rotate → downscale ≤ [maxSidePx] → re-encode. All in RAM. */
    public fun normalizedCapturedFrame(
        jpegBytes: ByteArray,
        rotationDegrees: Int,
        maxSidePx: Int,
        quality: Int,
    ): CapturedFrame {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val decoded =
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options)
                ?: error("frame decode failed")
        val rotated =
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            } else {
                decoded
            }
        val scaled = downscaleTo(rotated, maxSidePx)
        val width = scaled.width
        val height = scaled.height
        val out = ByteArrayOutputStream(width * height / 2)
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== rotated) scaled.recycle()
        if (rotated !== decoded) rotated.recycle()
        decoded.recycle()
        return CapturedFrame(bytes = out.toByteArray(), widthPx = width, heightPx = height)
    }

    /** Downscale so the LONG side is ≤ [maxSidePx] (short side follows aspect). */
    public fun downscaleTo(
        source: Bitmap,
        maxSidePx: Int,
    ): Bitmap {
        val longSide = maxOf(source.width, source.height)
        if (longSide <= maxSidePx) return source
        val ratio = maxSidePx.toFloat() / longSide.toFloat()
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    /** CameraX YUV_420_888 planes → single NV21 buffer (stride-tolerant). */
    private fun nv21From(proxy: ImageProxy): ByteArray {
        val yPlane = proxy.planes[0]
        val uPlane = proxy.planes[1]
        val vPlane = proxy.planes[2]
        val width = proxy.width
        val height = proxy.height
        val nv21 = ByteArray(width * height + 2 * (width / 2) * (height / 2))
        var pos = 0
        val yBuf = yPlane.buffer.duplicate()
        if (yPlane.rowStride == width) {
            yBuf.get(nv21, 0, width * height)
            pos = width * height
        } else {
            for (row in 0 until height) {
                yBuf.position(row * yPlane.rowStride)
                yBuf.get(nv21, pos, width)
                pos += width
            }
        }
        // Interleave V before U (NV21 = Y plane + VU interleaved).
        val uBuf = uPlane.buffer.duplicate()
        val vBuf = vPlane.buffer.duplicate()
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
                val vIndex = row * vPlane.rowStride + col * vPlane.pixelStride
                nv21[pos++] = vBuf.get(vIndex)
                nv21[pos++] = uBuf.get(uIndex)
            }
        }
        return nv21
    }

    private fun jpegFromNv21(
        nv21: ByteArray,
        width: Int,
        height: Int,
        quality: Int,
    ): ByteArray {
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream(nv21.size / 2)
        yuv.compressToJpeg(Rect(0, 0, width, height), quality, out)
        return out.toByteArray()
    }
}
