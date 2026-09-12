package app.wlo.core.common

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatcher injection port: production implementation wires platform
 * dispatchers; tests inject test dispatchers. UI layer is out of scope for the
 * core (`main` may be unsupported on JVM-only targets by the implementation).
 */
public interface DispatcherProvider {
    public val io: CoroutineDispatcher
    public val default: CoroutineDispatcher
    public val main: CoroutineDispatcher
}
