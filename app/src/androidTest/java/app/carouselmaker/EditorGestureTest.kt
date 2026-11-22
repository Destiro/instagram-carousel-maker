package app.carouselmaker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.carouselmaker.data.*
import app.carouselmaker.ui.editor.newTextItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class EditorGestureTest : UiTestBase() {
    private val density get() = TestSupport.context.resources.displayMetrics.density

    private fun it0(): Item = photos().first()

    @Test fun opensForEverySlideCountAndRatio() {
        for ((slides, ratio) in listOf(1 to Ratio.R1_1, 5 to Ratio.R4_5, 20 to Ratio.R3_4)) {
            val p = TestSupport.seed(slides, slides, ratio, name = "Open$slides")
            openEditor(p)
            assertEquals(slides, proj.slideCount)
            for (i in 0 until slides) scrollTo(i) // labels and strip must survive any scroll position
            scenario?.close(); scenario = null
        }
    }

    @Test fun tapSelectsAndTapOnBlankDeselects() {
        openEditor(TestSupport.seed(3))
        val item = it0()
        select(item)
        assertSelected(item.id)
        rule.waitUntil(5_000) { hasText("Photo") }
        tapCanvas(Offset(300f, 12f)) // above the slides = blank
        assertNull(vm.selectedId)
        select(item)
        click("Done")
        assertNull(vm.selectedId)
    }

    @Test fun dragMovesItemAndDetachesFromLayoutThenUndoRestores() {
        openEditor(TestSupport.seed(3))
        val item = it0()
        select(item)
        val before = frame(item)
        val start = screenOf(before.center)
        dragCanvas(start, start + Offset(150f, 90f))
        val after = proj.items.first { it.id == item.id }
        assertNull("moving a cell photo frees it", after.blockId)
        assertTrue("moved right (was ${before.left}, now ${after.x})", after.x > before.left + 100f)
        assertTrue(after.y > before.top)
        assertEquals(before.width, after.w, 1f)
        clickDesc("Undo")
        val undone = proj.items.first { it.id == item.id }
        assertNotNull("undo puts it back in its cell", undone.blockId)
        clickDesc("Redo")
        assertNull(proj.items.first { it.id == item.id }.blockId)
    }

    @Test fun cornerHandleResizesKeepingAspect() {
        openEditor(TestSupport.seed(3))
        val item = it0()
        select(item)
        val fr = frame(item)
        val handle = screenOf(Offset(fr.right, fr.bottom))
        dragCanvas(handle, handle + Offset(-120f, -150f))
        val a = proj.items.first { it.id == item.id }
        assertNull(a.blockId)
        assertTrue("shrunk: ${a.w} < ${fr.width}", a.w < fr.width - 20f)
        assertEquals(fr.width / fr.height, a.w / a.h, 0.02f)
        // the opposite (top-left) corner stays put
        assertEquals(fr.left, a.x, 2f)
        assertEquals(fr.top, a.y, 2f)
    }

    @Test fun sideHandleStretchesOneAxis() {
        openEditor(TestSupport.seed(3))
        val item = it0()
        select(item)
        val fr = frame(item)
        val handle = screenOf(Offset(fr.right, fr.center.y))
        dragCanvas(handle, handle + Offset(-100f, 0f))
        val a = proj.items.first { it.id == item.id }
        assertTrue(a.w < fr.width - 20f)
        assertEquals(fr.height, a.h, 1f)
    }

    @Test fun rotateHandleRotates() {
        openEditor(TestSupport.seed(3))
        val item = it0()
        select(item)
        val fr = frame(item)
        val handle = screenOf(Offset(fr.center.x, fr.top)) + Offset(0f, -34f * density)
        val target = screenOf(fr.center) + Offset(300f, 0f)
        dragCanvas(handle, target)
        val a = proj.items.first { it.id == item.id }
        assertTrue("rotation ${a.rotation}", abs(a.rotation - 90f) < 5f)
    }

    @Test fun pinchOnSelectedItemScalesIt() {
        openEditor(TestSupport.seed(3))
        val item = it0()
        select(item)
        val fr = frame(item)
        val c = screenOf(fr.center)
        rule.onNodeWithTag("editor_canvas").performTouchInput {
            pinch(c + Offset(-60f, 0f), c + Offset(-150f, 0f), c + Offset(60f, 0f), c + Offset(150f, 0f))
        }
        rule.waitForIdle()
        val a = proj.items.first { it.id == item.id }
        assertTrue("scaled up: ${a.w} vs ${fr.width}", a.w > fr.width * 1.5f)
        assertNull(a.blockId)
    }

    @Test fun dragOnBlankPansTheCanvas() {
        openEditor(TestSupport.seed(3))
        assertNull(vm.selectedId)
        val before = vm.offset
        dragCanvas(Offset(300f, 12f), Offset(150f, 50f))
        assertNotEquals(before, vm.offset)
        assertEquals(before.x - 150f, vm.offset.x, 8f)
        assertNull("panning does not select anything", vm.selectedId)
    }

    @Test fun pinchOnBlankZoomsTheCanvas() {
        openEditor(TestSupport.seed(3))
        val before = vm.scale
        rule.onNodeWithTag("editor_canvas").performTouchInput {
            pinch(Offset(400f, 12f), Offset(300f, 12f), Offset(500f, 12f), Offset(700f, 12f))
        }
        rule.waitForIdle()
        assertTrue("zoomed in: ${vm.scale} vs $before", vm.scale > before * 1.3f)
    }

    @Test fun extremeZoomLevelsStillRender() {
        openEditor(TestSupport.seed(5))
        for (s in listOf(3f, 0.05f, 1f)) {
            rule.runOnUiThread { vm.scale = s.coerceIn(vm.minScale(), vm.maxScale()) }
            rule.waitForIdle()
            Thread.sleep(400)
            rule.waitForIdle()
        }
        assertTrue(rule.onAllNodesCount("editor_canvas") > 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.SemanticsMatcher("t") {
            it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) == tag
        }).fetchSemanticsNodes().size

    @Test fun deleteEmptiesACellButKeepsTheLayout() {
        openEditor(TestSupport.seed(3))
        val item = it0()
        select(item)
        click("Delete")
        val a = proj.items.first { it.id == item.id }
        assertNull(a.media)
        assertNotNull(a.blockId)
        assertEquals(3, proj.items.size)
    }

    @Test fun textCornerHandleScalesFont() {
        openEditor(TestSupport.seed(2))
        val t = newTextItem()
        rule.runOnUiThread { vm.addAtCenter(t) }
        rule.waitForIdle()
        val id = vm.selectedId!!
        val fr = frame(vm.item(id)!!)
        val handle = screenOf(Offset(fr.right, fr.bottom))
        dragCanvas(handle, handle + Offset(90f, 90f))
        val a = vm.item(id)!!
        assertTrue("font grew ${a.fontSize} vs ${t.fontSize}", a.fontSize > t.fontSize * 1.1f)
    }

    @Test fun textSideHandleChangesWidthOnly() {
        openEditor(TestSupport.seed(2))
        rule.runOnUiThread { vm.addAtCenter(newTextItem()) }
        rule.waitForIdle()
        val id = vm.selectedId!!
        val before = vm.item(id)!!
        val fr = frame(before)
        val handle = screenOf(Offset(fr.right, fr.center.y))
        dragCanvas(handle, handle + Offset(-80f, 0f))
        val a = vm.item(id)!!
        assertTrue(a.w < before.w)
        assertEquals(before.fontSize, a.fontSize, 0.01f)
    }

    @Test fun tapOnEmptyCellOpensThePickerAndBackComesBackSafely() {
        val id = newId()
        val p = createProject("BlankCells", Ratio.R4_5, 3, emptyList(), blank = true, id = id)
        AppGraph.repo.saveNow(p)
        openEditor(p)
        val cell = proj.items.first { proj.slideOf(it) == 0 }
        tapCanvas(screenOf(frame(cell).center))
        device.waitForIdle()
        Thread.sleep(1500)
        device.pressBack()
        waitForEditor(id)
        assertEquals(3, proj.items.count { it.media == null })
        assertFalse(proj.items.any { it.media != null })
    }

    @Test fun mediaButtonOpensPickerAndBackComesBackSafely() {
        val p = TestSupport.seed(2)
        openEditor(p)
        click("Media")
        device.waitForIdle()
        Thread.sleep(1500)
        device.pressBack()
        waitForEditor(p.id)
        assertEquals(2, proj.items.size)
    }

    @Test fun replaceButtonOpensPickerAndBackComesBackSafely() {
        val p = TestSupport.seed(2)
        openEditor(p)
        select(it0())
        click("Replace")
        device.waitForIdle()
        Thread.sleep(1500)
        device.pressBack()
        waitForEditor(p.id)
        assertEquals(2, photos().size)
    }

    @Test fun layerFlipDuplicateAndDeleteOnAFreePhoto() {
        openEditor(TestSupport.seed(2))
        val item = it0()
        select(item)
        click("Flip")
        assertTrue(proj.items.first { it.id == item.id }.flipH)
        click("Back")
        assertEquals(item.id, proj.items.first().id)
        click("Front")
        assertEquals(item.id, proj.items.last().id)
        val n = proj.items.size
        click("Duplicate")
        assertEquals(n + 1, proj.items.size)
        assertNotEquals(item.id, vm.selectedId)
        click("Delete")
        assertEquals(n, proj.items.size)
        assertNull(vm.selectedId)
    }

    @Test fun addSlideButtonAndStripNavigation() {
        openEditor(TestSupport.seed(3))
        clickDesc("Add slide")
        assertEquals(4, proj.slideCount)
        scrollTo(3)
        assertEquals(3, vm.currentSlide())
    }
}
