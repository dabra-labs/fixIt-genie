package ai.fixitbuddy.app.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// FixIt Genie — always dark, always on-brand. No system/dynamic color overrides.
private val GenieColorScheme = darkColorScheme(
    primary              = GenieOrange,
    onPrimary            = Color(0xFF1A0800),
    primaryContainer     = Color(0xFF2A1200),
    onPrimaryContainer   = Color(0xFFFFB07A),
    secondary            = GeniePurple,
    onSecondary          = Color(0xFF0A0018),
    secondaryContainer   = Color(0xFF1A0A30),
    onSecondaryContainer = Color(0xFFCC99FF),
    tertiary             = StatusListening,
    onTertiary           = Color(0xFF001A08),
    tertiaryContainer    = Color(0xFF00200A),
    onTertiaryContainer  = Green90,
    error                = StatusError,
    onError              = Color(0xFF1A0000),
    errorContainer       = Color(0xFF2A0000),
    onErrorContainer     = Red90,
    background           = AppBackground,
    onBackground         = TextPrimary,
    surface              = AppSurface,
    onSurface            = TextPrimary,
    surfaceVariant       = AppCard,
    onSurfaceVariant     = TextSecondary,
    outline              = AppBorder,
    outlineVariant       = AppBorderBright,
    scrim                = Color(0xCC080810),
    inverseSurface       = TextPrimary,
    inverseOnSurface     = AppBackground,
    inversePrimary       = GenieOrangeDeep,
    surfaceTint          = GenieOrangeDim,
)

@Composable
fun FixItBuddyTheme(
    darkTheme: Boolean = true,         // always dark — brand identity
    dynamicColor: Boolean = false,     // never override with system colors
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GenieColorScheme,
        typography = Typography,
        content = content
    )
}
