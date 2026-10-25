package dev.sadeghtahani.rhinochat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dev.sadeghtahani.rhinochat.chat.data.DeliveryStatus
import dev.sadeghtahani.rhinochat.testing.ChatUiFixture
import dev.sadeghtahani.rhinochat.testing.SendReply
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class DemoControlsFlowTest {
    private val fixture = ChatUiFixture()
    private val ui = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(ui)

    @Test fun completedCachedHistoryShowsBeginningWithoutLoadingCachedPagesAgain() {
        fixture.remote.historyReply.complete(true)
        runBlocking {
            val allHistory = fixture.remote.getMessages(ChatUiFixture.CONVERSATION, null, 120)
            assertNull(allHistory.nextCursor)
            assertEquals(120, allHistory.messages.size)
            fixture.store.savePage(ChatUiFixture.CONVERSATION, allHistory)
        }
        ui.onNodeWithText("Start app").performClick()
        waitForText("پیام شماره 119")
        ui.waitForIdle()
        ui.onNodeWithTag("chat-history-list").performScrollToIndex(120)
        waitForText("You’re at the beginning")
        ui.onNodeWithText("You’re at the beginning").assertIsDisplayed()
        ui.onNodeWithText("Loading earlier messages…").assertDoesNotExist()
        // One setup fetch, one fresh head; no older request, delay, or failure opportunity.
        assertEquals(2, fixture.remote.historyCalls.get())
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("Reset cache").performScrollTo().performClick()
        waitForText("History cache cleared. Start app to load it again.")
        runBlocking {
            assertEquals(emptyList<dev.sadeghtahani.rhinochat.chat.data.ChatMessage>(), fixture.store.observeMessages(ChatUiFixture.CONVERSATION).first())
            assertEquals(false, fixture.store.observePagination(ChatUiFixture.CONVERSATION).first().hasLoadedInitialPage)
        }
        assertEquals(2, fixture.remote.historyCalls.get())
        ui.onNodeWithText("Start app").performClick()
        waitForText("پیام شماره 119")
        assertEquals(3, fixture.remote.historyCalls.get())
        runBlocking {
            assertEquals(20, fixture.store.observeMessages(ChatUiFixture.CONVERSATION).first().size)
            assertEquals("20", fixture.store.observePagination(ChatUiFixture.CONVERSATION).first().nextCursor)
        }
    }

    @Test fun offlineLaunchDoesNotLoadUntilStartAndSwitchingItOffLetsSameMessageRetry() {
        ui.onNodeWithText("Test controls").assertIsDisplayed()
        assertEquals(0, fixture.remote.historyCalls.get())
        ui.onNodeWithText("Go offline").performClick()
        ui.onNodeWithText("Start app").performClick()
        waitForText("Couldn’t load the conversation. Saved messages are still here.")
        ui.onNodeWithText("You’re offline").assertIsDisplayed()
        assertEquals(0, fixture.remote.historyCalls.get())
        ui.onNodeWithContentDescription("Message the team").performTextReplacement("Offline control test")
        ui.onNodeWithContentDescription("Send message").performClick()
        waitForText("Failed · Retry")
        val failed = fixture.outgoing().single()
        assertEquals(DeliveryStatus.FAILED, failed.deliveryStatus)
        assertEquals(0, fixture.remote.attempts.size)

        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("Go offline").performClick()
        ui.onNodeWithText("Start app").performClick()
        ui.onNodeWithText("You’re offline").assertDoesNotExist()
        ui.onNodeWithContentDescription("Retry message").performClick()
        ui.waitUntil(10_000) { fixture.remote.attempts.size == 1 }
        fixture.remote.attempts.single().reply.complete(SendReply.ACKNOWLEDGE)
        waitForText("Sent")
        val sent = fixture.outgoing().single()
        assertEquals(failed.localId, sent.localId)
        assertEquals(failed.clientId, sent.clientId)
        assertEquals(DeliveryStatus.SENT, sent.deliveryStatus)
        fixture.remote.historyReply.complete(true)
        // Start app retries an unsuccessful head load when returning from controls.
        waitForText("پیام شماره 119")
        assertEquals(1, fixture.remote.historyCalls.get())
    }

    private fun waitForText(text: String) {
        ui.waitUntil(10_000) { ui.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
    }
}
