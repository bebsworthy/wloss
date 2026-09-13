package app.wlo.core.media

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.wlo.core.ports.CapturedFrame
import java.util.concurrent.Executors

/**
 * The shared capture surface (ARCHITECTURE §2.5: the ONE camera stack — F02
 * photo/barcode/label today, F04/F09 later). The composable is bound by :app
 * (D1) into the capture flow's viewfinder slot; features never see it.
 *
 * Modes:
 *  - [WloCaptureMode.PHOTO] / [WloCaptureMode.LABEL]: the flow's shutter calls
 *    [WloShutterBridge.takeStill] → one memory-only still via [onStillFrame].
 *  - [WloCaptureMode.BARCODE]: throttled preview frames stream to
 *    [onPreviewFrame] (the BarcodeScanner port consumes them).
 *
 * Emulator note: CameraX binds the emulated virtual-scene camera unchanged.
 */
public enum class WloCaptureMode {
    PHOTO,
    BARCODE,
    LABEL,
}

/** Analyze ~4 preview frames/second — barcode decode does not need 30. */
private const val PREVIEW_FRAME_INTERVAL_MS: Long = 250

/**
 * The still-taker the bound camera publishes; the flow's shutter button calls
 * [takeStill]. Re-published on every re-bind — callers hold the BRIDGE, not
 * the shutter, and always reach the live camera.
 */
public class WloShutterBridge {
    private var shutter: ((onFrame: (CapturedFrame) -> Unit) -> Unit)? = null
    private var pending: ((CapturedFrame) -> Unit)? = null

    internal fun attach(shutter: (onFrame: (CapturedFrame) -> Unit) -> Unit) {
        this.shutter = shutter
        // A take requested before the camera bound fires the moment it does.
        val queued = pending
        if (queued != null) {
            pending = null
            shutter.invoke(queued)
        }
    }

    /**
     * Take one still (memory-only, R-U14 pipeline) into [onFrame]. A request
     * that arrives before the camera finished binding is QUEUED, never
     * dropped — a fast shutter tap right after opening the flow still lands.
     */
    public fun takeStill(onFrame: (CapturedFrame) -> Unit) {
        val bound = shutter
        if (bound == null) {
            pending = onFrame
        } else {
            bound.invoke(onFrame)
        }
    }
}

/**
 * The viewfinder: permission-gated CameraX Preview (+ImageAnalysis in barcode
 * mode, +ImageCapture always). Camera work stops when [isActive] is false
 * (e.g. the result state) or the composable leaves the composition; binding
 * is idempotent per mode change.
 */
@Composable
public fun WloViewfinder(
    modifier: Modifier,
    mode: WloCaptureMode,
    shutter: WloShutterBridge,
    onPreviewFrame: (CapturedFrame) -> Unit,
    isActive: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionGranted = granted
        }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = modifier) {
        if (permissionGranted && isActive) {
            val previewView = remember { PreviewView(context) }
            val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
            val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
            var lastPreviewAt by remember { mutableLongStateOf(0L) }
            // THIS composition's bound use cases — dispose unbinds exactly
            // these, never unbindAll() (a sibling capture surface may have
            // bound already; a global unbind would close its camera).
            var boundUseCases by remember { mutableStateOf(listOf<androidx.camera.core.UseCase>()) }
            // A failed still (transient camera state — emulators underreport
            // cameras on cold start) re-runs the binding effect; the queued
            // take then fires on re-attach.
            var bindGeneration by remember { mutableStateOf(0) }

            DisposableEffect(mode, bindGeneration) {
                val providerFuture = ProcessCameraProvider.getInstance(context)
                val onBound =
                    Runnable {
                        val provider = providerFuture.get()
                        val preview =
                            Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val capture =
                            ImageCapture
                                .Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                        // Publish the live shutter (main-thread; UI calls arrive there).
                        shutter.attach { onFrame ->
                            capture.takePicture(
                                mainExecutor,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val frame = MemoryOnlyFramePipeline.stillFrame(image)
                                        image.close()
                                        frame?.let(onFrame)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        // A failed still is silence for the flow —
                                        // but the binding self-heals: the next take
                                        // re-runs this effect on a fresh bind.
                                        bindGeneration++
                                    }
                                },
                            )
                        }
                        var analysisUseCase: ImageAnalysis? = null
                        if (mode == WloCaptureMode.BARCODE) {
                            val analysis =
                                ImageAnalysis
                                    .Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { analyzerView ->
                                        analyzerView.setAnalyzer(
                                            analysisExecutor,
                                            { proxy: ImageProxy ->
                                                val now = System.currentTimeMillis()
                                                val due = now - lastPreviewAt >= PREVIEW_FRAME_INTERVAL_MS
                                                if (due) {
                                                    lastPreviewAt = now
                                                    MemoryOnlyFramePipeline.analysisFrame(proxy)?.let(onPreviewFrame)
                                                }
                                                proxy.close()
                                            },
                                        )
                                    }
                            analysisUseCase = analysis
                        }
                        provider.unbindAll()
                        // Explicit call sites keep the spread operator out
                        // (detekt: SpreadOperator).
                        val selector = CameraSelector.DEFAULT_BACK_CAMERA
                        if (analysisUseCase == null) {
                            provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                            boundUseCases = listOf(preview, capture)
                        } else {
                            provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysisUseCase)
                            boundUseCases = listOf(preview, capture, analysisUseCase)
                        }
                    }
                mainExecutor.execute(onBound)
                onDispose {
                    runCatching { providerFuture.get() }.getOrNull()?.let { provider ->
                        for (useCase in boundUseCases) {
                            runCatching { provider.unbind(useCase) }
                        }
                    }
                    analysisExecutor.shutdown()
                }
            }

            androidx.compose.ui.viewinterop.AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )

            // Framing brackets in LABEL mode (DESIGN-SYSTEM: "OCR viewfinder
            // brackets" — v1 renders the static bracket, snap-green is later).
            if (mode == WloCaptureMode.LABEL) {
                LabelBrackets(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun LabelBrackets(modifier: Modifier) {
    val bracket = MaterialTheme.colorScheme.primary
    Canvas(modifier.padding(24.dp)) {
        val stroke = 4.dp.toPx()
        // Four corner brackets around the label area.
        val len = 56f
        val inset = 0f
        val w = size.width
        val h = size.height

        fun corner(
            x: Float,
            y: Float,
            dx: Float,
            dy: Float,
        ) {
            drawLine(bracket, Offset(x, y), Offset(x + dx * len, y), strokeWidth = stroke)
            drawLine(bracket, Offset(x, y), Offset(x, y + dy * len), strokeWidth = stroke)
        }
        corner(inset, inset, 1f, 1f)
        corner(w - inset, inset, -1f, 1f)
        corner(inset, h - inset, 1f, -1f)
        corner(w - inset, h - inset, -1f, -1f)
    }
}
