package app.carouselmaker

import android.provider.MediaStore
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.carouselmaker.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeAndExportTest : UiTestBase() {

    // ------------------------------------------------------------ home
    @Test fun emptyHomeShowsHelpfulMessage() {
        launchHome()
        waitText("Nothing here yet. Tap New carousel to make your first one.")
        assertTrue(hasText("New carousel"))
    }

    @Test fun homeListsProjectsWithMetaAndOpensThem() {
        TestSupport.seed(4, name = "Alpha")
        TestSupport.seed(2, ratio = Ratio.R1_1, name = "Beta")
        launchHome()
        waitText("Alpha"); waitText("Beta")
        rule.waitUntil(5_000) { rule.onAllNodesWithTextContains("4 slides · 4:5").isNotEmpty() }
        assertTrue(rule.onAllNodesWithTextContains("2 slides · 1:1").isNotEmpty())
        click("Beta")
        waitForEditor()
        assertEquals("Beta", proj.name)
        assertEquals(Ratio.R1_1, proj.ratio)
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextContains(t: String) =
        onAllNodesWithText(t, substring = true).fetchSemanticsNodes()

    @Test fun renameAndDeleteFromTheMenu() {
        TestSupport.seed(2, name = "OldName")
        TestSupport.seed(2, name = "Keeper")
        launchHome()
        waitText("OldName")
        // open the menu of the first card that is OldName: find by order of appearance
        val idx = AppGraph.let { 0 }
        clickDesc("More", index = idx)
        click("Rename")
        rule.onNode(hasSetTextAction()).performTextReplacement("Renamed")
        click("Save")
        rule.waitUntil(8_000) { hasText("Renamed") || hasText("OldName") }
        // delete the first remaining card
        clickDesc("More", 0)
        click("Delete")
        // confirmation dialog
        waitText("This removes the carousel and its photos from the app. Photos in your gallery are not touched.")
        click("Delete")
        rule.waitUntil(8_000) { runCatching { kotlinx.coroutines.runBlocking { AppGraph.repo.list().size } }.getOrDefault(2) == 1 }
        assertEquals(1, kotlinx.coroutines.runBlocking { AppGraph.repo.list().size })
    }

    @Test fun cancellingRenameAndDeleteKeepsTheProject() {
        TestSupport.seed(2, name = "Stay")
        launchHome()
        waitText("Stay")
        clickDesc("More"); click("Rename"); click("Cancel")
        clickDesc("More"); click("Delete"); click("Cancel")
        assertEquals(1, kotlinx.coroutines.runBlocking { AppGraph.repo.list().size })
        assertTrue(hasText("Stay"))
    }

    // ------------------------------------------------------------ new carousel
    @Test fun newBlankCarouselWithCustomFormatAndSlideCount() {
        launchHome()
        click("New carousel")
        waitText("FORMAT")
        click("1:1")
        clickDesc("More slides"); clickDesc("More slides")
        clickDesc("Fewer slides")
        assertTrue(hasText("6")) // 5 + 2 - 1
        click("Blank canvas")
        click("Create carousel")
        waitForEditor()
        assertEquals(Ratio.R1_1, proj.ratio)
        assertEquals(6, proj.slideCount)
        assertEquals(6, proj.items.count { it.media == null && it.blockId != null })
        assertTrue(proj.name.startsWith("Carousel "))
    }

    @Test fun slideCountStaysBetweenOneAndTwenty() {
        launchHome()
        click("New carousel")
        waitText("FORMAT")
        repeat(30) { clickDesc("More slides") }
        assertTrue(hasText("20"))
        repeat(30) { clickDesc("Fewer slides") }
        assertTrue(hasText("1"))
    }

    @Test fun everyFormatCanBeChosenAndCreated() {
        for ((label, ratio) in listOf("4:5" to Ratio.R4_5, "1:1" to Ratio.R1_1, "3:4" to Ratio.R3_4)) {
            launchHome()
            click("New carousel")
            waitText("FORMAT")
            click(label)
            click("Blank canvas")
            click("Create carousel")
            waitForEditor()
            assertEquals(ratio, proj.ratio)
            scenario?.close(); scenario = null
            TestSupport.clearProjects()
        }
    }

    @Test fun choosePhotosOpensPickerAndBackReturnsToTheForm() {
        launchHome()
        click("New carousel")
        waitText("Choose photos & create")
        click("Choose photos & create")
        device.waitForIdle()
        Thread.sleep(1500)
        device.pressBack()
        waitText("Choose photos & create")
        assertEquals(0, kotlinx.coroutines.runBlocking { AppGraph.repo.list().size })
    }

    @Test fun backFromNewCarouselReturnsHome() {
        launchHome()
        click("New carousel")
        waitText("FORMAT")
        clickDesc("Back")
        waitText("Your carousels")
    }

    // ------------------------------------------------------------ export
    private fun openExport(p: Project) {
        openEditor(p)
        click("Export")
        waitText("Save ${p.slideCount} slides to Gallery")
    }

    @Test fun exportSavesEverySlideAsJpgAndShowsTheSavedScreen() {
        val p = TestSupport.seed(3, name = "ExpJpgT")
        TestSupport.deleteGalleryExports("ExpJpgT")
        try {
            openExport(p)
            rule.onRoot().performTouchInput { swipeLeft() } // preview pager
            rule.waitForIdle()
            click("Save 3 slides to Gallery")
            waitText("Saved to Gallery", 90_000)
            assertEquals(3, TestSupport.galleryCount("ExpJpgT"))
            assertTrue(rule.onAllNodesWithTextContains("3 photos").isNotEmpty())
            assertTrue(hasText("Open Instagram"))
            click("Open Instagram") // Instagram is not installed on the emulator: must only show a toast
            click("Share…")
            device.waitForIdle(); Thread.sleep(1500)
            device.pressBack()
            waitText("Saved to Gallery")
            click("Back to editor")
            waitForEditor(p.id)
            assertEquals(3, proj.slideCount)
        } finally {
            TestSupport.deleteGalleryExports("ExpJpgT")
        }
    }

    @Test fun exportCanSaveAsPng() {
        val p = TestSupport.seed(2, name = "ExpPngT")
        TestSupport.deleteGalleryExports("ExpPngT")
        try {
            openExport(p)
            click("PNG")
            click("Save 2 slides to Gallery")
            waitText("Saved to Gallery", 90_000)
            val cr = TestSupport.context.contentResolver
            cr.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns.MIME_TYPE),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("%Carousel Maker/ExpPngT%"), null,
            ).use {
                assertNotNull(it)
                assertEquals(2, it!!.count)
                while (it.moveToNext()) assertEquals("image/png", it.getString(0))
            }
        } finally {
            TestSupport.deleteGalleryExports("ExpPngT")
        }
    }

    @Test fun exportBackReturnsToTheEditorWithoutSaving() {
        val p = TestSupport.seed(2, name = "ExpBackT")
        openExport(p)
        clickDesc("Back")
        waitForEditor(p.id)
        assertEquals(0, TestSupport.galleryCount("ExpBackT"))
    }

    @Test fun exportHandlesDecoratedCarouselsEndToEnd() {
        val p0 = TestSupport.seed(3, name = "ExpDecoT")
        TestSupport.deleteGalleryExports("ExpDecoT")
        try {
            openEditor(p0)
            // add a sticker, text, tape and a torn edge through the real UI, then export
            click("Stickers"); waitText("Add to canvas")
            clickTag("sticker_${AppGraph.extras!!.stickers.library.all[2].id}")
            click("Done")
            click("Text"); waitText("Your text"); click("Done"); click("Done")
            click("Transitions"); click("Taped"); click("Done")
            val firstPhoto = photos().first().id
            rule.runOnUiThread { vm.selectedId = firstPhoto } // the sticker and text now sit on top of the photo centre
            rule.waitForIdle()
            click("Edges"); click("Torn"); click("Done"); click("Done")
            click("Export")
            waitText("Save 3 slides to Gallery")
            click("Save 3 slides to Gallery")
            waitText("Saved to Gallery", 90_000)
            assertEquals(3, TestSupport.galleryCount("ExpDecoT"))
        } finally {
            TestSupport.deleteGalleryExports("ExpDecoT")
        }
    }

    @Test fun savedScreenBackArrowGoesHome() {
        val p = TestSupport.seed(1, name = "ExpHomeT")
        TestSupport.deleteGalleryExports("ExpHomeT")
        try {
            openExport(p)
            click("Save 1 slides to Gallery")
            waitText("Saved to Gallery", 60_000)
            clickDesc("Back")
            waitText("Your carousels")
            assertFalse(hasText("Saved to Gallery"))
        } finally {
            TestSupport.deleteGalleryExports("ExpHomeT")
        }
    }
}
