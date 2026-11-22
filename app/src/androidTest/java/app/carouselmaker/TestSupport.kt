package app.carouselmaker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import app.carouselmaker.data.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

object TestSupport {
    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    fun clearProjects() = runBlocking {
        AppGraph.repo.list().forEach { AppGraph.repo.delete(it.id) }
    }

    /** Write a coloured test photo (with its number drawn on it) into a project's media folder. */
    fun writePhoto(projectId: String, name: String, index: Int, w: Int = 1600, h: Int = 1200): ImportedMedia {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.HSVToColor(floatArrayOf(((index * 67) % 360).toFloat(), .55f, .85f)))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = h * 0.5f; textAlign = Paint.Align.CENTER }
        c.drawText("${index + 1}", w / 2f, h * 0.65f, p)
        FileOutputStream(AppGraph.repo.mediaFile(projectId, name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return ImportedMedia(name, w, h, false)
    }

    /** A saved project with [photos] photos spread over [slides] slides. */
    fun seed(slides: Int = 3, photos: Int = slides, ratio: Ratio = Ratio.R4_5, name: String = "Test"): Project {
        val id = newId()
        val media = (0 until photos).map { writePhoto(id, "p$it.jpg", it) }
        val p = createProject(name, ratio, slides, media, blank = false, id = id)
        AppGraph.repo.saveNow(p)
        return p
    }

    /** Encode a short H.264 MP4 (red background, white box sweeping across). Takes about [frames]/30 seconds. */
    fun makeVideo(file: File, w: Int = 320, h: Int = 240, frames: Int = 70) {
        val fmt = MediaFormat.createVideoFormat("video/avc", w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 1_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val enc = MediaCodec.createEncoderByType("video/avc")
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = enc.createInputSurface()
        enc.start()
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var started = false
        val info = MediaCodec.BufferInfo()
        val paint = Paint().apply { color = Color.WHITE }

        fun drain(end: Boolean) {
            if (end) enc.signalEndOfInputStream()
            while (true) {
                val i = enc.dequeueOutputBuffer(info, 10_000)
                when {
                    i == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end) return
                    i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(enc.outputFormat); muxer.start(); started = true }
                    i >= 0 -> {
                        val buf = enc.getOutputBuffer(i)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && started) {
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        enc.releaseOutputBuffer(i, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }
        for (f in 0 until frames) {
            val c = surface.lockCanvas(null)
            c.drawColor(Color.rgb(200, 30, 30))
            val x = (w - 60) * f / frames.toFloat()
            c.drawRect(x, h / 2f - 30, x + 60, h / 2f + 30, paint)
            surface.unlockCanvasAndPost(c)
            drain(false)
            Thread.sleep(33)
        }
        drain(true)
        enc.stop(); enc.release(); muxer.stop(); muxer.release(); surface.release()
    }

    /** Delete everything the export tests put in the gallery under Carousel Maker/<folder>. */
    fun deleteGalleryExports(folder: String) {
        val like = "%Carousel Maker/$folder%"
        val cr = context.contentResolver
        cr.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf(like))
        cr.delete(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf(like))
    }

    fun galleryCount(folder: String, video: Boolean = false): Int {
        val uri = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val like = "%Carousel Maker/$folder%"
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf(like), null)
            .use { return it?.count ?: 0 }
    }
}
