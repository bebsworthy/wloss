package app.wlo.core.testing

import app.wlo.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Eager, deterministic dispatcher set for tests: everything runs on the
 * provided test dispatcher, so `runTest` bodies complete without manual
 * `advanceUntilIdle` calls in the common case.
 */
public class FakeDispatcherProvider(
    private val testDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
) : DispatcherProvider {
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val main: CoroutineDispatcher = testDispatcher
}
