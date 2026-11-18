package ru.voploshchenie.app.ui.theme

import ru.voploshchenie.app.R
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppColors {
    val light = lightColorScheme(
        primary = Color(0xFF6D4AFF), secondary = Color(0xFF14B8A6),
        primaryContainer = Color(0xFFEDE8FF),
        onSecondary = Color(0xFF211C35),
        onPrimary = Color(0xFFFFFFFF),
        background = Color(0xFFF8F7FC), surface = Color(0xFFFFFFFF),
        onBackground = Color(0xFF211C35), onSurface = Color(0xFF211C35),
        onSurfaceVariant = Color(0xFF655E78), outline = Color(0xFFDDD9E8), outlineVariant = Color(0xFFDDD9E8),
    )
    val dark = darkColorScheme(
        primary = Color(0xFFB6A2FF), secondary = Color(0xFF5EEAD4),
        primaryContainer = Color(0xFF352B52),
        onSecondary = Color(0xFF211C35),
        onPrimary = Color(0xFF211C35),
        background = Color(0xFF111019), surface = Color(0xFF1C192B),
        onBackground = Color(0xFFF5F3FF), onSurface = Color(0xFFF5F3FF),
        onSurfaceVariant = Color(0xFFB8B3CC), outline = Color(0xFF716D86), outlineVariant = Color(0xFF2A2640),
    )
    val resultGradientStart = light.primary
    val resultGradientEnd = light.secondary
    val flowerPrimary = light.primary
    val flowerSecondary = light.secondary
    val lightSecondaryText = Color(0xFF0F766E)
    val darkSecondaryText = Color(0xFF5EEAD4)
    val lightSuccess = Color(0xFF15803D)
    val darkSuccess = Color(0xFF4ADE80)
    val lightDeadline = Color(0xFF92400E)
    val darkDeadline = Color(0xFFFBBF24)
}

val ColorScheme.secondaryText: Color
    get() = if (this == AppColors.light) AppColors.lightSecondaryText else AppColors.darkSecondaryText

val ColorScheme.success: Color
    get() = if (this == AppColors.light) AppColors.lightSuccess else AppColors.darkSuccess

val ColorScheme.deadline: Color
    get() = if (this == AppColors.light) AppColors.lightDeadline else AppColors.darkDeadline

object Spacing {
    val none = 0.dp
    val step = 8.dp
    val screen = 20.dp
}

val CardRadius = 16.dp

object CatalogSpacing {
    val cardHeight = 148.dp
    val dateWidth = 84.dp
    val undatedWidth = 112.dp
    val gridWidth = 220.dp
    val gridHeight = 110.dp
    val gridGap = 15.dp
    val searchStroke = 1.8.dp
    val radioSize = 18.dp
    val radioDot = 4.dp
}

object ResultSpacing {
    val chartHeight = 120.dp
    val diagramSize = 72.dp
    val dotsWidth = 160.dp
    val dotsHeight = 24.dp
    val dotGap = 8.dp
    val barHeight = 12.dp
}

object ComponentSpacing {
    val primaryHeight = 60.dp
    val printX = 6.dp
    val printY = 8.dp
    val savedIconSize = 16.dp
    val saveWidth = 140.dp
    val saveHeight = 44.dp
    val iconSize = 18.dp
    val iconGap = 6.dp
    val dividerDash = 3.dp
}

object NavigationSpacing {
    val height = 88.dp
    val indicatorWidth = 24.dp
    val indicatorHeight = 3.dp
    val indicatorRadius = 2.dp
    val iconGap = 12.dp
    val labelSize = 10.5.sp
}


object QuizSpacing {
    val optionHeight = 64.dp
    val optionGap = 14.dp
    val optionPadding = 15.dp
    val radioSize = 16.dp
    val radioGap = 12.dp
    val selectedOffset = 4.dp
    val stroke = 1.dp
    val progressHeight = 3.dp
    val scaleHeight = 52.dp
    val touch = 48.dp
    val logoHeight = 24.dp
    val arrowSize = 18.dp
}

@OptIn(ExperimentalTextApi::class)
private val Manrope = FontFamily(
    Font(R.font.manrope, weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    Font(
        R.font.manrope,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)
internal val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

val Typography.brand: TextStyle
    get() = titleMedium.copy(fontSize = 11.sp)

object PrintSpacing {
    val grid = 24.dp
    val node = 12.dp
    val currentNode = 28.dp
    val rail = 76.dp
    val avatarWidth = 104.dp
    val avatarHeight = 112.dp
    val avatarFaceWidth = 94.dp
    val avatarFaceHeight = 100.dp
    val avatarCorner = 44.dp
    val leafCorner = 24.dp
    val pathFirstHeight = 96.dp
    val pathCurrentHeight = 112.dp
    val pathStepHeight = 76.dp
    val rulerHeight = 18.dp
    val rulerGap = 12.5.dp
    val letterWidth = 40.dp
    val avatarFont = 34.sp
    val nameFont = 30.sp
}

val Typography.printNumber: TextStyle
    get() = displayLarge.copy(fontSize = 72.sp, lineHeight = 72.sp, letterSpacing = (-3.6).sp, fontWeight = FontWeight.ExtraBold)
val Typography.printTitle: TextStyle
    get() = headlineLarge.copy(fontSize = 40.sp, lineHeight = 38.sp, letterSpacing = (-1.8).sp, fontWeight = FontWeight.ExtraBold)
val Typography.quizTitle: TextStyle
    get() = headlineMedium.copy(fontSize = 28.sp, lineHeight = 31.sp, letterSpacing = (-.7).sp, fontWeight = FontWeight.ExtraBold)
val Typography.printBody: TextStyle
    get() = bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp)

@Composable
fun AppTheme(content: @Composable () -> Unit) = AppTheme(isSystemInDarkTheme(), content)

@Composable
fun AppTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) AppColors.dark else AppColors.light,
        typography = AppTypography,
        shapes = Shapes(
            small = RoundedCornerShape(CardRadius),
            medium = RoundedCornerShape(CardRadius),
            large = RoundedCornerShape(CardRadius),
        ),
        content = content,
    )
}

val Typography.kicker: TextStyle
    get() = labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal)

val Typography.sectionTitle: TextStyle
    get() = headlineMedium.copy(fontSize = 32.sp)

val Typography.dateStatus: TextStyle
    get() = bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp)

