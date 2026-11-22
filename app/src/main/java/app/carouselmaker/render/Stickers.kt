package app.carouselmaker.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ComposePathEffect
import android.graphics.DashPathEffect
import android.graphics.DiscretePathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathEffect
import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.PathParser
import app.carouselmaker.data.StickerLook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Collections

@Serializable
data class StickerFile(val viewBox: Int = 64, val packs: List<StickerPack>)

@Serializable
data class StickerPack(val id: String, val name: String, val stickers: List<StickerDef>)

@Serializable
data class StickerDef(val id: String, val name: String, val short: String = "", val layers: List<StickerLayer>)

@Serializable
data class StickerLayer(
    val k: String,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val sw: Float? = null,
    val shapes: List<StickerShape>,
)

@Serializable
data class StickerShape(
    val d: String? = null,
    val t: String? = null,
    val x: Float = 0f,
    val y: Float = 0f,
    val size: Float = 10f,
    val anchor: String = "start",
    val mono: Boolean = false,
    val fill: String? = null,
    val nofill: Boolean = false,
    val stroke: String? = null,
    val sw: Float? = null,
    val dash: List<Float>? = null,
    val op: Float? = null,
    val rot: List<Float>? = null,
)

class StickerLibrary(context: Context) {
    val packs: List<StickerPack> = runCatching {
        val text = context.assets.open("stickers.json").bufferedReader().use { it.readText() }
        Json { ignoreUnknownKeys = true }.decodeFromString(StickerFile.serializer(), text).packs
    }.getOrDefault(emptyList())

    private val byId = packs.flatMap { it.stickers }.associateBy { it.id }
    fun get(id: String?): StickerDef? = if (id == null) null else byId[id]
    val all: List<StickerDef> get() = packs.flatMap { it.stickers }
}

/** Renders the sketched stickers to bitmaps (ink line with a wobble, loose watercolour wash). */
class StickerBitmaps(val library: StickerLibrary) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val pending = Collections.synchronizedSet(HashSet<String>())

    var version by mutableIntStateOf(0)
        private set

    private fun key(id: String, look: StickerLook, px: Int) = "$id@${look.name}@$px"

    fun get(id: String, look: StickerLook, px: Int): ImageBitmap? {
        val k = key(id, look, px)
        cache.get(k)?.let { return it }
        if (pending.add(k)) {
            scope.launch {
                val def = library.get(id)
                if (def != null) {
                    cache.put(k, render(def, look, px))
                    version++
                }
                pending.remove(k)
            }
        }
        return null
    }

    fun getBlocking(id: String, look: StickerLook, px: Int): ImageBitmap? {
        val k = key(id, look, px)
        cache.get(k)?.let { return it }
        val def = library.get(id) ?: return null
        return render(def, look, px).also { cache.put(k, it) }
    }

    private fun parse(c: String?, fallback: Int): Int = try {
        if (c == null) fallback else android.graphics.Color.parseColor(c)
    } catch (e: Exception) {
        fallback
    }

    fun render(def: StickerDef, look: StickerLook, px: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val s = px / 72f
        canvas.scale(s, s)
        canvas.translate(4f, 4f)
        val inkColor = if (look == StickerLook.PENCIL) 0xFF6A655E.toInt() else 0xFF2A2622.toInt()
        val lineK = if (look == StickerLook.PENCIL) 0.7f else 1f
        for (layer in def.layers) {
            if (layer.k == "wash" && look == StickerLook.INK) continue
            val layerAlpha = when (layer.k) {
                "wash" -> if (look == StickerLook.PENCIL) 0.35f else 0.85f
                "fine" -> 0.8f
                else -> 1f
            }
            canvas.save()
            canvas.translate(layer.dx, layer.dy)
            for (sh in layer.shapes) {
                canvas.save()
                sh.rot?.let { if (it.size == 3) canvas.rotate(it[0], it[1], it[2]) }
                val alpha = layerAlpha * (sh.op ?: 1f)
                if (sh.t != null) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = parse(sh.fill, inkColor)
                        this.alpha = (255 * alpha).toInt()
                        textSize = sh.size
                        textAlign = when (sh.anchor) { "middle" -> Paint.Align.CENTER; "end" -> Paint.Align.RIGHT; else -> Paint.Align.LEFT }
                        typeface = if (sh.mono) Typeface.MONOSPACE else Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    canvas.drawText(sh.t, sh.x, sh.y, p)
                } else if (sh.d != null) {
                    val path: Path = PathParser.createPathFromPathData(sh.d)
                    val fillColor = if (sh.nofill) null else sh.fill
                    if (fillColor != null) {
                        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.FILL
                            color = parse(fillColor, 0)
                            this.alpha = (255 * alpha * (android.graphics.Color.alpha(color) / 255f)).toInt()
                            pathEffect = DiscretePathEffect(4f, if (layer.k == "wash") 0.8f else 0.4f)
                        }
                        canvas.drawPath(path, p)
                    }
                    val strokeColor = sh.stroke
                    val isLine = layer.k == "ink" || layer.k == "fine"
                    if (strokeColor != null || isLine) {
                        val base = when (layer.k) { "fine" -> 0.9f; "ink" -> 1.7f; else -> 1.7f }
                        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            strokeWidth = (sh.sw ?: layer.sw ?: base) * lineK
                            color = if (strokeColor != null) parse(strokeColor, inkColor) else inkColor
                            this.alpha = (255 * alpha).toInt()
                            val rough: PathEffect = DiscretePathEffect(if (layer.k == "fine") 2.5f else 3.2f, if (layer.k == "fine") 0.35f else 0.55f)
                            pathEffect = if (sh.dash != null && sh.dash.size >= 2) {
                                ComposePathEffect(DashPathEffect(sh.dash.toFloatArray(), 0f), rough)
                            } else rough
                        }
                        canvas.drawPath(path, p)
                    }
                }
                canvas.restore()
            }
            canvas.restore()
        }
        return bmp.asImageBitmap()
    }
}
