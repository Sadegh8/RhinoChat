package dev.sadeghtahani.rhinochat.chat

import dev.sadeghtahani.rhinochat.chat.data.MessageTimestamp

/** Presentation formatting uses the device locale, time zone, and platform date conventions. */
internal expect fun formatMessageTimestamp(epochMillis: Long): MessageTimestamp
