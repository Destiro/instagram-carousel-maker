package app.carouselmaker

import app.carouselmaker.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectOpsTest {
    private fun media(n: Int) = List(n) { ImportedMedia("m$it.jpg", 4000, 3000, false) }

    @Test fun distributesPhotosAcrossSlides() {
        val p = createProject("t", Ratio.R4_5, 5, media(7), blank = false)
        assertEquals(5, p.slideCount)
        assertEquals(7, p.items.count { it.media != null })
        assertTrue(p.items.all { it.blockId != null })
    }

    @Test fun addsSlidesWhenTooManyPhotos() {
        val p = createProject("t", Ratio.R4_5, 2, media(9), blank = false)
        assertEquals(3, p.slideCount)
    }

    @Test fun blankCanvasGetsPlaceholders() {
        val p = createProject("t", Ratio.R1_1, 3, emptyList(), blank = true)
        assertEquals(3, p.items.size)
        assertTrue(p.items.all { it.media == null })
    }

    @Test fun cellRectsRespectMarginAndGap() {
        val p = createProject("t", Ratio.R4_5, 1, media(1), blank = false)
        val r = p.rectOf(p.items.first())
        assertEquals(60f, r.left, 0.01f)
        assertEquals(60f, r.top, 0.01f)
        assertEquals(1020f, r.right, 0.01f)
    }

    @Test fun applyingTemplateKeepsMedia() {
        val p = createProject("t", Ratio.R4_5, 2, media(2), blank = false)
        val q = applyTemplate(p, 0, 2, "pano2")
        assertEquals(1, q.blocks.size)
        assertEquals(1, q.items.count { it.media != null && it.isBound() })
        assertEquals(1, q.items.count { it.media != null && !it.isBound() })
    }

    @Test fun jsonRoundTrip() {
        val p = createProject("t", Ratio.R3_4, 2, media(3), blank = false)
        val json = kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val back = json.decodeFromString(Project.serializer(), json.encodeToString(Project.serializer(), p))
        assertEquals(p, back)
    }
}
