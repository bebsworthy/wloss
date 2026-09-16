package app.wlo.app.ui.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileFactsViewModelTest {
    @Test
    fun birthYearRejectsFutureAndRetainsSupportedLowerBound() {
        assertTrue(ProfileFactsViewModel.isBirthYearSupported("1900", 2026))
        assertTrue(ProfileFactsViewModel.isBirthYearSupported("2026", 2026))
        assertFalse(ProfileFactsViewModel.isBirthYearSupported("2027", 2026))
        assertFalse(ProfileFactsViewModel.isBirthYearSupported("not a year", 2026))
    }
}
