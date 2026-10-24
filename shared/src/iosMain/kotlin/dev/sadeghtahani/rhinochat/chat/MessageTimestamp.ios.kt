package dev.sadeghtahani.rhinochat.chat

import dev.sadeghtahani.rhinochat.chat.data.MessageTimestamp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970

@OptIn(ExperimentalForeignApi::class)
internal actual fun formatMessageTimestamp(epochMillis: Long): MessageTimestamp {
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1_000.0)
    val formatter = NSDateFormatter()
    formatter.timeStyle = NSDateFormatterShortStyle
    formatter.dateStyle = NSDateFormatterNoStyle
    val time = formatter.stringFromDate(date)
    formatter.timeStyle = NSDateFormatterNoStyle
    formatter.dateStyle = NSDateFormatterMediumStyle
    return MessageTimestamp(time, formatter.stringFromDate(date))
}
