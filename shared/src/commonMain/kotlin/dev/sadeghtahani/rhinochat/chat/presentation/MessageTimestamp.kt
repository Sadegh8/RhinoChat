package dev.sadeghtahani.rhinochat.chat.presentation

import dev.sadeghtahani.rhinochat.chat.presentation.model.MessageTimestamp

/** Presentation formatting uses the device locale, time zone, and platform date conventions. */
internal expect fun formatMessageTimestamp(epochMillis: Long): MessageTimestamp
