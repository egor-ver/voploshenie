package ru.voploshchenie.app

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config
import ru.voploshchenie.app.ui.components.OpportunityCard
import ru.voploshchenie.app.ui.result.SphereDots
import ru.voploshchenie.app.ui.theme.CatalogSpacing
import ru.voploshchenie.app.ui.theme.Spacing
import ru.voploshchenie.app.ui.theme.AppColors
import ru.voploshchenie.app.ui.theme.AppTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h844dp-mdpi")
class U05UiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun datesLightAt360dp() = dates(false)
    @Test fun datesDarkAt360dp() = dates(true)

    private fun dates(dark: Boolean) {
        val unknown = compose.activity.getString(R.string.catalog_unknown_date)
        val rolling = compose.activity.getString(R.string.catalog_rolling)
        val month = compose.activity.resources.getStringArray(R.array.catalog_months).first()
        compose.setContent {
            AppTheme(darkTheme = dark) {
                Column {
                    listOf(unknown, rolling).forEach { label ->
                        OpportunityCard("", "", "", "", label)
                    }
                    OpportunityCard("", "", "", "31", month)
                }
            }
        }
        listOf(unknown, rolling).forEach { label ->
            assertDateText(label, 11f, CatalogSpacing.undatedWidth)
        }
        assertDateText("31", 32f, CatalogSpacing.dateWidth)
        assertDateText(month, 12f, CatalogSpacing.dateWidth)
    }

    private fun assertDateText(text: String, fontSize: Float, columnWidth: androidx.compose.ui.unit.Dp) {
        val layout = textLayout(text)
        val bounds = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        val availableWidth = with(compose.density) { (columnWidth - Spacing.step * 2).toPx() }
        val geometry = "text=$text size=${layout.size}, paragraph=${layout.multiParagraph.width}, " +
            "line=${layout.getLineLeft(0)}..${layout.getLineRight(0)}, node=$bounds, available=$availableWidth"
        assertEquals(geometry, 1, layout.lineCount)
        assertEquals(text, layout.layoutInput.text.text)
        assertEquals(text.length, layout.getLineEnd(0, visibleEnd = true))
        assertFalse(geometry, layout.isLineEllipsized(0))
        assertFalse(geometry, layout.hasVisualOverflow)
        assertEquals(fontSize, layout.layoutInput.style.fontSize.value)
        assertEquals(geometry, layout.size.width.toFloat(), layout.multiParagraph.width, 0f)
        assertEquals(geometry, layout.size.width.toFloat(), bounds.width, 0f)
        assertTrue(geometry, bounds.width <= availableWidth)
        assertTrue(geometry, layout.getLineLeft(0) >= 0f)
        assertTrue(geometry, layout.getLineRight(0) <= layout.size.width)
        assertTrue(geometry, layout.getLineRight(0) <= bounds.width)
        assertTrue(geometry, layout.getLineTop(0) >= 0f)
        assertTrue(geometry, layout.getLineBottom(0) <= layout.size.height)
        assertTrue(geometry, layout.getLineBottom(0) <= bounds.height)
    }

    @Test fun zeroAndFullPercentRemainVisibleAt360dp() {
        compose.setContent {
            AppTheme {
                Column { SphereDots(0); SphereDots(100) }
            }
        }
        listOf(0, 100).forEach { value ->
            val label = compose.activity.getString(R.string.result_chart_percent, value)
            compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(value / 100f, 0f..1f))).assertExists()
            assertEquals(1, textLayout(label).lineCount)
            assertFalse(textLayout(label).hasVisualOverflow)
        }
    }

    @Test fun darkTextControlsAndCtaHaveReadableContrast() {
        val colors = AppColors.dark
        fun contrast(foreground: androidx.compose.ui.graphics.Color, background: androidx.compose.ui.graphics.Color) =
            ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb())
        assertTrue(contrast(colors.onSurface, colors.surface) >= 4.5)
        assertTrue(contrast(colors.onSurfaceVariant, colors.surface) >= 4.5)
        assertTrue(contrast(colors.primary, colors.surface) >= 4.5)
        assertTrue(contrast(colors.onSecondary, colors.secondary) >= 4.5)
        assertTrue(contrast(colors.outline, colors.surface) >= 3.0)
    }

    private fun textLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
}
