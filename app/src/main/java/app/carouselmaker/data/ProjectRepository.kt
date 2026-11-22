package app.carouselmaker.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class ProjectRepository(context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun dir(projectId: String) = File(root, projectId).apply { mkdirs() }
    fun mediaDir(projectId: String) = File(dir(projectId), "media").apply { mkdirs() }
    fun mediaFile(projectId: String, name: String) = File(mediaDir(projectId), name)

    suspend fun list(): List<Project> = withContext(Dispatchers.IO) {
        root.listFiles().orEmpty().mapNotNull { load(it.name) }.sortedByDescending { it.updatedAt }
    }

    fun load(id: String): Project? = runCatching {
        val f = File(File(root, id), "project.json")
        if (f.exists()) json.decodeFromString(Project.serializer(), f.readText()) else null
    }.getOrNull()

    suspend fun save(project: Project) = withContext(Dispatchers.IO) { saveNow(project) }

    fun saveNow(project: Project) {
        val d = dir(project.id)
        val tmp = File(d, "project.json.tmp")
        tmp.writeText(json.encodeToString(Project.serializer(), project))
        val dst = File(d, "project.json")
        if (!tmp.renameTo(dst)) {
            dst.delete()
            tmp.renameTo(dst)
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { File(root, id).deleteRecursively() }
}
