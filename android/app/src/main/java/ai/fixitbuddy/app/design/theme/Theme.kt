package ai.fixitbuddy.app.design.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Orange80,
    onPrimary = Color(0xFF3E1700),
    primaryContainer = Orange40,
    onPrimaryContainer = Orange90,
    secondary = Blue80,
    onSecondary = Color(0xFF003063),
    secondaryContainer = Blue40,
    onSecondaryContainer = Blue90,
    tertiary = Green80,
    onTertiary = Color(0xFF003919),
    tertiaryContainer = Green40,
    onTertiaryContainer = Green90,
    error = Red80,
    onError = Color(0xFF690005),
    errorContainer = Red40,
    onErrorContainer = Red90,
    background = Gray10,
    onBackground = Gray90,
    surface = Gray10,
    onSurface = Gray90,
    surfaceVariant = Gray30,
    onSurfaceVariant = Gray80,
    outline = Gray40
)

private val LightColorScheme = lightColorScheme(
    primary = Orange40,
    onPrimary = Color.White,
    primaryContainer = Orange90,
    onPrimaryContainer = Color(0xFF3E1700),
    secondary = Blue40,
    onSecondary = Color.White,
    secondaryContainer = Blue90,
    onSecondaryContainer = Color(0xFF003063),
    tertiary = Green40,
    onTertiary = Color.White,
    tertiaryContainer = Green90,
    onTertiaryContainer = Color(0xFF003919),
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Color(0xFF690005),
    background = Gray99,
    onBackground = Gray10,
    surface = Gray99,
    onSurface = Gray10,
    surfaceVariant = Gray95,
    onSurfaceVariant = Gray30,
    outline = Gray40
)

@Composable
fun FixItBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
