package ru.voploshchenie.app

import ru.voploshchenie.app.ui.theme.deadline
import ru.voploshchenie.app.ui.theme.success
import ru.voploshchenie.app.ui.theme.secondaryText
import ru.voploshchenie.app.ui.theme.AppColors
import ru.voploshchenie.app.ui.theme.AppTypography
import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight

class AppThemeTest {
    @Test
    fun tabHeadingsAndLargeDatesUseManropeBold() {
        assertEquals(FontWeight.Bold, AppTypography.headlineMedium.fontWeight)
        assertEquals(FontWeight.Bold, AppTypography.displayLarge.fontWeight)
    }

    @Test
    fun colorTokensMatchTheDesignPalette() {
        assertEquals(0xFFF8F7FC.toInt(), AppColors.light.background.toArgb())
        assertEquals(0xFFFFFFFF.toInt(), AppColors.light.surface.toArgb())
        assertEquals(0xFF211C35.toInt(), AppColors.light.onBackground.toArgb())
        assertEquals(0xFF655E78.toInt(), AppColors.light.onSurfaceVariant.toArgb())
        assertEquals(0xFF6D4AFF.toInt(), AppColors.light.primary.toArgb())
        assertEquals(0xFF14B8A6.toInt(), AppColors.light.secondary.toArgb())
        assertEquals(0xFF0F766E.toInt(), AppColors.light.secondaryText.toArgb())
        assertEquals(0xFF15803D.toInt(), AppColors.light.success.toArgb())
        assertEquals(0xFF92400E.toInt(), AppColors.light.deadline.toArgb())
        assertEquals(0xFFDDD9E8.toInt(), AppColors.light.outline.toArgb())

        assertEquals(0xFF111019.toInt(), AppColors.dark.background.toArgb())
        assertEquals(0xFF1C192B.toInt(), AppColors.dark.surface.toArgb())
        assertEquals(0xFFF5F3FF.toInt(), AppColors.dark.onBackground.toArgb())
        assertEquals(0xFFB8B3CC.toInt(), AppColors.dark.onSurfaceVariant.toArgb())
        assertEquals(0xFFB6A2FF.toInt(), AppColors.dark.primary.toArgb())
        assertEquals(0xFF5EEAD4.toInt(), AppColors.dark.secondary.toArgb())
        assertEquals(0xFF5EEAD4.toInt(), AppColors.dark.secondaryText.toArgb())
        assertEquals(0xFF4ADE80.toInt(), AppColors.dark.success.toArgb())
        assertEquals(0xFFFBBF24.toInt(), AppColors.dark.deadline.toArgb())
        assertEquals(0xFF716D86.toInt(), AppColors.dark.outline.toArgb())
    }
}
