package dev.sadeghtahani.rhinochat

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatMessage
import dev.sadeghtahani.rhinochat.chat.domain.model.DeliveryStatus
import dev.sadeghtahani.rhinochat.testing.ChatUiFixture
import dev.sadeghtahani.rhinochat.testing.SendReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** UI actions -> production Activity/ViewModel/repository/Room -> controlled real fake API. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ChatSendFlowTest {
    private val fixture = ChatUiFixture()
    private val ui = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(fixture).around(ui)

    @Before fun startChat() {
        assertEquals(0, fixture.remote.historyCalls.get())
        ui.onNodeWithText("Start app").performClick()
    }

    @Test fun lostReplyThenRapidRetryKeepsOneBubbleOneClientIdentityAndOneServerMessage() {
        loadHistory()
        val text = "Lost reply: retry this exact message"
        composer().performTextReplacement(text)
        send().assertIsEnabled().performClick()
        waitForAttempts(1)
        val pending = outgoing(1).single()
        assertNotNull(pending.clientId)
        assertNull(pending.serverId)
        assertEquals(DeliveryStatus.SENDING, pending.deliveryStatus)
        assertBubble(pending, text, "Sending")
        assertDraft("")
        send().assertIsNotEnabled()

        // The actual supplied server commits; only its response is lost.
        fixture.remote.attempts[0].reply.complete(SendReply.COMMIT_THEN_LOSE_REPLY)
        waitForStatus(pending, "Failed · Retry")
        assertEquals(1, fixture.remote.serverCopies(requireNotNull(pending.clientId)))
        val failed = outgoing(1).single()
        assertEquals(pending.localId, failed.localId)
        assertEquals(pending.clientId, failed.clientId)
        assertEquals(DeliveryStatus.FAILED, failed.deliveryStatus)
        ui.onNode(retryIn(pending), useUnmergedTree = true).performTouchInput { doubleClick() }
        waitForAttempts(2)
        assertBubble(pending, text, "Sending")
        ui.waitForIdle()
        assertEquals(2, fixture.remote.attempts.size) // First send plus one coalesced retry.
        assertEquals(listOf(pending.clientId, pending.clientId), fixture.remote.attempts.map { it.clientId })
        assertEquals(listOf(text, text), fixture.remote.attempts.map { it.text })

        fixture.remote.attempts[1].reply.complete(SendReply.ACKNOWLEDGE)
        assertBubble(pending, text, "Sent")
        ui.onNode(retryIn(pending), useUnmergedTree = true).assertDoesNotExist()
        ui.onNode(statusIn(pending, "Sending"), useUnmergedTree = true).assertDoesNotExist()
        ui.onAllNodesWithText(text, useUnmergedTree = true).assertCountEquals(1)
        val sent = outgoing(1).single()
        assertEquals(pending.localId, sent.localId)
        assertEquals(pending.clientId, sent.clientId)
        assertEquals(DeliveryStatus.SENT, sent.deliveryStatus)
        assertEquals(1, fixture.remote.serverCopies(requireNotNull(pending.clientId)))
    }

    @Test fun blankInputIsDisabledAndTwoIntentionalIdenticalSendsCanCompleteOutOfOrder() {
        loadHistory()
        send().assertIsNotEnabled()
        composer().performTextReplacement("  \n  ")
        send().assertIsNotEnabled()
        assertEquals(0, fixture.remote.attempts.size)
        assertEquals(emptyList<ChatMessage>(), fixture.outgoing())

        val text = "Same text, two deliberate sends"
        composer().performTextReplacement(text)
        send().assertIsEnabled().performClick()
        waitForAttempts(1)
        val first = outgoing(1).single()
        assertBubble(first, text, "Sending")
        assertDraft("")
        composer().performTextReplacement(text)
        send().assertIsEnabled().performClick()
        waitForAttempts(2)
        val second = outgoing(2).single { it.localId != first.localId }
        assertNotEquals(first.clientId, second.clientId)
        assertBubble(second, text, "Sending")
        ui.onAllNodesWithText(text, useUnmergedTree = true).assertCountEquals(2)

        fixture.remote.attempts[1].reply.complete(SendReply.ACKNOWLEDGE)
        assertBubble(second, text, "Sent")
        assertBubble(first, text, "Sending")
        fixture.remote.attempts[0].reply.complete(SendReply.ACKNOWLEDGE)
        assertBubble(first, text, "Sent")
        assertBubble(second, text, "Sent")
        assertDraft("")
        assertEquals(2, fixture.remote.attempts.size)
        val rows = outgoing(2)
        assertEquals(setOf(first.localId, second.localId), rows.map { it.localId }.toSet())
        assertEquals(listOf(DeliveryStatus.SENT, DeliveryStatus.SENT), rows.map { it.deliveryStatus })
        assertEquals(1, fixture.remote.serverCopies(requireNotNull(first.clientId)))
        assertEquals(1, fixture.remote.serverCopies(requireNotNull(second.clientId)))
    }

    @Test fun activityRecreationRetainsDraftAndPendingSendWithoutRepeatingHistoryOrSend() {
        // Hold both real requests open across Activity recreation, rather than relying on timing.
        ui.waitUntil(10_000) { fixture.remote.historyCalls.get() == 1 }
        composer().performTextReplacement("Draft before recreation")
        assertDraft("Draft before recreation")
        ui.activityRule.scenario.recreate()
        assertDraft("Draft before recreation")
        send().performClick()
        waitForAttempts(1)
        val pending = outgoing(1).single()
        assertBubble(pending, pending.text, "Sending")
        composer().performTextReplacement("Do not clear this newer draft")
        assertDraft("Do not clear this newer draft")
        ui.activityRule.scenario.recreate()
        assertDraft("Do not clear this newer draft")
        assertBubble(pending, pending.text, "Sending")
        assertEquals(1, fixture.remote.historyCalls.get())
        assertEquals(1, fixture.remote.attempts.size)

        fixture.remote.historyReply.complete(true)
        waitForNode(hasText("پیام شماره 119"))
        fixture.remote.attempts[0].reply.complete(SendReply.ACKNOWLEDGE)
        assertBubble(pending, pending.text, "Sent")
        assertDraft("Do not clear this newer draft")
        assertEquals(1, fixture.remote.historyCalls.get())
        assertEquals(1, fixture.remote.attempts.size)
        assertEquals(pending.localId, outgoing(1).single().localId)
    }

    @Test fun historyFailureDoesNotBlockSendAndHistoryRetryMergesTheConfirmedBubble() {
        fixture.remote.historyReply.complete(false)
        waitForNode(hasText("Couldn’t load the conversation. Saved messages are still here."))
        val text = "Send despite a history failure"
        composer().performTextReplacement(text)
        send().performClick()
        waitForAttempts(1)
        val pending = outgoing(1).single()
        assertBubble(pending, text, "Sending")
        fixture.remote.attempts[0].reply.complete(SendReply.ACKNOWLEDGE)
        assertBubble(pending, text, "Sent")
        composer().performTextReplacement("Keep while history retries")
        ui.onNodeWithText("Try again").performClick()
        waitForNode(hasText("پیام شماره 119"))
        assertBubble(pending, text, "Sent")
        assertDraft("Keep while history retries")
        ui.onAllNodesWithText(text, useUnmergedTree = true).assertCountEquals(1)
        assertEquals(2, fixture.remote.historyCalls.get())
        assertEquals(1, fixture.remote.attempts.size)
        assertEquals(pending.localId, outgoing(1).single().localId)
        assertEquals(1, fixture.remote.serverCopies(requireNotNull(pending.clientId)))
    }

    private fun composer() = ui.onNodeWithContentDescription("Message the team")
    private fun send() = ui.onNodeWithContentDescription("Send message")

    private fun assertDraft(text: String) {
        // TextField also exposes placeholder text. Assert its actual editable value, and wait
        // for lifecycle-aware collection to reconnect after Activity recreation.
        val editableValue = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))
        waitForNode(hasContentDescription("Message the team") and editableValue)
        composer().assert(editableValue)
    }
    private fun tag(message: ChatMessage) = "chat-message-${message.localId}"
    private fun statusIn(message: ChatMessage, status: String) = hasText(status) and hasAnyAncestor(hasTestTag(tag(message)))
    private fun retryIn(message: ChatMessage) = hasContentDescription("Retry message") and hasAnyAncestor(hasTestTag(tag(message)))

    private fun loadHistory() {
        fixture.remote.historyReply.complete(true)
        waitForNode(hasText("پیام شماره 119"))
    }

    private fun waitForAttempts(count: Int) {
        ui.waitUntil(10_000) { fixture.remote.attempts.size >= count }
        assertEquals(count, fixture.remote.attempts.size)
    }

    private fun outgoing(count: Int): List<ChatMessage> {
        ui.waitUntil(10_000) { fixture.outgoing().size == count }
        return fixture.outgoing()
    }

    private fun waitForNode(matcher: SemanticsMatcher) {
        ui.waitUntil(10_000) { ui.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
    }

    private fun waitForStatus(message: ChatMessage, status: String) = waitForNode(statusIn(message, status))

    private fun assertBubble(message: ChatMessage, text: String, status: String) {
        waitForStatus(message, status)
        ui.waitForIdle() // Settle delivery/placement transitions before checking disappearance/counts.
        ui.onAllNodesWithTag(tag(message)).assertCountEquals(1)
        ui.onNodeWithTag(tag(message)).assertIsDisplayed()
        ui.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag(message))), useUnmergedTree = true).assertIsDisplayed()
        ui.onNode(statusIn(message, status), useUnmergedTree = true).assertIsDisplayed()
    }
}
