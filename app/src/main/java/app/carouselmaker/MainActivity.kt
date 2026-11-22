package app.carouselmaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.carouselmaker.ui.editor.EditorScreen
import app.carouselmaker.ui.export.ExportScreen
import app.carouselmaker.ui.home.HomeScreen
import app.carouselmaker.ui.home.NewCarouselScreen
import app.carouselmaker.ui.theme.CarouselTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CarouselTheme { AppNav() } }
    }
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(onNew = { nav.navigate("new") }, onOpen = { nav.navigate("editor/$it") })
        }
        composable("new") {
            NewCarouselScreen(
                onBack = { nav.popBackStack() },
                onCreated = { id -> nav.navigate("editor/$id") { popUpTo("home") } },
            )
        }
        composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            val id = it.arguments!!.getString("id")!!
            EditorScreen(id, onBack = { nav.popBackStack() }, onExport = { pid -> nav.navigate("export/$pid") })
        }
        composable("export/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            ExportScreen(
                it.arguments!!.getString("id")!!,
                onBack = { nav.popBackStack() },
                onHome = { nav.popBackStack("home", false) },
            )
        }
    }
}
