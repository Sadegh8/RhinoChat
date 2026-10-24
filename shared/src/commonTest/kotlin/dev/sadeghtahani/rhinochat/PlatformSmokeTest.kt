package dev.sadeghtahani.rhinochat

import kotlin.test.Test
import kotlin.test.assertTrue

/** Bootstrap coverage of the shared test runner and expect/actual platform wiring. */
class PlatformSmokeTest {
    @Test
    fun platformFactoryProvidesAnActualImplementation() {
        assertTrue(getPlatform().name.isNotBlank())
    }
}
