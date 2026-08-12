package nl.dicomcamera.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.dicomcamera.app.R

/**
 * Shared with Rebost: cool linen canvas, deep teal CTAs, turquoise signal, gold accents.
 * Typography: Plus Jakarta Sans + IBM Plex Mono for IDs / UIDs.
 */
object DicomColors {
    val Linen = Color(0xFFF4F7F7)
    val Ink = Color(0xFF0F172A)

    /** Soft teal-tinted chrome for top bars. */
    val Chrome = Color(0xFFEAF6F4)

    /** Deep teal — primary fills / borders (white text). */
    val Forest = Color(0xFF0F766E)
    /** Mid turquoise — secondary fills & link text on light surfaces. */
    val ForestMid = Color(0xFF0D9488)
    /** Bright turquoise — accents & selected signals. */
    val Teal = Color(0xFF2DD4BF)
    val TealSoft = Color(0x332DD4BF)
    val Emerald = Color(0xFF5EEAD4)

    val Slate950 = Color(0xFF0B1220)
    val Slate900 = Color(0xFF0F172A)
    val Slate800 = Color(0xFF1E293B)
    val Slate700 = Color(0xFF334155)
    val Slate500 = Color(0xFF64748B)
    val Slate400 = Color(0xFF94A3B8)

    val Gold = Color(0xFFD4A017)
    val GoldInk = Color(0xFF8A6A0A)
    val GoldSoft = Color(0x33D4A017)

    val White = Color(0xFFFFFFFF)
    val Panel = Color(0xFFFCFEFE)
    val Hairline = Color(0xFFD7E0E0)
    val Rose = Color(0xFFBE123C)
    val RoseSoft = Color(0x22BE123C)

    fun onColor(background: Color): Color {
        val r = background.red
        val g = background.green
        val b = background.blue
        val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
        return if (luminance > 0.55f) Ink else White
    }
}

object DicomType {
    val Sans = FontFamily(
        Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
        Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
        Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
        Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
        Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
        Font(R.font.plus_jakarta_sans_extrabold, FontWeight.Black),
    )
    /** Brand wordmark — Sansation (OFL). Bold ≈ 700 for “Arnout.pro”. */
    val Brand = FontFamily(
        Font(R.font.sansation_regular, FontWeight.Normal),
        Font(R.font.sansation_bold, FontWeight.Bold),
        Font(R.font.sansation_bold, FontWeight.W700),
    )
    val Mono = FontFamily(
        Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
        Font(R.font.ibm_plex_mono_medium, FontWeight.Bold),
    )
}

object DicomShapes {
    val Panel = RoundedCornerShape(14.dp)
    val Control = RoundedCornerShape(12.dp)
    val Chip = RoundedCornerShape(999.dp)
    val Thumb = RoundedCornerShape(10.dp)
    val Search = RoundedCornerShape(22.dp)
}

val BrandGradient = Brush.linearGradient(
    listOf(DicomColors.Forest, DicomColors.Teal, DicomColors.Gold),
)

private val LightColors = lightColorScheme(
    primary = DicomColors.Forest,
    onPrimary = DicomColors.White,
    secondary = DicomColors.Teal,
    onSecondary = DicomColors.Ink,
    tertiary = DicomColors.Gold,
    onTertiary = DicomColors.Ink,
    background = DicomColors.Linen,
    onBackground = DicomColors.Ink,
    surface = DicomColors.Panel,
    onSurface = DicomColors.Ink,
    surfaceVariant = Color(0xFFE8F0F0),
    onSurfaceVariant = DicomColors.Slate700,
    outline = DicomColors.Hairline,
    error = DicomColors.Rose,
)

private val DicomTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = DicomType.Sans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        letterSpacing = (-0.6).sp,
        color = DicomColors.Ink,
    ),
    headlineSmall = TextStyle(
        fontFamily = DicomType.Sans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        letterSpacing = (-0.4).sp,
        color = DicomColors.Ink,
    ),
    titleLarge = TextStyle(
        fontFamily = DicomType.Sans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        letterSpacing = (-0.3).sp,
        color = DicomColors.Ink,
    ),
    titleMedium = TextStyle(
        fontFamily = DicomType.Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = (-0.2).sp,
        color = DicomColors.Ink,
    ),
    titleSmall = TextStyle(
        fontFamily = DicomType.Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = (-0.1).sp,
        color = DicomColors.Ink,
    ),
    bodyLarge = TextStyle(
        fontFamily = DicomType.Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        letterSpacing = (-0.1).sp,
        color = DicomColors.Ink,
    ),
    bodyMedium = TextStyle(
        fontFamily = DicomType.Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = DicomColors.Ink,
    ),
    bodySmall = TextStyle(
        fontFamily = DicomType.Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = DicomColors.Slate700,
    ),
    labelLarge = TextStyle(
        fontFamily = DicomType.Sans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp,
        color = DicomColors.Ink,
    ),
    labelMedium = TextStyle(
        fontFamily = DicomType.Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
        color = DicomColors.Slate500,
    ),
    labelSmall = TextStyle(
        fontFamily = DicomType.Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 0.6.sp,
        color = DicomColors.Slate500,
    ),
)

private val DicomMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

@Composable
fun DicomCameraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = DicomTypography,
        shapes = DicomMaterialShapes,
        content = content,
    )
}
