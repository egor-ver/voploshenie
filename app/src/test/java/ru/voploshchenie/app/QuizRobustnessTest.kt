package ru.voploshchenie.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.voploshchenie.app.data.local.asset.ConfigLoader
import ru.voploshchenie.app.ui.quiz.TestQuestionScreen
import ru.voploshchenie.app.ui.theme.AppTheme

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h844dp-mdpi")
class U04QuizRobustnessTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun longQuestionAndPrfScaleRemainReadableAtLargeTextOn360Dp() {
        val questions = ConfigLoader.load(compose.activity).parts.flatMap { it.questions }
        val open = questions.first { it.id == "OPEN01" }.copy(prompt = "Ряд чисел: 2, 6, 12, 20, 30, …")
        compose.activity.setContent {
            AppTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    TestQuestionScreen(open, 0, 28, null, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText(open.prompt!!).performScrollTo().assertIsDisplayed()
        val questionBounds = compose.onNodeWithTag("question_OPEN01").fetchSemanticsNode().boundsInRoot
        assertTrue("long question exceeds 360 dp: $questionBounds", questionBounds.left >= 0f && questionBounds.right <= 360f)

        val scale = questions.first { it.id == "PRF05" }
        compose.activity.setContent {
            AppTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    TestQuestionScreen(scale, 0, 28, null, {}, {}, {}, {})
                }
            }
        }
        (1..10).forEach { value ->
            val target = compose.onNodeWithTag("PRF05_$value").performScrollTo()
            val bounds = target.fetchSemanticsNode().boundsInRoot
            assertTrue("PRF05_$value is narrower than 48 dp: $bounds", bounds.width >= 48.dp.value)
            assertTrue("PRF05_$value exceeds 360 dp: $bounds", bounds.left >= 0f && bounds.right <= 360f)
        }
    }
}
