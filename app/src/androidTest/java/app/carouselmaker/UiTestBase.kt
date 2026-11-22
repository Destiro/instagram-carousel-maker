package app.carouselmaker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.carouselmaker.data.*
import app.carouselmaker.render.frameOf
import app.carouselmaker.ui.editor.EditorViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule

/** Shared helpers for tests that drive the real UI on the emulator. */
abstract class UiTestBase {
    @get:Rule val rule = createEmptyComposeRule()
    protected var scenario: ActivityScenario<MainActivity>? = null

    protected val device: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    protected val vm: EditorViewModel get() = EditorViewModel.current!!
    protected val proj: Project get() = vm.project!!

    @Before fun baseSetUp() = TestSupport.clearProjects()

    @After fun baseTearDown() {
        scenario?.close()
        scenario = null
        TestSupport.clearProjects()
    }

    protected fun launchHome() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Your carousels").fetchSemanticsNodes().isNotEmpty() }
    }

    protected fun waitForEditor(projectId: String? = null) {
        rule.waitUntil(20_000) {
            val v = EditorViewModel.current
            v?.project != null && (projectId == null || v.project?.id == projectId) &&
                rule.onAllNodesWithTagSafe("editor_canvas")
        }
        rule.waitForIdle()
    }

    private fun ComposeTestRule.onAllNodesWithTagSafe(tag: String) =
        onAllNodes(SemanticsMatcher("tag $tag") { it.config.getOrNull(SemanticsProperties.TestTag) == tag }).fetchSemanticsNodes().isNotEmpty()

    protected fun openEditor(p: Project): EditorViewModel {
        launchHome()
        rule.waitUntil(15_000) { rule.onAllNodesWithText(p.name).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText(p.name)[0].performClick()
        waitForEditor(p.id)
        return vm
    }

    // ---- clicking ----
    protected fun click(text: String, index: Int = 0) {
        rule.waitUntil(10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().size > index }
        val n = rule.onAllNodesWithText(text)[index]
        runCatching { n.performScrollTo() }
        n.performClick()
        rule.waitForIdle()
    }

    protected fun clickTag(tag: String) {
        if (!rule.onAllNodesWithTagSafe(tag)) {
            // lazy grids only compose visible tiles: scroll the grid to the one we want
            val scrollers = rule.onAllNodes(hasScrollToNodeAction())
            for (i in 0 until scrollers.fetchSemanticsNodes().size) {
                runCatching { scrollers[i].performScrollToNode(hasTestTag(tag)) }
                if (rule.onAllNodesWithTagSafe(tag)) break
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTagSafe(tag) }
        val n = rule.onNodeWithTag(tag)
        runCatching { n.performScrollTo() }
        n.performClick()
        rule.waitForIdle()
    }

    protected fun clickDesc(desc: String, index: Int = 0) {
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().size > index }
        rule.onAllNodesWithContentDescription(desc)[index].performClick()
        rule.waitForIdle()
    }

    protected fun hasText(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    protected fun waitText(text: String, timeout: Long = 15_000) {
        rule.waitUntil(timeout) { hasText(text) }
    }

    protected fun setSlider(index: Int, value: Float) {
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))[index]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        rule.waitForIdle()
    }

    protected fun sliderCount() =
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes().size

    // ---- canvas ----
    protected fun frame(item: Item) = proj.frameOf(item, AppGraph.extras)

    protected fun screenOf(canvasPoint: Offset): Offset = vm.toScreen(canvasPoint)

    protected fun scrollTo(slide: Int) {
        rule.runOnUiThread { vm.scrollToSlide(slide) }
        rule.waitForIdle()
    }

    protected fun tapCanvas(at: Offset) {
        rule.onNodeWithTag("editor_canvas").performTouchInput { click(at) }
        rule.waitForIdle()
    }

    protected fun dragCanvas(from: Offset, to: Offset) {
        rule.onNodeWithTag("editor_canvas").performTouchInput {
            down(from)
            moveTo(from + (to - from) * 0.5f)
            moveTo(to)
            up()
        }
        rule.waitForIdle()
    }

    /** Tap the centre of [item] and wait until it is selected. */
    protected fun select(item: Item) {
        val c = screenOf(frame(item).center)
        tapCanvas(c)
        rule.waitUntil(5_000) { vm.selectedId == item.id }
    }

    protected fun photos() = proj.items.filter { it.type == ItemType.PHOTO && it.media != null }

    protected fun assertSelected(id: String) {
        assertEquals(id, vm.selectedId)
        assertNotNull(vm.item(id))
        assertTrue(true)
    }
}
