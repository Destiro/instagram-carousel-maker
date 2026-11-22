package app.carouselmaker.data

import android.app.Application
import app.carouselmaker.render.ImageStore

object AppGraph {
    lateinit var app: Application
    lateinit var repo: ProjectRepository
    lateinit var importer: MediaImporter
    lateinit var images: ImageStore

    /** Sticker/text drawing hooks, set once those features are initialised. */
    var extras: app.carouselmaker.render.RenderExtras? = null

    fun init(application: Application) {
        app = application
        repo = ProjectRepository(application)
        importer = MediaImporter(application, repo)
        images = ImageStore(repo)
    }
}

class CarouselApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
