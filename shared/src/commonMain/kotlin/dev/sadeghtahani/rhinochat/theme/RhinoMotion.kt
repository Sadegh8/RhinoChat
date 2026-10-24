package dev.sadeghtahani.rhinochat.theme

import androidx.compose.animation.core.spring

/** Material Expressive spring tokens through stable animation APIs. See docs/RESEARCH.md. */
internal object RhinoMotion {
    fun <T> spatial() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> fastSpatial() = spring<T>(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> effects() = spring<T>(dampingRatio = 1f, stiffness = 1_600f)
}
