package app.carouselmaker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import app.carouselmaker.data.*
import app.carouselmaker.ui.editor.HandleKind
import app.carouselmaker.ui.editor.handlesFor
import app.carouselmaker.ui.editor.resizeItem
import app.carouselmaker.ui.editor.rotVec
import app.carouselmaker.ui.editor.snapRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureMathTest {
    private fun photo(x: Float = 0f, y: Float = 0f, w: Float = 200f, h: Float = 100f, rot: Float = 0f) =
        Item(id = "a", type = ItemType.PHOTO, x = x, y = y, w = w, h = h, rotation = rot)

    private fun frame(i: Item) = Rect(i.x, i.y, i.x + i.w, i.y + i.h)

    @Test fun cornerDragKeepsAspectAndAnchor() {
        val it0 = photo()
        val r = resizeItem(it0, frame(it0), HandleKind.BR, Offset(300f, 150f))
        assertEquals(300f, r.w, 0.5f)
        assertEquals(150f, r.h, 0.5f)
        assertEquals(0f, r.x, 0.5f)
        assertEquals(0f, r.y, 0.5f)
    }

    @Test fun cornerDragFromTopLeftMovesOrigin() {
        val it0 = photo(x = 100f, y = 100f)
        val r = resizeItem(it0, frame(it0), HandleKind.TL, Offset(0f, 50f))
        assertEquals(300f, r.w, 1f) // scale 1.5 about the bottom-right anchor (300,200)
        assertEquals(150f, r.h, 1f)
        assertEquals(300f, r.x + r.w, 0.5f)
        assertEquals(200f, r.y + r.h, 0.5f)
    }

    @Test fun sideHandleStretchesOneAxisOnly() {
        val it0 = photo()
        val r = resizeItem(it0, frame(it0), HandleKind.R, Offset(400f, 777f))
        assertEquals(400f, r.w, 0.5f)
        assertEquals(100f, r.h, 0.5f)
        val b = resizeItem(it0, frame(it0), HandleKind.B, Offset(55f, 260f))
        assertEquals(200f, b.w, 0.5f)
        assertEquals(260f, b.h, 0.5f)
    }

    @Test fun cannotCollapseBelowMinimum() {
        val it0 = photo()
        val r = resizeItem(it0, frame(it0), HandleKind.R, Offset(-500f, 50f))
        assertTrue(r.w >= 39.9f)
        val c = resizeItem(it0, frame(it0), HandleKind.BR, Offset(-500f, -500f))
        assertTrue(c.w >= 39.9f && c.h >= 19.9f)
    }

    @Test fun oppositeCornerStaysFixedWhenRotated() {
        val it0 = photo(x = 50f, y = 20f, w = 240f, h = 120f, rot = 30f)
        val fr = frame(it0)
        fun world(i: Item, sx: Int, sy: Int): Offset {
            val f = frame(i)
            return f.center + rotVec(Offset(sx * f.width / 2, sy * f.height / 2), i.rotation)
        }
        val anchorBefore = world(it0, -1, -1)
        val pointer = world(it0, 1, 1) + rotVec(Offset(60f, 30f), 30f)
        val r = resizeItem(it0, fr, HandleKind.BR, pointer)
        val anchorAfter = world(r, -1, -1)
        assertEquals(anchorBefore.x, anchorAfter.x, 0.5f)
        assertEquals(anchorBefore.y, anchorAfter.y, 0.5f)
        assertTrue(r.w > it0.w)
        assertEquals(it0.w / it0.h, r.w / r.h, 0.01f)
    }

    @Test fun textCornerScalesFont() {
        val t = Item(id = "t", type = ItemType.TEXT, x = 0f, y = 0f, w = 400f, h = 100f, fontSize = 80f)
        val r = resizeItem(t, Rect(0f, 0f, 400f, 100f), HandleKind.BR, Offset(800f, 200f))
        assertEquals(160f, r.fontSize, 1f)
    }

    @Test fun handleSetsPerItemType() {
        assertEquals(9, handlesFor(photo()).size)
        assertFalse(HandleKind.T in handlesFor(Item(id = "s", type = ItemType.STICKER)))
        assertTrue(HandleKind.L in handlesFor(Item(id = "t", type = ItemType.TAPE)))
        assertTrue(HandleKind.ROT in handlesFor(Item(id = "x", type = ItemType.TEXT)))
    }

    @Test fun rotationVectorMath() {
        val v = rotVec(Offset(10f, 0f), 90f)
        assertEquals(0f, v.x, 0.001f)
        assertEquals(10f, v.y, 0.001f)
        val back = rotVec(v, -90f)
        assertEquals(10f, back.x, 0.001f)
    }

    private fun project(): Project =
        createProject("t", Ratio.R4_5, 3, emptyList(), blank = false)

    @Test fun snapsToMarginAndCentre() {
        val p = project()
        val (dx, _, g) = snapRect(p, Rect(64f, 300f, 364f, 600f), 10f)
        assertEquals(-4f, dx, 0.01f)
        assertTrue(g.any { it.vertical && it.pos == 60f })
        val (cx, _, g2) = snapRect(p, Rect(390f, 300f, 696f, 600f), 10f) // centre 543 vs slide centre 540
        assertEquals(-3f, cx, 0.01f)
        assertTrue(g2.any { it.vertical && it.pos == 540f })
    }

    @Test fun noSnapWhenFarAway() {
        val p = project()
        val (dx, dy, g) = snapRect(p, Rect(200f, 333f, 330f, 433f), 10f)
        assertEquals(0f, dx, 0f)
        assertEquals(0f, dy, 0f)
        assertTrue(g.isEmpty())
    }

    @Test fun snapsToVerticalCentreAndBottomMargin() {
        val p = project()
        val h = p.canvasH
        val (_, dy, g) = snapRect(p, Rect(200f, h - 60f - 100f - 5f, 400f, h - 60f - 5f), 10f)
        assertEquals(5f, dy, 0.01f)
        assertTrue(g.any { !it.vertical })
    }
}
