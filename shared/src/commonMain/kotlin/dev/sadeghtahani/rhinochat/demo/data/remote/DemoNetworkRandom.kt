package dev.sadeghtahani.rhinochat.demo.data.remote

import dev.sadeghtahani.rhinochat.demo.data.DemoNetworkControls
import kotlin.random.Random

/** Keeps the supplied fake's default failure policy, with a reliable-connection test override. */
class DemoNetworkRandom(
    private val controls: DemoNetworkControls,
    private val delegate: Random = Random.Default,
) : Random() {
    override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

    override fun nextDouble(): Double =
        if (controls.settings.value.randomFailures) delegate.nextDouble() else 0.5
}
