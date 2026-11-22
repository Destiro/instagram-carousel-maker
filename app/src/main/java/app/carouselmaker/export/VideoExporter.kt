package app.carouselmaker.export

import android.content.Context
import app.carouselmaker.data.Item
import app.carouselmaker.data.Project
import java.io.File

/** Placeholder; the real implementation (Media3 Transformer) arrives with the video feature. */
object VideoExporter {
    suspend fun export(context: Context, project: Project, slide: Int, video: Item): File? = null
}
