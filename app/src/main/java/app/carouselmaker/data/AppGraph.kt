package app.carouselmaker.data

import android.app.Application
import app.carouselmaker.render.AppExtras
import app.carouselmaker.render.ImageStore
import app.carouselmaker.render.StickerBitmaps
import app.carouselmaker.render.StickerLibrary
import app.carouselmaker.render.TextEngine

object AppGraph {
    lateinit var app: Application
    lateinit var repo: ProjectRepository
    lateinit var importer: MediaImporter
    lateinit var images: ImageStore

    /** Sticker/text drawing hooks, set once those features are initialised. */
    var extras: AppExtras? = null

    fun init(application: Application) {
        app = application
        repo = ProjectRepository(application)
        importer = MediaImporter(application, repo)
        images = ImageStore(repo)
        extras = AppExtras(StickerBitmaps(StickerLibrary(application))).also { it.texts = TextEngine(application) }
    }
}

class CarouselApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
