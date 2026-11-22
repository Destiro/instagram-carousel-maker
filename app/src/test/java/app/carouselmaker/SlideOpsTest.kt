package app.carouselmaker

import app.carouselmaker.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideOpsTest {
    private fun project(n: Int) = createProject("t", Ratio.R4_5, n, List(n) { ImportedMedia("m$it.jpg", 4000, 3000, false) }, blank = false)

    private fun mediaOnSlide(p: Project, s: Int) = p.items.filter { it.media != null && p.slideOf(it) == s }.map { it.media }

    @Test fun moveSlideCarriesItsPhoto() {
        val p = project(3)
        val q = moveSlide(p, 0, 2)
        assertEquals(listOf<String?>("m0.jpg"), mediaOnSlide(q, 2))
        assertEquals(listOf<String?>("m1.jpg"), mediaOnSlide(q, 0))
        assertEquals(listOf<String?>("m2.jpg"), mediaOnSlide(q, 1))
    }

    @Test fun duplicateAddsASlideWithACopy() {
        val p = project(2)
        val q = duplicateSlide(p, 0)
        assertEquals(3, q.slideCount)
        assertEquals(listOf<String?>("m0.jpg"), mediaOnSlide(q, 1))
        assertEquals(listOf<String?>("m1.jpg"), mediaOnSlide(q, 2))
        assertEquals(q.items.map { it.id }.toSet().size, q.items.size)
    }

    @Test fun deleteRemovesSlideAndContent() {
        val p = project(3)
        val q = deleteSlide(p, 1)
        assertEquals(2, q.slideCount)
        assertEquals(listOf<String?>("m0.jpg"), mediaOnSlide(q, 0))
        assertEquals(listOf<String?>("m2.jpg"), mediaOnSlide(q, 1))
    }

    @Test fun cannotDeleteLastSlide() {
        val p = project(1)
        assertEquals(1, deleteSlide(p, 0).slideCount)
    }

    @Test fun seamDecorationReplacesPrevious() {
        val p = project(2)
        val a = applySeam(p, 0, SeamKind.TAPED)
        assertEquals(2, a.items.count { it.seam == 0 })
        val b = applySeam(a, 0, SeamKind.TORN)
        assertEquals(1, b.items.count { it.seam == 0 })
        assertEquals(SeamKind.TORN, b.seamKind(0))
        assertTrue(applySeam(b, 0, SeamKind.CLEAN).items.none { it.seam == 0 })
    }
}
