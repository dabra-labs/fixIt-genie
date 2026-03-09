package ai.fixitbuddy.app.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ai.fixitbuddy.app.core.di.dataStore
import ai.fixitbuddy.app.features.history.HistoryScreen
import ai.fixitbuddy.app.features.onboarding.OnboardingScreen
import ai.fixitbuddy.app.features.session.SessionScreen
import ai.fixitbuddy.app.features.settings.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

object Routes {
    const val ONBOARDING = "onboarding"
    const val SESSION = "session"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

@Composable
fun FixItBuddyNavHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Determine start destination: onboarding on first launch, session otherwise
    var startDestination by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val done = context.dataStore.data
            .map { it[ONBOARDING_DONE] ?: false }
            .first()
        startDestination = if (done) Routes.SESSION else Routes.ONBOARDING
    }

    val destination = startDestination ?: return  // Wait until resolved

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = destination
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    scope.launch {
                        context.dataStore.edit { it[ONBOARDING_DONE] = true }
                    }
                    navController.navigate(Routes.SESSION) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.SESSION) {
            SessionScreen(
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
