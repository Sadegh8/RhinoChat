package dev.sadeghtahani.rhinochat.chat.data.remote.supplied

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.random.Random

// Supplied challenge API; keep its behavior unchanged and adapt outside this file.
data class MessageDto(
    val id: String,
    val clientId: String?,   // شناسه‌ای که کلاینت هنگام ارسال می‌سازد
    val senderId: String,    // "me" یا "colleague"
    val text: String,
    val createdAt: Long      // epoch millis
)

data class MessagePageDto(
    val messages: List<MessageDto>, // جدیدترین اول
    val nextCursor: String?         // null یعنی پیام قدیمی‌تری نیست
)

interface ChatApi {
    suspend fun getMessages(conversationId: String, cursor: String?, limit: Int = 20): MessagePageDto
    suspend fun sendMessage(conversationId: String, clientId: String, text: String): MessageDto
}

class FakeChatApi(
    private val failureRate: Double = 0.2,
    private val random: Random = Random.Default
) : ChatApi {
    private val mutex = Mutex()
    private val messages = MutableList(120) { i ->
        MessageDto(
            id = "srv-$i",
            clientId = null,
            senderId = if (i % 3 == 0) "me" else "colleague",
            text = "پیام شماره $i",
            createdAt = 1_760_000_000_000L + i * 60_000L
        )
    }

    override suspend fun getMessages(conversationId: String, cursor: String?, limit: Int): MessagePageDto {
        simulateNetwork()
        return mutex.withLock {
            val sorted = messages.sortedByDescending { it.createdAt }
            val start = cursor?.toInt() ?: 0
            val page = sorted.drop(start).take(limit)
            val next = (start + page.size).takeIf { it < sorted.size }?.toString()
            MessagePageDto(page, next)
        }
    }

    override suspend fun sendMessage(conversationId: String, clientId: String, text: String): MessageDto {
        simulateNetwork()
        return mutex.withLock {
            val existing = messages.firstOrNull { it.clientId == clientId }
            if (existing != null) {
                existing
            } else {
                val dto = MessageDto("srv-${messages.size}", clientId, "me", text, System.currentTimeMillis())
                messages += dto
                dto
            }
        }
    }

    private suspend fun simulateNetwork() {
        delay(random.nextLong(300, 1500))
        if (random.nextDouble() < failureRate) throw IOException("Simulated network error")
    }
}
