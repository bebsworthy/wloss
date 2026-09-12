package app.wlo.core.ports

/**
 * Biometric unlock gate for the vault and FLAG_SECURE surfaces (F08/F09/F13 §9).
 * Framework BiometricPrompt details stay behind this port (API 29 graceful
 * fallbacks per ADR-002).
 */
public interface BiometricGate {
    /** Returns success when the user passed the gate; failure carries the reason. */
    public suspend fun unlock(reason: String): Result<Unit>
}
