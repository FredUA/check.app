package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rows of 60 px in a list, each a clickable card inside a [SwipeToDeleteBox], on Robolectric's default
 * 320 x 470 px screen at density 1 (so dp = px). Gestures are sent to the list, as a finger would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SwipeToDeleteBoxTest {

    @get:Rule
    val rule = createComposeRule()

    private val deleted = mutableListOf<Int>()
    private val clicked = mutableListOf<Int>()
    private lateinit var listState: LazyListState

    private fun showList() {
        rule.setContent {
            val rows = remember { mutableStateListOf<Int>().apply { addAll(0 until 30) } }
            val state = rememberLazyListState()
            listState = state
            // As on the home screen: rows get the item animation only while a swipe is under way, so the
            // row's modifier changes in the middle of the gesture.
            var swipeActive by remember { mutableStateOf(false) }
            LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("list")) {
                items(rows, key = { it }) { row ->
                    SwipeToDeleteBox(
                        onDelete = {
                            deleted += row
                            rows.remove(row)
                        },
                        onSwipeActiveChanged = { swipeActive = it },
                        modifier = if (swipeActive) {
                            Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                        } else {
                            Modifier
                        }
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .clickable { clicked += row }
                                .testTag("row_$row")
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    /** A finger moving over the list, in the list's own coordinates. Row 2 is at y = 120..180. */
    private fun drag(from: Offset, to: Offset, millis: Long) {
        rule.onNodeWithTag("list").performTouchInput { swipe(from, to, millis) }
        rule.waitForIdle()
    }

    private fun cardX(row: Int): Float = rule.onNodeWithTag("row_$row").fetchSemanticsNode().positionInRoot.x

    private fun listScrolled(): Boolean =
        listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0

    private fun waitOutUndoWindow() {
        rule.mainClock.advanceTimeBy(6_000)
        rule.waitForIdle()
    }

    @Test
    fun `quick short flick to the right deletes the task after the undo window`() {
        showList()
        // 120 px (well under 40% of the width) in 100 ms.
        drag(Offset(60f, 150f), Offset(180f, 158f), 100)

        assertTrue("card should be off screen, is at ${cardX(2)}", cardX(2) >= 320f)
        rule.onNodeWithText("Undo").assertExists()
        assertEquals(emptyList<Int>(), deleted)
        assertTrue(!listScrolled())

        waitOutUndoWindow()
        assertEquals(listOf(2), deleted)
        assertEquals(emptyList<Int>(), clicked)
    }

    @Test
    fun `quick short flick to the left deletes the task`() {
        showList()
        drag(Offset(260f, 150f), Offset(140f, 142f), 100)

        assertTrue("card should be off screen, is at ${cardX(2)}", cardX(2) <= -320f)
        waitOutUndoWindow()
        assertEquals(listOf(2), deleted)
    }

    @Test
    fun `slow drag over 40 percent of the width deletes the task`() {
        showList()
        // 160 px = half the width, over two seconds: far, but not a flick.
        drag(Offset(40f, 150f), Offset(200f, 150f), 2_000)

        assertTrue("card should be off screen, is at ${cardX(2)}", cardX(2) >= 320f)
        waitOutUndoWindow()
        assertEquals(listOf(2), deleted)
    }

    @Test
    fun `slow short drag springs back`() {
        showList()
        // 80 px = a quarter of the width, slowly.
        drag(Offset(40f, 150f), Offset(120f, 150f), 2_000)

        assertEquals(0f, cardX(2), 0.5f)
        waitOutUndoWindow()
        assertEquals(emptyList<Int>(), deleted)
        rule.onAllNodesWithText("Delete").assertCountEquals(0)
        rule.onAllNodesWithText("Undo").assertCountEquals(0)
    }

    @Test
    fun `quick nudge shorter than the minimum distance springs back`() {
        showList()
        drag(Offset(60f, 150f), Offset(90f, 150f), 30)

        assertEquals(0f, cardX(2), 0.5f)
        waitOutUndoWindow()
        assertEquals(emptyList<Int>(), deleted)
    }

    @Test
    fun `steep one-handed scroll scrolls the list and moves no card`() {
        showList()
        // 60 px sideways while going 250 px up: about 76 degrees from the horizontal.
        drag(Offset(160f, 400f), Offset(100f, 150f), 200)

        assertTrue("list should have scrolled", listScrolled())
        waitOutUndoWindow()
        assertEquals(emptyList<Int>(), deleted)
        assertEquals(emptyList<Int>(), clicked)
    }

    @Test
    fun `fast diagonal at 40 degrees is a scroll, not a swipe`() {
        showList()
        // 150 px sideways, 126 px up, fast: over the 30 degree limit, so the list takes it.
        drag(Offset(250f, 400f), Offset(100f, 274f), 100)

        assertTrue("list should have scrolled", listScrolled())
        waitOutUndoWindow()
        assertEquals(emptyList<Int>(), deleted)
    }

    @Test
    fun `fast diagonal at 20 degrees is a swipe, and the list stays put`() {
        showList()
        // 150 px sideways, 55 px up.
        drag(Offset(60f, 170f), Offset(210f, 115f), 100)

        assertTrue(!listScrolled())
        waitOutUndoWindow()
        assertEquals(listOf(2), deleted)
    }

    @Test
    fun `undo brings the card back and nothing is deleted`() {
        showList()
        drag(Offset(60f, 150f), Offset(180f, 158f), 100)
        assertTrue(cardX(2) >= 320f)

        rule.onNodeWithTag("undo_delete_strip").performClick()
        rule.waitForIdle()

        assertEquals(0f, cardX(2), 0.5f)
        waitOutUndoWindow()
        assertEquals(emptyList<Int>(), deleted)
        rule.onAllNodesWithText("Undo").assertCountEquals(0)

        // The row works as before afterwards.
        drag(Offset(60f, 150f), Offset(180f, 158f), 100)
        waitOutUndoWindow()
        assertEquals(listOf(2), deleted)
    }

    @Test
    fun `tap still reaches the card`() {
        showList()
        rule.onNodeWithTag("row_1").performClick()
        rule.waitForIdle()

        assertEquals(listOf(1), clicked)
        waitOutUndoWindow()
        assertEquals(emptyList<Int>(), deleted)
    }
}
