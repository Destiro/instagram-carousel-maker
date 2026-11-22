package app.carouselmaker.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.carouselmaker.data.AppGraph
import app.carouselmaker.data.Project
import app.carouselmaker.ui.common.IconBtn
import app.carouselmaker.ui.common.ProjectThumb
import app.carouselmaker.ui.theme.Palette

/** Temporary read-only view; replaced by the real editor in the next feature. */
@Composable
fun EditorScreen(projectId: String, onBack: () -> Unit) {
    var project by remember { mutableStateOf<Project?>(null) }
    LaunchedEffect(projectId) { project = AppGraph.repo.load(projectId) }
    Column(Modifier.fillMaxSize().background(Palette.Bg2).systemBarsPadding()) {
        IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, Modifier.padding(8.dp))
        project?.let {
            Text(it.name, Modifier.padding(16.dp), color = Palette.Text)
            ProjectThumb(it, Modifier.fillMaxWidth().height(300.dp), maxDim = 1000)
        }
        Box(Modifier.fillMaxSize())
    }
}
