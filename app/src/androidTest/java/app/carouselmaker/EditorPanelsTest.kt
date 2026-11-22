package app.carouselmaker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.carouselmaker.data.*
import app.carouselmaker.render.PaperKinds
import app.carouselmaker.render.TapeStyles
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
class EditorPanelsTest : UiTestBase() {

    private val lib get() = AppGraph.extras!!.stickers.library

    private fun toggle() = rule.onNode(isToggleable()).performClick().also { rule.waitForIdle() }

    /** Leave any selection so the main tool bar is showing. */
    private fun mainBar() {
        if (vm.selectedId != null) click("Done")
        rule.waitUntil(5_000) { hasText("Stickers") }
    }

    // ------------------------------------------------------------ layouts
    @Test fun layoutsPanelAppliesEveryKindOfTemplateWithoutLosingPhotos() {
        openEditor(TestSupport.seed(3))
        click("Layout")
        waitText("Layouts")
        clickTag("template_grid4")
        val b0 = proj.blocks.first { it.start == 0 }
        assertEquals("grid4", b0.templateId)
        assertEquals(4, proj.items.count { it.blockId == b0.id })
        assertEquals(3, proj.items.count { it.media != null })

        click("2-slide span")
        clickTag("template_pano2")
        assertTrue(proj.blocks.any { it.span == 2 && it.start == 0 && it.templateId == "pano2" })
        assertEquals("no photo is ever lost", 3, proj.items.count { it.media != null })

        click("3-slide span")
        clickTag("template_wall3")
        assertTrue(proj.blocks.any { it.span == 3 && it.templateId == "wall3" })
        assertEquals(3, proj.items.count { it.media != null })

        click("1 slide")
        click("Every slide")
        clickTag("template_side2")
        assertEquals(3, proj.blocks.count { it.templateId == "side2" })
        assertEquals(3, proj.items.count { it.media != null })

        // every single template renders and applies
        for (t in Templates.all) {
            click(when (t.span) { 1 -> "1 slide"; 2 -> "2-slide span"; else -> "3-slide span" })
            clickTag("template_${t.id}")
            assertTrue("template ${t.id}", proj.blocks.any { it.templateId == t.id })
            assertEquals(3, proj.items.count { it.media != null })
        }
        click("Done")
        waitText("Layout")
    }

    @Test fun layoutsRepeatAppliesAcrossSlidePairs() {
        openEditor(TestSupport.seed(4))
        click("Layout")
        click("2-slide span")
        click("Repeat")
        clickTag("template_bridge2")
        assertEquals(2, proj.blocks.count { it.templateId == "bridge2" })
        assertEquals(4, proj.items.count { it.media != null })
    }

    @Test fun layoutOnBlankCanvasCreatesEmptyCells() {
        val id = newId()
        val p = createProject("Blank", Ratio.R1_1, 2, emptyList(), blank = true, id = id)
        AppGraph.repo.saveNow(p)
        openEditor(p)
        click("Layout")
        clickTag("template_grid4")
        assertEquals(4, proj.items.count { it.blockId != null && proj.block(it.blockId)?.start == 0 })
        assertTrue(proj.items.none { it.media != null })
    }

    // ------------------------------------------------------------ spacing & backgrounds
    @Test fun spacingSlidersChangeEveryLayoutLive() {
        openEditor(TestSupport.seed(3))
        click("Spacing")
        waitText("Outer margin")
        assertEquals(3, sliderCount())
        setSlider(0, 100f)
        assertTrue(proj.blocks.all { abs(it.margin - 100f) < 1f })
        setSlider(1, 40f)
        assertTrue(proj.blocks.all { abs(it.gap - 40f) < 1f })
        setSlider(2, 50f)
        assertTrue(proj.blocks.all { abs(it.radius - 50f) < 1f })
        val first = photos().first { proj.block(it.blockId)?.start == 0 }
        assertEquals("first cell starts at the outer margin", 100f, proj.rectOf(first).left, 2f)
        toggle()
        assertTrue(proj.blocks.none { it.seamGap })
        toggle()
        assertTrue(proj.blocks.all { it.seamGap })
        click("Done")
    }

