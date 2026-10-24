package dev.sadeghtahani.rhinochat

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform