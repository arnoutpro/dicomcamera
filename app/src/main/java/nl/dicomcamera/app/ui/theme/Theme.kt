package nl.dicomcamera.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF0F6B6B)
private val Sand = Color(0xFFF3EFE7)
private val Ink = Color(0xFF1B1F1E)
private val Coral = Color(0xFFC45C26)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Coral,
    background = Sand,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7EC8C8),
    onPrimary = Ink,
    secondary = Color(0xFFE08A5B),
    background = Color(0xFF121615),
    onBackground = Color(0xFFE8E6E1),
    surface = Color(0xFF1C2221),
    onSurface = Color(0xFFE8E6E1),
)

@Composable
fun DicomCameraTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