    @Test fun backgroundColoursApplyToAllOrJustThisSlide() {
        openEditor(TestSupport.seed(3))
        click("Spacing")
        click("Background")
        val sky = 0xFF9CC3E6.toInt()
        val pink = 0xFFF28B9B.toInt()
        clickTag("bg_$sky")
        assertTrue(proj.slides.all { it.background == sky })
        click("This slide")
        clickTag("bg_$pink")
        assertEquals(pink, proj.slides[0].background)
        assertEquals(sky, proj.slides[1].background)
        click("Done")
    }

    @Test fun spacingOnProjectWithoutLayoutsShowsAHintInsteadOfCrashing() {
        val p = createProject("NoLayout", Ratio.R4_5, 2, emptyList(), blank = false, id = newId())
        AppGraph.repo.saveNow(p)
        openEditor(p)
        click("Spacing")
        waitText("Add a layout first. Spacing applies to layout cells.")
    }

    // ------------------------------------------------------------ stickers
    private fun openStickers() {
        mainBar()
        click("Stickers")
        waitText("Add to canvas")
    }

    @Test fun addingAStickerSelectsItAndTheToolsWork() {
        openEditor(TestSupport.seed(3))
        openStickers()
        val london = lib.all.first { it.name.startsWith("London") }
        clickTag("sticker_${london.id}")
        val s = proj.items.last()
        assertEquals(ItemType.STICKER, s.type)
        assertEquals(london.id, s.stickerId)
        assertEquals(s.id, vm.selectedId)
        waitText("Outline")
        Thread.sleep(600) // let the sketch render asynchronously

        click("Outline"); assertTrue(vm.item(s.id)!!.outline)
        click("Outline"); assertFalse(vm.item(s.id)!!.outline)
        click("Shadow"); assertTrue(vm.item(s.id)!!.shadow)
        assertNull(vm.item(s.id)!!.tint)
        click("Tint"); assertNotNull(vm.item(s.id)!!.tint)
        repeat(6) { click("Tint") }
        assertNull("tint cycles back to none", vm.item(s.id)!!.tint)
        click("Opacity 100%"); assertEquals(0.75f, vm.item(s.id)!!.opacity, 0.01f)
        click("Opacity 75%"); assertEquals(0.5f, vm.item(s.id)!!.opacity, 0.01f)
        click("Opacity 50%"); assertEquals(0.25f, vm.item(s.id)!!.opacity, 0.01f)
        click("Opacity 25%"); assertEquals(1f, vm.item(s.id)!!.opacity, 0.01f)
        click("Flip"); assertTrue(vm.item(s.id)!!.flipH)
        click("Back"); assertEquals(s.id, proj.items.first().id)
        click("Front"); assertEquals(s.id, proj.items.last().id)
        val n = proj.items.size
        click("Duplicate"); assertEquals(n + 1, proj.items.size)
        click("Delete"); assertEquals(n, proj.items.size)
        assertNull(vm.selectedId)
    }

    @Test fun stickerSearchPackFiltersAndLookSwitch() {
        openEditor(TestSupport.seed(2))
        openStickers()
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("york")
        rule.waitForIdle()
        val york = lib.all.first { it.name.startsWith("York") }
        val london = lib.all.first { it.name.startsWith("London") }
        clickTagExists("sticker_${york.id}")
        assertFalse(tagExists("sticker_${london.id}"))
        rule.onNode(hasSetTextAction()).performTextReplacement("")
        rule.waitForIdle()
        for (pack in lib.packs) click(pack.name.substringBefore(" ·"))
        click("All")
        click("Pencil"); assertEquals(StickerLook.PENCIL, proj.look)
        click("Ink"); assertEquals(StickerLook.INK, proj.look)
        click("Wash"); assertEquals(StickerLook.WASH, proj.look)
        assertTrue(hasText("Import PNG"))
        click("Done")
    }

