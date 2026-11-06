package app.cofcup.ui.theme

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

// Coffee-inspired brand palette (kept even on dynamic-colour devices for identity).
private val LightScheme = lightColorScheme(
    primary = Color(0xFF8B5E3C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF5DCC8),
    onPrimaryContainer = Color(0xFF35210F),
    secondary = Color(0xFF756254),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFE5DC),
    onSecondaryContainer = Color(0xFF2B2016),
    tertiary = Color(0xFF546341),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD7E8BC),
    onTertiaryContainer = Color(0xFF131F06),
    background = Color(0xFFFDF8F5),
    onBackground = Color(0xFF231917),
    surface = Color(0xFFFDF8F5),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF3DFD3),
    onSurfaceVariant = Color(0xFF53443C),
    outline = Color(0xFF867268),
    outlineVariant = Color(0xFFD8C2B7),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFEFB68C),
    onPrimary = Color(0xFF48291C),
    primaryContainer = Color(0xFF663E2B),
    onPrimaryContainer = Color(0xFFFFDCC4),
    secondary = Color(0xFFD2C1AF),
    onSecondary = Color(0xFF372C21),
    secondaryContainer = Color(0xFF4E4237),
    onSecondaryContainer = Color(0xFFF5EEE3),
    tertiary = Color(0xFFC2CD8A),
    onTertiary = Color(0xFF29330A),
    tertiaryContainer = Color(0xFF3F4A1E),
    onTertiaryContainer = Color(0xFFDFECB8),
    background = Color(0xFF1A110D),
    onBackground = Color(0xFFF0DFD6),
    surface = Color(0xFF1A110D),
    onSurface = Color(0xFFF0DFD6),
    surfaceVariant = Color(0xFF53443C),
    onSurfaceVariant = Color(0xFFD8C2B7),
    outline = Color(0xFF9F8D83),
    outlineVariant = Color(0xFF53443C),
)

@Composable
fun CofcupTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Brand colours by default; set true to follow wallpaper-based dynamic colour.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
