package ai.fixitbuddy.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ai.fixitbuddy.app.features.session.SessionScreen
import ai.fixitbuddy.app.features.history.HistoryScreen
import ai.fixitbuddy.app.features.settings.SettingsScreen

object Routes {
    const val SESSION = "session"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

@Composable
fun FixItBuddyNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.SESSION
    ) {
        composable(Routes.SESSION) {
            SessionScreen(
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