    private fun tagExists(tag: String) = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("t") {
        it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) == tag
    }).fetchSemanticsNodes().isNotEmpty()

    private fun clickTagExists(tag: String) {
        rule.waitUntil(8_000) { tagExists(tag) }
    }

    @Test fun everyStickerCanBeAddedToTheCanvas() {
        openEditor(TestSupport.seed(2))
        for (def in lib.all) {
            openStickers()
            clickTag("sticker_${def.id}")
            assertEquals(def.id, proj.items.last().stickerId)
        }
        Thread.sleep(800)
        assertEquals(48, proj.items.count { it.type == ItemType.STICKER })
        // scroll through the crowded canvas to force renders at every position
        scrollTo(1); scrollTo(0)
    }

    @Test fun everyTapeAndPaperStyleCanBeAdded() {
        openEditor(TestSupport.seed(2))
        for (t in TapeStyles.all) {
            openStickers(); click("Tape")
            clickTag("tape_${t.id}")
            assertEquals(t.id, proj.items.last().style)
            assertEquals(ItemType.TAPE, proj.items.last().type)
        }
        // selected tape: change style, opacity
        click("Style")
        click("Opacity 100%")
        for (k in PaperKinds.all) {
            openStickers(); click("Paper")
            clickTag("paper_${k.id}")
            assertEquals(k.id, proj.items.last().style)
            assertEquals(ItemType.PAPER, proj.items.last().type)
        }
        Thread.sleep(500)
        assertEquals(8, proj.items.count { it.type == ItemType.TAPE })
        assertEquals(6, proj.items.count { it.type == ItemType.PAPER })
    }

    // ------------------------------------------------------------ text
    @Test fun textPanelEditsEverythingAboutTheText() {
        openEditor(TestSupport.seed(2))
        mainBar()
        click("Text")
        waitText("Your text", 10_000)
        val id = vm.selectedId!!
        assertEquals(ItemType.TEXT, vm.item(id)!!.type)
        rule.onNode(hasSetTextAction()).performTextReplacement("Hello Rome")
        rule.waitForIdle()
        assertEquals("Hello Rome", vm.item(id)!!.text)

        click("Style")
        assertEquals(2, sliderCount())
        setSlider(0, 200f); assertEquals(200f, vm.item(id)!!.fontSize, 2f)
        setSlider(1, 12f); assertEquals(12f, vm.item(id)!!.spacing, 1f)
        click("Left"); assertEquals("left", vm.item(id)!!.align)
        click("Right"); assertEquals("right", vm.item(id)!!.align)
        click("Centre"); assertEquals("center", vm.item(id)!!.align)
        for ((label, key) in listOf("None" to "none", "Label" to "label", "Outline" to "outline", "Shadow" to "shadow")) {
            click(label); assertEquals(key, vm.item(id)!!.effect)
        }
        toggle(); assertTrue(vm.item(id)!!.caps)
        toggle(); assertFalse(vm.item(id)!!.caps)

        click("Colour")
        val coral = 0xFFE8735A.toInt()
        clickTag("tcolor_$coral"); assertEquals(coral, vm.item(id)!!.color)

        click("Font")
        for (cat in listOf("Handwritten", "Bold", "Serif", "Typewriter", "Clean", "All")) click(cat)
        click("Serif")
        click("Playfair Display"); assertEquals("Playfair Display", vm.item(id)!!.font)
        click("DM Serif Display"); assertEquals("DM Serif Display", vm.item(id)!!.font)
        click("Typewriter")
        click("Special Elite"); assertEquals("Special Elite", vm.item(id)!!.font)
        click("Handwritten")
        click("Caveat"); assertEquals("Caveat", vm.item(id)!!.font)
        click("Done")
        waitText("Edit")
        click("Edit")
        waitText("Font")
        click("Done")
        // the whole editing session was one undo step on top of adding the item
        clickDesc("Undo")
        assertEquals("Your text", vm.item(id)?.text ?: "Your text")
    }

    @Test fun clearingTextAndExtremeSizesDoNotCrash() {
        openEditor(TestSupport.seed(2))
        mainBar(); click("Text")
        waitText("Your text")
        val id = vm.selectedId!!
        rule.onNode(hasSetTextAction()).performTextReplacement("")
        rule.waitForIdle()
        click("Style")
        setSlider(0, 400f)
        setSlider(0, 24f)
        setSlider(1, -5f)
        rule.waitForIdle()
        Thread.sleep(300)
        assertNotNull(vm.item(id))
        click("Done")
    }

    // ------------------------------------------------------------ edges & transitions
    @Test fun everyEdgeStyleSideRoughnessAndRimWork() {
        openEditor(TestSupport.seed(3))
        select(photos().first())
        val id = vm.selectedId!!
        click("Edges")
        waitText("Photo edge")
        for ((label, type) in listOf("Torn" to EdgeType.TORN, "Deckle" to EdgeType.DECKLE, "Wavy" to EdgeType.WAVE, "Pinking" to EdgeType.ZIGZAG, "Fade" to EdgeType.FADE, "None" to EdgeType.NONE)) {
            click(label)
            assertEquals(type, vm.item(id)!!.edge.type)
            Thread.sleep(150)
        }
        click("Torn")
        for (side in listOf("L", "T", "B", "R")) click(side)
        val e = vm.item(id)!!.edge
        assertTrue(e.left && e.top && e.bottom)
        assertFalse("right was on by default and toggled off", e.right)
        setSlider(0, 100f); assertEquals(100, vm.item(id)!!.edge.roughness)
        setSlider(0, 0f); assertEquals(0, vm.item(id)!!.edge.roughness)
        val seed = vm.item(id)!!.edge.seed
        clickDesc("Shuffle the tear"); assertEquals(seed + 1, vm.item(id)!!.edge.seed)
        val rim = vm.item(id)!!.edge.rim
        toggle(); assertNotEquals(rim, vm.item(id)!!.edge.rim)
        Thread.sleep(300)
        click("Done")
    }

    @Test fun edgesPanelWithoutAPhotoSelectedShowsHint() {
        openEditor(TestSupport.seed(2))
        // transitions panel -> switch to photo edge tab with nothing selected
        click("Transitions")
        click("Photo edge")
        waitText("Tap a photo on the canvas first, then choose how its edge looks.")
    }

    @Test fun everySlideTransitionCanBeApplied() {
        openEditor(TestSupport.seed(3))
        click("Transitions")
        waitText("Cut between slide 1 and 2")
        for ((label, kind) in listOf("Torn" to SeamKind.TORN, "Taped" to SeamKind.TAPED, "Paper strip" to SeamKind.STRIP, "Fold shadow" to SeamKind.SHADOW, "Clean cut" to SeamKind.CLEAN, "Taped" to SeamKind.TAPED)) {
            click(label)
            assertEquals(kind, proj.seamKind(0))
            Thread.sleep(150)
        }
        clickDesc("Next seam")
        waitText("Cut between slide 2 and 3")
        click("Torn")
        assertEquals(SeamKind.TORN, proj.seamKind(1))
        assertEquals("seam 0 untouched", SeamKind.TAPED, proj.seamKind(0))
        toggle() // every cut
        click("Paper strip")
        assertEquals(SeamKind.STRIP, proj.seamKind(0))
        assertEquals(SeamKind.STRIP, proj.seamKind(1))
        clickDesc("Previous seam")
        waitText("Cut between slide 1 and 2")
        click("Done")
    }

    @Test fun transitionsNeedTwoSlides() {
        openEditor(TestSupport.seed(1))
        click("Transitions")
        waitText("Add a second slide to decorate the cut between slides.")
    }

    // ------------------------------------------------------------ slides
    @Test fun slidesPanelDuplicateMoveBlankDelete() {
        openEditor(TestSupport.seed(3))
        click("Slides")
        waitText("Move left")
        click("Move right")
        assertEquals(listOf("p1.jpg", "p0.jpg", "p2.jpg"), orderedMedia())
        click("Move left")
        assertEquals(listOf("p0.jpg", "p1.jpg", "p2.jpg"), orderedMedia())
        click("Duplicate")
        assertEquals(4, proj.slideCount)
        assertEquals(listOf("p0.jpg", "p0.jpg", "p1.jpg", "p2.jpg"), orderedMedia())
        click("Blank after")
        assertEquals(5, proj.slideCount)
        click("Delete")
        assertEquals(4, proj.slideCount)
        clickDesc("Undo")
        assertEquals(5, proj.slideCount)
        clickDesc("Redo")
        assertEquals(4, proj.slideCount)
        click("Done")
    }

    private fun orderedMedia() = proj.items.filter { it.media != null }.sortedBy { proj.slideOf(it) }.map { it.media }

    @Test fun slidesPanelCapsAtTwentyAndKeepsLastSlide() {
        openEditor(TestSupport.seed(1))
        click("Slides")
        click("Delete")
        assertEquals("cannot delete the only slide", 1, proj.slideCount)
        repeat(22) { click("Duplicate") }
        assertEquals(20, proj.slideCount)
    }

    // ------------------------------------------------------------ crop
    @Test fun cropOverlayAspectPresetsMoveRotateFlipResetDone() {
        openEditor(TestSupport.seed(3))
        select(photos().first())
        val id = vm.selectedId!!
        val frameAspect = frame(vm.item(id)!!).let { it.width / it.height }
        click("Crop")
        waitText("Fit frame")
        // drag the crop window around (move mode)
        rule.runOnUiThread { }
        for (chip in listOf("Free", "Original", "1:1", "4:5", "3:4", "16:9", "Fit frame")) {
            click(chip)
            Thread.sleep(80)
        }
        click("Reset")
        click("Flip")
        val mw = vm.item(id)!!.mw; val mh = vm.item(id)!!.mh
        click("Rotate 90°")
        rule.waitUntil(15_000) { vm.item(id)!!.mw == mh && vm.item(id)!!.mh == mw }
        click("Fit frame")
        click("Done")
        val a = vm.item(id)!!
        assertTrue(a.flipH)
        val cropAspect = ((a.cropR - a.cropL) * a.mw) / ((a.cropB - a.cropT) * a.mh)
        assertEquals("crop matches the frame shape", frameAspect, cropAspect, 0.05f)
        assertNotNull("still bound to its cell with Fit frame", a.blockId)
    }

    @Test fun cropToSquareReshapesTheFrameAndFreesTheCell() {
        openEditor(TestSupport.seed(3))
        select(photos().first())
        val id = vm.selectedId!!
        click("Crop")
        click("1:1")
        click("Done")
        val a = vm.item(id)!!
        assertNull(a.blockId)
        assertEquals(a.w, a.h, 2f)
    }

    @Test fun cropCancelChangesNothing() {
        openEditor(TestSupport.seed(3))
        select(photos().first())
        val id = vm.selectedId!!
        val before = vm.item(id)!!
        click("Crop")
        click("Free")
        click("Cancel")
        assertEquals(before, vm.item(id)!!)
    }

    @Test fun cropWindowCanBeDraggedAndResized() {
        openEditor(TestSupport.seed(3))
        select(photos().first())
        val id = vm.selectedId!!
        click("Crop")
        click("Free")
        val w = rule.onRoot().fetchSemanticsNode().size
        val mid = Offset(w.width / 2f, w.height * 0.42f)
        rule.onRoot().performTouchInput { down(mid); moveTo(mid + Offset(60f, 40f)); up() }
        rule.waitForIdle()
        // corner drag: top-left of the image area, towards the centre
        rule.onRoot().performTouchInput { down(Offset(w.width * 0.5f, w.height * 0.42f)); moveTo(Offset(w.width * 0.45f, w.height * 0.4f)); up() }
        rule.waitForIdle()
        click("Done")
        assertNotNull(vm.item(id))
    }

    // ------------------------------------------------------------ undo / redo / persistence
    @Test fun undoRedoWalkThroughSeveralEdits() {
        openEditor(TestSupport.seed(3))
        val n0 = proj.items.size
        openStickers()
        clickTag("sticker_${lib.all.first().id}")
        assertEquals(n0 + 1, proj.items.size)
        click("Duplicate")
        assertEquals(n0 + 2, proj.items.size)
        clickDesc("Undo"); assertEquals(n0 + 1, proj.items.size)
        clickDesc("Undo"); assertEquals(n0, proj.items.size)
        clickDesc("Redo"); assertEquals(n0 + 1, proj.items.size)
        clickDesc("Redo"); assertEquals(n0 + 2, proj.items.size)
        assertFalse(vm.canRedo)
    }

    @Test fun backAutosavesAndReopeningShowsTheSameCarousel() {
        val p = TestSupport.seed(3, name = "Persist")
        openEditor(p)
        openStickers()
        clickTag("sticker_${lib.all[5].id}")
        mainBar()
        val expected = proj
        clickDesc("Back")
        waitText("Your carousels")
        rule.waitUntil(10_000) { AppGraph.repo.load(p.id)?.items?.any { it.type == ItemType.STICKER } == true }
        val saved = AppGraph.repo.load(p.id)!!
        assertEquals(expected.items.map { it.id }, saved.items.map { it.id })
        // open it again from Home
        click("Persist")
        waitForEditor(p.id)
        assertEquals(saved.items.size, proj.items.size)
    }

}
