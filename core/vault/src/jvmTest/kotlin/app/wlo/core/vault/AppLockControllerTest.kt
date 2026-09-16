package app.wlo.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockControllerTest {
    @Test
    fun `startup is unresolved and therefore fail closed`() {
        val controller = AppLockController()

        assertEquals(AppLockPosture.UNRESOLVED, controller.posture.value)
        assertTrue(controller.shouldLock(nowEpochMs = 0L, timeout = LockTimeout.FIVE_MINUTES))
    }

    @Test
    fun `cold start locks when enabled for every timeout`() {
        LockTimeout.entries.forEach { timeout ->
            val controller = AppLockController()

            controller.resolveStartup(enabled = true)

            assertEquals(AppLockPosture.LOCKED, controller.posture.value, timeout.wireName)
            assertTrue(controller.shouldLock(nowEpochMs = 0L, timeout = timeout), timeout.wireName)
        }
    }

    @Test
    fun `cold start is unlocked when disabled`() {
        val controller = AppLockController()

        controller.resolveStartup(enabled = false)

        assertEquals(AppLockPosture.UNLOCKED, controller.posture.value)
        assertFalse(controller.shouldLock(nowEpochMs = 0L, timeout = LockTimeout.IMMEDIATE))
    }

    @Test
    fun `activity recreation preserves an unlock in the same process`() {
        val controller = AppLockController()
        controller.resolveStartup(enabled = true)
        controller.onUnlock()

        controller.resolveStartup(enabled = true)

        assertEquals(AppLockPosture.UNLOCKED, controller.posture.value)
    }

    @Test
    fun `immediate timeout locks on foreground`() {
        val controller = unlockedController()
        controller.onBackground(atEpochMs = 1_000L)

        controller.onForeground(atEpochMs = 1_000L, timeout = LockTimeout.IMMEDIATE)

        assertEquals(AppLockPosture.LOCKED, controller.posture.value)
    }

    @Test
    fun `one minute timeout locks only after threshold`() {
        val controller = unlockedController()
        controller.onBackground(atEpochMs = 1_000L)

        controller.onForeground(atEpochMs = 60_999L, timeout = LockTimeout.ONE_MINUTE)

        assertEquals(AppLockPosture.UNLOCKED, controller.posture.value)

        controller.onBackground(atEpochMs = 1_000L)
        controller.onForeground(atEpochMs = 61_000L, timeout = LockTimeout.ONE_MINUTE)
        assertEquals(AppLockPosture.LOCKED, controller.posture.value)
    }

    @Test
    fun `five minute timeout locks only after threshold`() {
        val controller = unlockedController()
        controller.onBackground(atEpochMs = 1_000L)

        controller.onForeground(atEpochMs = 300_999L, timeout = LockTimeout.FIVE_MINUTES)

        assertEquals(AppLockPosture.UNLOCKED, controller.posture.value)

        controller.onBackground(atEpochMs = 1_000L)
        controller.onForeground(atEpochMs = 301_000L, timeout = LockTimeout.FIVE_MINUTES)
        assertEquals(AppLockPosture.LOCKED, controller.posture.value)
    }

    private fun unlockedController(): AppLockController = AppLockController().also { it.resolveStartup(enabled = false) }
}
