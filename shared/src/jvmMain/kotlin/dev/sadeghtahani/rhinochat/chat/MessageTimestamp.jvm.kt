package dev.sadeghtahani.rhinochat.chat

import dev.sadeghtahani.rhinochat.chat.data.MessageTimestamp
import java.text.DateFormat
import java.util.Date

internal actual fun formatMessageTimestamp(epochMillis: Long): MessageTimestamp {
    val date = Date(epochMillis)
    return MessageTimestamp(
        time = DateFormat.getTimeInstance(DateFormat.SHORT).format(date),
        date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(date),
    )
}
