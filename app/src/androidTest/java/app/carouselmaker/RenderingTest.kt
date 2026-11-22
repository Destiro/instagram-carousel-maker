package app.carouselmaker

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.carouselmaker.data.*
import app.carouselmaker.export.Exporter
import app.carouselmaker.export.ImageFormat
import app.carouselmaker.render.FontCatalog
import app.carouselmaker.render.PaperKinds
import app.carouselmaker.render.RenderCtx
import app.carouselmaker.render.TapeStyles
import app.carouselmaker.render.drawScene
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenderingTest {
    @Before fun setUp() = TestSupport.clearProjects()
    @After fun tearDown() = TestSupport.clearProjects()

    private fun distinctColors(b: Bitmap): Int {
        val seen = HashSet<Int>()
        val step = maxOf(1, b.width / 60)
        for (y in 0 until b.height step step) for (x in 0 until b.width step step) seen += b.getPixel(x, y)
        return seen.size
    }

    @Test fun everyStickerRendersInEveryLook() {
        val bitmaps = AppGraph.extras!!.stickers
        val all = bitmaps.library.all
        assertEquals("48 stickers expected", 48, all.size)
        for (look in StickerLook.entries) for (def in all) {
            val img = bitmaps.render(def, look, 160).asAndroidBitmap()
            var opaque = 0
            for (y in 0 until img.height step 2) for (x in 0 until img.width step 2) if (android.graphics.Color.alpha(img.getPixel(x, y)) > 40) opaque++
            assertTrue("${def.id} ($look) drew nothing", opaque > 30)
        }
    }

    @Test fun requiredCitiesAndGenericStickersExist() {
        val names = AppGraph.extras!!.stickers.library.all.map { it.name.lowercase() }
        for (n in listOf("singapore", "london", "york", "edinburgh", "paris", "dijon", "lyon", "barcelona", "rome", "athens", "delphi", "mykonos", "coffee", "phone", "running", "swimming", "cooking")) {
            assertTrue("missing sticker for $n", names.any { it.contains(n) })
        }
    }

    @Test fun everyFontLaysOutText() {
        val texts = AppGraph.extras!!.texts!!
        for (f in FontCatalog.all) {
            for (caps in listOf(false, true)) {
                val h = texts.height(Item(id = "t", type = ItemType.TEXT, text = "Hello Rome, Dijon & Lyon", font = f.name, fontSize = 90f, w = 600f, caps = caps))
                assertTrue("${f.name} produced no height", h > 20f)
            }
        }
        // empty text must not crash
        assertTrue(texts.height(Item(id = "e", type = ItemType.TEXT, text = "", w = 500f)) > 0f)
    }

    private fun everythingProject(): Project {
        val id = newId()
        val media = (0 until 6).map { TestSupport.writePhoto(id, "p$it.jpg", it) }
        var p = createProject("All", Ratio.R4_5, 3, media, blank = false, id = id)
        p = applyTemplate(p, 0, 2, "mosaic2")
        // every edge style on a free photo
        val edgeTypes = EdgeType.entries
        edgeTypes.forEachIndexed { i, t ->
            p = p.copy(items = p.items + Item(
                id = "edge$i", type = ItemType.PHOTO, media = "p${i % 6}.jpg", mw = 1600, mh = 1200,
                x = 40f + (i % 3) * 330f, y = 300f + (i / 3) * 300f, w = 300f, h = 260f,
                edge = Edge(type = t, left = true, top = true, right = true, bottom = true, roughness = 40 + i * 10, seed = i, rim = i % 2 == 0),
                rotation = i * 7f, cropL = 0.1f, cropT = 0.1f, cropR = 0.9f, cropB = 0.9f,
            ))
        }
        // stickers: all looks, tint/outline/shadow/flip/opacity
        AppGraph.extras!!.stickers.library.all.take(10).forEachIndexed { i, s ->
            p = p.copy(items = p.items + Item(
                id = "st$i", type = ItemType.STICKER, stickerId = s.id, x = 20f + i * 90f, y = 900f, w = 250f, h = 250f,
                outline = i % 2 == 0, shadow = i % 3 == 0, flipH = i % 2 == 1, opacity = if (i == 4) 0.5f else 1f,
                tint = if (i == 5) 0xFF3F6FA8.toInt() else null, rotation = -8f + i,
            ))
        }
        TapeStyles.all.forEachIndexed { i, t ->
            p = p.copy(items = p.items + Item(id = "tp$i", type = ItemType.TAPE, style = t.id, x = 100f + i * 40f, y = 100f + i * 30f, w = 380f, h = 90f, rotation = i * 3f - 8f, opacity = if (i == 3) 0.6f else 1f))
        }
        (PaperKinds.all.map { it.id } + PaperKinds.STRIP + PaperKinds.SHADOW).forEachIndexed { i, k ->
            p = p.copy(items = p.items + Item(id = "pp$i", type = ItemType.PAPER, style = k, x = 60f + i * 130f, y = 1000f, w = 300f, h = 260f, rotation = i * 2f))
        }
        FontCatalog.all.forEachIndexed { i, f ->
            val effect = listOf("none", "shadow", "label", "outline")[i % 4]
            p = p.copy(items = p.items + Item(
                id = "tx$i", type = ItemType.TEXT, text = "Font ${f.name}", font = f.name, fontSize = 70f,
                x = 40f, y = 40f + i * 75f, w = 900f, h = 80f, color = if (i % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF141414.toInt(),
                effect = effect, align = listOf("left", "center", "right")[i % 3], caps = i % 5 == 0, spacing = (i % 4).toFloat(),
            ))
        }
        p = applySeam(p, 0, SeamKind.TAPED)
        p = applySeam(p, 1, SeamKind.STRIP)
        return p
    }

    @Test fun renderEverythingForExport() {
        val p = everythingProject()
        for (look in StickerLook.entries) {
            val q = p.copy(look = look)
            for (i in 0 until q.slideCount) {
                val b = Exporter.renderSlide(q, i, 1080, blocking = true)
                assertEquals(1080, b.width)
                assertEquals(1350, b.height)
                assertTrue("slide ${i + 1} ($look) looks blank", distinctColors(b) > 20)
                b.recycle()
            }
        }
    }

    @Test fun renderEverythingForEditingWithPlaceholders() {
        var p = everythingProject()
        p = applyTemplate(p, 2, 1, "grid4") // leaves empty cells
        val image = ImageBitmap(540, 675)
        val ctx = RenderCtx(p, AppGraph.images, maxDim = 700, editing = true, blocking = true, extras = AppGraph.extras)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(540f, 675f)) {
            scale(0.5f, 0.5f, Offset.Zero) { drawScene(ctx) }
        }
        assertTrue(distinctColors(image.asAndroidBitmap()) > 10)
    }

    @Test fun squareAndTallRatiosRenderAtTheRightSize() {
        for ((ratio, h) in listOf(Ratio.R1_1 to 1080, Ratio.R3_4 to 1440, Ratio.R4_5 to 1350)) {
            val p = TestSupport.seed(2, 2, ratio)
            val b = Exporter.renderSlide(p, 1, 1080, blocking = true)
            assertEquals(h, b.height)
        }
    }

    @Test fun previewSizeRenderingMatchesAspect() {
        val p = TestSupport.seed(2)
        val b = Exporter.renderSlide(p, 0, 540, blocking = false)
        assertEquals(540, b.width)
        assertEquals(675, b.height)
    }

    @Test fun exportAllSavesEverySlideToTheGallery() {
        val p = TestSupport.seed(4, 4, name = "RenderExportT")
        TestSupport.deleteGalleryExports("RenderExportT")
        try {
            for (fmt in ImageFormat.entries) {
                val saved = runBlocking { Exporter.exportAll(TestSupport.context, p, fmt) { _, _ -> } }
                assertEquals(4, saved.size)
                assertTrue(saved.none { it.isVideo })
                for (s in saved) {
                    TestSupport.context.contentResolver.openInputStream(s.uri)!!.use {
                        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeStream(it, null, opts)
                        assertEquals(1080, opts.outWidth)
                        assertEquals(1350, opts.outHeight)
                    }
                }
            }
            assertEquals(8, TestSupport.galleryCount("RenderExportT"))
        } finally {
            TestSupport.deleteGalleryExports("RenderExportT")
        }
    }

    @Test fun repositoryRoundTripKeepsEverything() {
        val p = everythingProject()
        AppGraph.repo.saveNow(p)
        val back = AppGraph.repo.load(p.id)
        assertNotNull(back)
        assertEquals(p, back)
    }
}
