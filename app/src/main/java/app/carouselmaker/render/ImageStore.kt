package app.carouselmaker.render

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.carouselmaker.data.MediaImporter
import app.carouselmaker.data.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Collections
import kotlin.math.max

/** Decodes project media at a requested size, caching results. Reading [version] makes draws reactive. */
class ImageStore(private val repo: ProjectRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = object : LruCache<String, ImageBitmap>(96 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val pending = Collections.synchronizedSet(HashSet<String>())

    var version by mutableIntStateOf(0)
        private set

    private fun key(projectId: String, name: String, maxDim: Int) = "$projectId/$name@$maxDim"

    /** File to decode for a media name (videos use their thumbnail). */
    private fun fileFor(projectId: String, name: String) =
        repo.mediaFile(projectId, if (name.endsWith(".mp4")) MediaImporter.thumbName(name) else name)

    /** Non-blocking: returns a cached bitmap or null while it loads in the background. */
    fun get(projectId: String, name: String, maxDim: Int): ImageBitmap? {
        val k = key(projectId, name, maxDim)
        cache.get(k)?.let { return it }
        // fall back to any bigger/smaller version already decoded so the UI doesn't flicker
        if (pending.add(k)) {
            scope.launch {
                val bmp = decode(projectId, name, maxDim)
                if (bmp != null) {
                    cache.put(k, bmp)
                    version++
                }
                pending.remove(k)
            }
        }
        return null
    }

    fun getBlocking(projectId: String, name: String, maxDim: Int): ImageBitmap? {
        val k = key(projectId, name, maxDim)
        cache.get(k)?.let { return it }
        val bmp = decode(projectId, name, maxDim) ?: return null
        cache.put(k, bmp)
        return bmp
    }

    fun invalidate(projectId: String, name: String) {
        cache.snapshot().keys.filter { it.startsWith("$projectId/$name@") }.forEach { cache.remove(it) }
        version++
    }

    private fun decode(projectId: String, name: String, maxDim: Int): ImageBitmap? = runCatching {
        val f = fileFor(projectId, name)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(f.absolutePath, opts)?.asImageBitmap()
    }.getOrNull()
}
