package ru.voploshchenie.app

import ru.voploshchenie.app.data.local.asset.ConfigLoader
import ru.voploshchenie.app.domain.scoring.ExperienceAnswer
import ru.voploshchenie.app.ui.quiz.TextAnswer
import ru.voploshchenie.app.ui.quiz.TestQuestionScreen
import ru.voploshchenie.app.ui.theme.AppTheme
import ru.voploshchenie.app.domain.model.ValueSelection
import ru.voploshchenie.app.domain.model.experienceAnswer
import ru.voploshchenie.app.domain.model.quizQuestions
import ru.voploshchenie.app.domain.model.rankingAnswer
import ru.voploshchenie.app.domain.model.selectedIds
import ru.voploshchenie.app.domain.model.textLimit
import ru.voploshchenie.app.domain.model.validQuizAnswer
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-mdpi")
class T04QuizTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun config() = ConfigLoader.load(RuntimeEnvironment.getApplication())

    @Test fun valuesRequireDistinctRanksAndTwoDisjointLeastChoices() {
        val questions = config().parts.first { it.id == "values" }.questions
        val screen = quizQuestions(config()).first { it.id == "values" }
        var answer by mutableStateOf<String?>(null)
        compose.runOnUiThread { compose.activity.setContent { AppTheme {
            TestQuestionScreen(screen, 1, 10, answer, { answer = it }, {}, {}, {}, groupedQuestions = questions)
        } } }
        fun tap(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.test_next)).assertIsNotEnabled()
        tap("VAL01_3")
        tap("VAL02_1")
        tap("VAL03_2")
        compose.onNodeWithTag("VAL01_least").assertIsNotEnabled()
        compose.onNodeWithTag("VAL04_1").assertIsNotEnabled()
        tap("VAL04_least")
        compose.onNodeWithText(compose.activity.getString(R.string.test_next)).assertIsNotEnabled()
        tap("VAL05_least")
        compose.onNodeWithText(compose.activity.getString(R.string.test_next)).assertIsEnabled()
        compose.onNodeWithTag("VAL06_least").performScrollTo().assertIsNotEnabled()
        assertEquals(ValueSelection(listOf("VAL02", "VAL03", "VAL01"), listOf("VAL04", "VAL05")), rankingAnswer(answer))
        tap("VAL02_1")
        compose.onNodeWithText(compose.activity.getString(R.string.test_next)).assertIsNotEnabled()
        tap("VAL06_1")
        compose.onNodeWithText(compose.activity.getString(R.string.test_next)).assertIsEnabled()
        tap("VAL04_least")
        compose.onNodeWithText(compose.activity.getString(R.string.test_next)).assertIsNotEnabled()
        assertEquals(listOf("VAL05"), rankingAnswer(answer).least)
    }

    @Test fun pickThreeIsIndependentOfRatingsAndHasHardLimit() {
        val question = quizQuestions(config()).first { it.type == "pick3" }
        var answer by mutableStateOf<String?>(null)
        compose.runOnUiThread { compose.activity.setContent { AppTheme {
            TestQuestionScreen(question, 1, 10, answer, { answer = it }, {}, {}, {})
        } } }
        fun tap(id: String) = compose.onNodeWithTag("pick_$id").performScrollTo().performClick()
        tap("NEED01")
        tap("NEED02")
        compose.onNodeWithText(compose.activity.getString(R.string.test_next)).assertIsNotEnabled()
        tap("NEED03")
        compose.onNodeWithText(compose.activity.getString(R.string.test_next)).assertIsEnabled()
        compose.onNodeWithTag("pick_NEED04").performScrollTo().assertIsNotEnabled()
        tap("NEED02")
        compose.onNodeWithText(compose.activity.getString(R.string.test_next)).assertIsNotEnabled()
        tap("NEED08")
        assertEquals(listOf("NEED01", "NEED03", "NEED08"), selectedIds(answer))
        val all = quizQuestions(config())
        assertEquals("NEED08", all[all.indexOf(question) - 1].id)
        assertEquals(8, all.count { it.id.startsWith("NEED") })
        all.filter { it.id.startsWith("NEED") }.forEach { rating ->
            assertFalse(validQuizAnswer(rating, null, emptyList()))
            assertTrue(validQuizAnswer(rating, "1", emptyList()))
            assertTrue(validQuizAnswer(rating, "5", emptyList()))
        }
    }

    @Test fun textLimitCounterAndSavedStateSurviveRecreation() {
        compose.runOnUiThread {
            compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).removeAllViews()
        }
        val restoration = StateRestorationTester(compose)
        var saved = ""
        restoration.setContent { AppTheme { TextAnswer("PRF01", "", 60) { saved = it } } }
        compose.onNodeWithTag("text_PRF01").performTextReplacement("я".repeat(61))
        compose.onNodeWithTag("text_PRF01").assertTextEquals("я".repeat(60))
        compose.onNodeWithText("60 / 60").assertExists()
        assertEquals("я".repeat(60), saved)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("text_PRF01").assertTextEquals("я".repeat(60))
        compose.onNodeWithTag("text_PRF01").performTextReplacement("Имя")
        compose.onNodeWithText("3 / 60").assertExists()
        assertEquals("Имя", saved)
    }

    @Test fun experienceRequiresEnergyOnlyWhenTextIsPresent() {
        val question = config().parts.first { it.id == "experience" }.questions.first()
        var answer by mutableStateOf<String?>(null)
        compose.runOnUiThread { compose.activity.setContent { AppTheme {
            TestQuestionScreen(question, 1, 10, answer, { answer = it }, {}, {}, {})
        } } }
        compose.onNodeWithText(compose.activity.getString(R.string.test_next)).assertIsEnabled()
        compose.onNodeWithTag("text_EXP01").performTextReplacement("Ответ")
        compose.onNodeWithText(compose.activity.getString(R.string.test_next)).assertIsNotEnabled()
        compose.onNodeWithText("5").performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.test_next)).assertIsEnabled()
        assertEquals(ExperienceAnswer("Ответ", 5), experienceAnswer(answer))
        compose.onNodeWithTag("text_EXP01").performTextReplacement("x".repeat(1001))
        assertEquals(1000, experienceAnswer(answer).text.length)
        compose.onNodeWithText("1000 / 1000").assertExists()
    }

    @Test fun profileRequiresFieldsAndOtherDetailsWithTheirLimits() {
        val questions = config().parts.first { it.id == "profile" }.questions
        val screen = quizQuestions(config()).first { it.id == "profile" }
        var values = mapOf("PRF02" to questions.first { it.id == "PRF02" }.options.first().id,
            "PRF03" to "Специальность", "PRF04" to "Город", "PRF05" to "10",
            "PRF06" to questions.last().options.last().id)
        fun valid() = validQuizAnswer(screen, Json.encodeToString(values), questions)
        assertFalse(valid())
        values = values + ("PRF06_other" to "Запрос")
        assertTrue(valid())
        values = values + ("PRF03" to " ")
        assertFalse(valid())
        values = values + ("PRF03" to "x".repeat(120)) + ("PRF01" to "x".repeat(61))
        assertFalse(valid())
        values = values + ("PRF01" to "")
        assertTrue(valid())
        values = values + ("PRF06_other" to "x".repeat(201))
        assertFalse(valid())
        assertEquals(listOf(60, 120, 120), questions.filter { it.type == "text" }.map(::textLimit))
    }
}
