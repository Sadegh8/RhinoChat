package dev.sadeghtahani.rhinochat.chat.presentation

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatLayoutTest {
    @Test fun phoneUsesTheAvailableWindow() {
        val layout = layout(393, 820)
        assertEquals(Rect(0f, 0f, 393f, 820f), layout.canvasBoundsPx)
        assertFalse(layout.compactHeader)
        assertFalse(layout.floatingCanvas)
        assertEquals(5, layout.composerMaxLines)
    }

    @Test fun landscapePreservesHeightAndUsesACompactHeaderAndComposer() {
        val layout = layout(840, 393)
        assertEquals(Rect(16f, 0f, 824f, 393f), layout.canvasBoundsPx)
        assertTrue(layout.compactHeader)
        assertFalse(layout.floatingCanvas)
        assertEquals(2, layout.composerMaxLines)
    }

    @Test fun veryShortKeyboardWindowPrioritizesMessagesAndInputOverTheHeader() {
        val layout = layout(800, 180)
        assertFalse(layout.showHeader)
        assertEquals(2, layout.composerMaxLines)
        assertFalse(layout(800, 239).showHeader)
        assertTrue(layout(800, 240).showHeader)
    }

    @Test fun largeTabletCentersAReadableCanvas() {
        val layout = layout(1280, 800)
        assertEquals(Rect(220f, 24f, 1060f, 776f), layout.canvasBoundsPx)
        assertTrue(layout.floatingCanvas)
    }

    @Test fun breakpointsUseAvailableWidthAndHeightIndependently() {
        assertFalse(layout(599, 800).floatingCanvas)
        assertTrue(layout(600, 800).floatingCanvas)
        assertTrue(layout(600, 479).compactHeader)
        assertFalse(layout(600, 480).compactHeader)
    }

    @Test fun densityDoesNotChangeTheLogicalLayout() {
        val layout = calculateChatLayout(IntSize(2560, 1600), 2f, 1f, Posture(), Offset.Zero, LayoutDirection.Ltr)
        assertEquals(Rect(440f, 48f, 2120f, 1552f), layout.canvasBoundsPx)
    }

    @Test fun separatingBookHingeKeepsControlsInOnePaneInEitherDirection() {
        val fold = verticalHinge(414f, 426f)
        assertEquals(Rect(0f, 0f, 406f, 900f), layout(840, 900, listOf(fold)).canvasBoundsPx)
        assertEquals(Rect(434f, 0f, 840f, 900f), layout(840, 900, listOf(fold), LayoutDirection.Rtl).canvasBoundsPx)
    }

    @Test fun zeroWidthSeparatingFoldStillReservesAGutter() {
        val layout = layout(840, 900, listOf(verticalHinge(420f, 420f)))
        assertEquals(412f, layout.canvasBoundsPx.right)
    }

    @Test fun occludingFlatHingeIsAvoidedButAFlatNonSeparatingCreaseCanBeSpanned() {
        val bounds = Rect(414f, 0f, 426f, 900f)
        val occluding = HingeInfo(bounds, isFlat = true, isVertical = true, isSeparating = false, isOccluding = true)
        val crease = HingeInfo(bounds, isFlat = true, isVertical = true, isSeparating = false, isOccluding = false)
        assertTrue(layout(840, 900, listOf(occluding)).canvasBoundsPx.right < bounds.left)
        assertEquals(Rect(24f, 24f, 816f, 876f), layout(840, 900, listOf(crease)).canvasBoundsPx)
    }

    @Test fun tabletopKeepsHistoryAboveAndInputBelowTheHinge() {
        val layout = layout(840, 900, listOf(horizontalHinge(444f, 456f)))
        assertEquals(436f, layout.canvasBoundsPx.top + requireNotNull(layout.historyBottomPx))
        assertEquals(464f, layout.canvasBoundsPx.bottom - requireNotNull(layout.inputMaxHeightPx))
    }

    @Test fun keyboardCoveringLowerPaneMovesTheWholeChatAboveTheFold() {
        val layout = layout(840, 540, listOf(horizontalHinge(444f, 456f)))
        assertNull(layout.historyBottomPx)
        assertNull(layout.inputMaxHeightPx)
        assertTrue(layout.compactHeader)
        assertEquals(436f, layout.canvasBoundsPx.bottom)
    }

    @Test fun hingeWindowCoordinatesAreTranslatedAfterSafeInsets() {
        val hinge = HingeInfo(Rect(434f, 40f, 446f, 940f), false, true, true, true)
        val layout = calculateChatLayout(IntSize(840, 900), 1f, 1f, Posture(hingeList = listOf(hinge)), Offset(20f, 40f), LayoutDirection.Ltr)
        assertEquals(406f, layout.canvasBoundsPx.right)
    }

    @Test fun multipleHingesDoNotLeaveTheChosenPaneCrossingAnotherHinge() {
        val layout = layout(900, 900, listOf(verticalHinge(300f, 300f), verticalHinge(600f, 600f)))
        assertEquals(Rect(0f, 0f, 292f, 900f), layout.canvasBoundsPx)
        val horizontal = layout(1000, 1000, listOf(horizontalHinge(300f, 320f), horizontalHinge(600f, 620f)))
        assertEquals(628f, horizontal.canvasBoundsPx.top)
    }

    @Test fun largeTextRestrictsComposerLinesToTheLowerPaneBudget() {
        val fold = horizontalHinge(400f, 412f)
        val layout = calculateChatLayout(IntSize(840, 620), 1f, 2f, Posture(hingeList = listOf(fold)), Offset.Zero, LayoutDirection.Ltr)
        val composerHeight = layout.composerMaxLines * 48f + 32f + 24f
        assertTrue(composerHeight <= requireNotNull(layout.inputMaxHeightPx))
    }

    private fun layout(width: Int, height: Int, hinges: List<HingeInfo> = emptyList(), direction: LayoutDirection = LayoutDirection.Ltr) =
        calculateChatLayout(IntSize(width, height), 1f, 1f, Posture(hingeList = hinges), Offset.Zero, direction)

    private fun verticalHinge(left: Float, right: Float) =
        HingeInfo(Rect(left, 0f, right, 900f), false, true, true, right > left)

    private fun horizontalHinge(top: Float, bottom: Float) =
        HingeInfo(Rect(0f, top, 1000f, bottom), false, false, true, bottom > top)
}
