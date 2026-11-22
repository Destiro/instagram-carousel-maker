package app.carouselmaker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.carouselmaker.data.*
import app.carouselmaker.export.Exporter
import app.carouselmaker.export.ImageFormat
import app.carouselmaker.export.VideoExporter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ImportAndVideoTest {
    private val cache get() = TestSupport.context.cacheDir

    @Before fun setUp() = TestSupport.clearProjects()
    @After fun tearDown() = TestSupport.clearProjects()

    private fun jpeg(name: String, w: Int, h: Int, orientation: Int? = null): File {
        val f = File(cache, name)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.rgb(30, 120, 200))
        FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (orientation != null) {
            val e = ExifInterface(f.absolutePath)
            e.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            e.saveAttributes()
        }
        return f
    }

    @Test fun importsPhotoAndDownscalesLargeImages() {
        val id = newId()
        val f = jpeg("big.jpg", 4000, 3000)
        val m = runBlocking { AppGraph.importer.import(id, listOf(Uri.fromFile(f))) }.single()
        assertTrue(!m.isVideo)
        assertEquals(2560, maxOf(m.width, m.height))
        assertTrue(AppGraph.repo.mediaFile(id, m.name).exists())
    }

    @Test fun importAppliesExifRotation() {
        val id = newId()
        val f = jpeg("rot.jpg", 800, 400, ExifInterface.ORIENTATION_ROTATE_90)
        val m = runBlocking { AppGraph.importer.import(id, listOf(Uri.fromFile(f))) }.single()
        assertEquals(400, m.width)
        assertEquals(800, m.height)
    }

    @Test fun importSkipsBrokenFilesButKeepsGoodOnes() {
        val id = newId()
        val bad = File(cache, "bad.jpg").apply { writeText("not an image") }
        val good = jpeg("good.jpg", 600, 400)
        val list = runBlocking { AppGraph.importer.import(id, listOf(Uri.fromFile(bad), Uri.fromFile(good))) }
        assertEquals(1, list.size)
    }

    @Test fun rotate90SwapsDimensions() {
        val id = newId()
        val f = jpeg("r.jpg", 900, 600)
        val m = runBlocking { AppGraph.importer.import(id, listOf(Uri.fromFile(f))) }.single()
        val r = runBlocking { AppGraph.importer.rotate90(id, m.name) }!!
        assertEquals(m.height, r.width)
        assertEquals(m.width, r.height)
        assertTrue(AppGraph.repo.mediaFile(id, r.name).exists())
    }

    @Test fun importsPngStickerKeepingTransparency() {
        val id = newId()
        val f = File(cache, "s.png")
        val b = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.TRANSPARENT)
        android.graphics.Canvas(b).drawCircle(100f, 100f, 60f, android.graphics.Paint().apply { color = Color.RED })
        FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val m = runBlocking { AppGraph.importer.importSticker(id, Uri.fromFile(f)) }!!
        assertTrue(m.name.endsWith(".png"))
        val back = BitmapFactory.decodeFile(AppGraph.repo.mediaFile(id, m.name).absolutePath)
        assertEquals(0, Color.alpha(back.getPixel(2, 2)))
        assertEquals(255, Color.alpha(back.getPixel(100, 100)))
    }

    private fun makeVideoProject(trimStart: Long = 0, trimEnd: Long = 0, mute: Boolean = false): Pair<Project, Item> {
        val id = newId()
        val src = File(cache, "src.mp4")
        TestSupport.makeVideo(src)
        val m = runBlocking { AppGraph.importer.import(id, listOf(Uri.fromFile(src))) }.single()
        assertTrue("video not recognised", m.isVideo)
        assertTrue(m.durationMs in 1500..4000)
        assertEquals(320, m.width)
        assertEquals(240, m.height)
        // a slide with a full-bleed video cell and a second slide with a photo
        val photo = TestSupport.writePhoto(id, "ph.jpg", 1)
        var p = createProject("Vid", Ratio.R4_5, 2, listOf(m, photo), blank = false, id = id)
        val v = p.items.first { it.type == ItemType.VIDEO }
        val tuned = v.copy(trimStartMs = trimStart, trimEndMs = if (trimEnd > 0) trimEnd else m.durationMs, mute = mute)
        p = p.copy(items = p.items.map { if (it.id == v.id) tuned else it })
        AppGraph.repo.saveNow(p)
        return p to tuned
    }

    @Test fun videoImportCreatesThumbnail() {
        val (p, v) = makeVideoProject()
        assertTrue(AppGraph.repo.mediaFile(p.id, MediaImporter.thumbName(v.media!!)).exists())
        val img = AppGraph.images.getBlocking(p.id, v.media!!, 400)
        assertNotNull(img)
    }

    private fun frameAt(file: File, ms: Long): Bitmap {
        val r = MediaMetadataRetriever()
        r.setDataSource(file.absolutePath)
        val b = r.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST)!!
        r.release()
        return b
    }

    @Test fun videoExportCompositesTheSlideAroundTheClip() {
        val (p, v) = makeVideoProject()
        val out = runBlocking { VideoExporter.export(TestSupport.context, p, 0, v) }
        assertNotNull(out)
        assertTrue(out!!.length() > 10_000)
        val r = MediaMetadataRetriever()
        r.setDataSource(out.absolutePath)
        var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
        var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        // the encoder may store a landscape frame plus a rotation flag; players show it upright
        if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
        android.util.Log.i("VideoTest", "exported video: stored/upright=${w}x${h} rotation=$rot")
        val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        r.release()
        assertEquals(1080, w)
        assertEquals(1350, h)
        assertTrue("duration $dur", dur in 1200..4500)
        val frame = frameAt(out, 500)
        // corner is slide background (margin area), centre is the red video
        val bg = frame.getPixel(15, 15)
        assertTrue("corner should be slide background but was ${Integer.toHexString(bg)}", Color.red(bg) > 225 && Color.green(bg) > 225 && Color.blue(bg) > 215)
        val centre = frame.getPixel(frame.width / 2, frame.height / 2)
        assertTrue("centre should be the red clip but was ${Integer.toHexString(centre)}", Color.red(centre) > 150 && Color.green(centre) < 110)
        out.delete()
    }

    @Test fun videoExportHonoursTrim() {
        val (p, v) = makeVideoProject(trimStart = 500, trimEnd = 1500)
        val out = runBlocking { VideoExporter.export(TestSupport.context, p, 0, v) }!!
        val r = MediaMetadataRetriever()
        r.setDataSource(out.absolutePath)
        val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        r.release()
        assertTrue("trimmed duration $dur", abs(dur - 1000) < 450)
        out.delete()
    }

    @Test fun videoExportCanMuteAndRemovesAudioTrack() {
        val (p, v) = makeVideoProject(mute = true)
        val out = runBlocking { VideoExporter.export(TestSupport.context, p, 0, v) }!!
        val ex = android.media.MediaExtractor()
        ex.setDataSource(out.absolutePath)
        val mimes = (0 until ex.trackCount).map { ex.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)!! }
        ex.release()
        assertTrue(mimes.none { it.startsWith("audio") })
        assertTrue(mimes.any { it.startsWith("video") })
        out.delete()
    }

    @Test fun exportAllMakesAnMp4ForVideoSlidesAndImagesForTheRest() {
        val (p, _) = makeVideoProject()
        val p2 = p.copy(name = "VidExportT")
        TestSupport.deleteGalleryExports("VidExportT")
        try {
            val saved = runBlocking { Exporter.exportAll(TestSupport.context, p2, ImageFormat.JPG) { _, _ -> } }
            assertEquals(2, saved.size)
            assertTrue(saved.first { it.slide == 0 }.isVideo)
            assertTrue(!saved.first { it.slide == 1 }.isVideo)
            assertEquals(1, TestSupport.galleryCount("VidExportT", video = true))
            assertEquals(1, TestSupport.galleryCount("VidExportT", video = false))
        } finally {
            TestSupport.deleteGalleryExports("VidExportT")
        }
    }
}
