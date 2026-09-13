package app.wlo.feature.f02.food.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.wlo.core.ports.CapturedFrame
import app.wlo.feature.f02.food.state.CaptureLensMode

/*
 * The capture flow's viewfinder slot (F02 §3 rungs 1-3). The VIEWFINDER
 * itself arrives as a SLOT — the camera stack lives in :core:media and :app
 * wires it in (D1: features cannot see the implementation); the contract
 * below is the only coupling.
 *
 * R-U15 everywhere: the manual ladder is one tap from every stage, the OCR
 * draft is the SAME custom-food form (prefilled, confirm-gated), a barcode
 * can be typed as well as scanned, and every failure lands with kind copy.
 */

/** The shutter handle the slot binds; the flow's shutter button drives it. */
public class CaptureShutter {
    private var impl: ((onFrame: (CapturedFrame) -> Unit) -> Unit)? = null

    /** The :app adapter binds the live camera's still-taker here. */
    public fun bind(impl: (onFrame: (CapturedFrame) -> Unit) -> Unit) {
        this.impl = impl
    }

    public fun take(onFrame: (CapturedFrame) -> Unit) {
        impl?.invoke(onFrame)
    }
}

/**
 * The viewfinder slot: :app composes [app.wlo.core.media.WloViewfinder] into
 * it. Frames are memory-only (R-U14 pipeline); nothing here persists. Stills
 * travel through the [CaptureShutter]; preview frames stream to [onPreview].
 */
public typealias CaptureViewfinderSlot =
    @Composable (
        modifier: Modifier,
        mode: CaptureLensMode,
        shutter: CaptureShutter,
        onPreview: (CapturedFrame) -> Unit,
        isActive: Boolean,
    ) -> Unit
