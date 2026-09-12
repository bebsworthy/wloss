package app.wlo.core.ports

import app.wlo.core.model.Analysis

/**
 * Speech-to-text for voice quick-logs (F02/F05/F10); cloud STT is gated by the
 * `voice-input` capability, and the on-device path is the equal-status
 * fallback (R-U15 manual-parity discipline).
 */
public interface SpeechTranscriber {
    public suspend fun transcribe(audio: SpeechAudio): Analysis<String>
}

/** Raw audio bytes plus an optional BCP-47 hint; nothing here persists. */
public class SpeechAudio(
    public val bytes: ByteArray,
    public val languageTag: String?,
)
