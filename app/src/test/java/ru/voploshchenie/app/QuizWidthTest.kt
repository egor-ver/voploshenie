package ru.voploshchenie.app

import androidx.compose.ui.unit.dp
import ru.voploshchenie.app.data.local.asset.ConfigLoader
import ru.voploshchenie.app.domain.model.ConfigQuestion
import ru.voploshchenie.app.domain.scoring.ExperienceAnswer
import ru.voploshchenie.app.ui.quiz.NeedsRatings
import ru.voploshchenie.app.ui.quiz.TestQuestionScreen
import ru.voploshchenie.app.ui.theme.AppTheme
import ru.voploshchenie.app.domain.model.ValueSelection
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h844dp-mdpi")
class T06QuizWidthTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun everyInputTypeAndPairOrderIsUsableWithin360Dp() {
        val config = ConfigLoader.load(compose.activity)
        val groupedProfile = config.parts.first { it.id == "profile" }.questions
        val groupedValues = config.parts.first { it.id == "values" }.questions
        val all = config.parts.flatMap { it.questions }
        val needs = config.parts.first { it.id == "needs" }.questions
        val questions = listOf(
            ConfigQuestion("profile", emptyMap(), "profile"),
            ConfigQuestion("values", emptyMap(), "rank_values"),
            *all.filter { it.type == "pair" }.toTypedArray(),
            all.first { it.type == "single_choice" },
            all.first { it.type == "likert5" },
            all.first { it.type == "scale" },
            all.first { it.type == "text" },
            all.first { it.type == "text_energy5" },
            ConfigQuestion("needs/ratings", emptyMap(), "needs_ratings"),
            config.parts.first { it.id == "needs" }.steps!!.first { it.type == "pick3" }.let {
                ConfigQuestion("needs/${it.id}", emptyMap(), "pick3", it.options, it.prompt, min = it.min, max = it.max)
            },
        )
        val index = mutableIntStateOf(0)
        var changed = ""
        val selection = mutableIntStateOf(0)
        val answer = mutableStateOf<String?>(null)
        compose.runOnUiThread {
            compose.activity.setContent {
                AppTheme {
                    val question = questions[index.intValue]
                    if (question.id == "needs/ratings") {
                        Column(Modifier.fillMaxSize()) {
                            NeedsRatings(needs, emptyMap(), Modifier.weight(1f)) { id, value -> changed = "$id:$value" }
                        }
                    } else TestQuestionScreen(question, 0, 1, answer.value,
                        { changed = it; answer.value = it }, {}, { changed = "next" }, {},
                        groupedQuestions = when (question.type) {
                            "profile" -> groupedProfile
                            "rank_values" -> groupedValues
                            else -> emptyList()
                        },
                        pairValues = if (question.type == "pair") {
                            val types = listOf(requireNotNull(question.type_a), requireNotNull(question.type_b))
                            if (selection.intValue % 2 == 0) types else types.reversed()
                        } else listOf("A", "B"), isLastScreen = false)
                }
            }
        }
        questions.forEachIndexed { position, question ->
            compose.runOnIdle { index.intValue = position; changed = ""; answer.value = initialAnswer(question, groupedProfile) }
            if (question.id == "needs/ratings") {
                compose.onNodeWithTag("NEED08_5").performScrollTo().performClick()
                assertTrue(changed == "NEED08:5")
                return@forEachIndexed
            }
            val root = compose.onNodeWithTag("question_${question.id}")
            root.assertIsDisplayed()
            val rootBounds = root.fetchSemanticsNode().boundsInRoot
            assertTrue("${question.id} exceeds viewport: $rootBounds", rootBounds.left >= 0f && rootBounds.right <= 360f)
            compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val bounds = node.boundsInRoot
                if (bounds.width == 0f || bounds.height == 0f) return@forEach
                assertTrue("${question.id} input exceeds viewport: $bounds", bounds.left >= 0f && bounds.right <= 360f)
            }
            when (question.type) {
                "profile" -> {
                    for (value in 1..10) compose.onNodeWithTag("PRF05_$value").performScrollTo().performClick()
                    compose.onNodeWithTag("text_PRF03").performScrollTo().performTextInput("x")
                }
                "rank_values" -> groupedValues.take(3).forEachIndexed { rank, item ->
                    compose.onNodeWithTag("${item.id}_${rank + 1}").performScrollTo().performClick()
                }.also { groupedValues.drop(3).take(2).forEach {
                    compose.onNodeWithTag("${it.id}_least").performScrollTo().performClick()
                } }
                "pair" -> {
                    val ordered = listOf(requireNotNull(question.type_a), requireNotNull(question.type_b))
                    val selected = ordered[selection.intValue % 2]
                    compose.onNodeWithTag("choice_$selected").performScrollTo().performClick()
                    val fieldLabels = question.fields.filter { (key, value) ->
                        key != "ID" && value != question.id && value != question.type_a && value != question.type_b && value != question.linked
                    }.values.toList()
                    compose.onNodeWithTag("choice_$selected").assertTextContains(fieldLabels[if (selected == question.type_a) 0 else 1])
                    assertTrue(changed == selected)
                    compose.runOnIdle { selection.intValue++ }
                }
                "single_choice" -> compose.onNodeWithTag("choice_${question.options.first().id}").performScrollTo().performClick()
                "likert5", "scale" -> compose.onNodeWithTag("${question.id}_1").performScrollTo().performClick()
                "text" -> compose.onNodeWithTag("text_${question.id}").performScrollTo().performTextInput("текст")
                "text_energy5" -> {
                    compose.onNodeWithTag("text_${question.id}").performScrollTo().performTextInput("текст")
                    compose.onNodeWithTag("${question.id}_energy_5").performScrollTo().performClick()
                }
                "pick3" -> question.options.take(question.min ?: 3).forEach {
                    compose.onNodeWithTag("pick_${it.id}").performScrollTo().performClick()
                }
            }
            compose.onNodeWithText(compose.activity.getString(R.string.test_back)).assertIsDisplayed()
            val nextLabel = compose.activity.getString(R.string.test_next)
            compose.onNodeWithText(nextLabel).assertIsEnabled().performClick()
            assertTrue("${question.id} did not advance: changed=$changed, answer=${answer.value}", changed == "next")
        }
    }

    private fun initialAnswer(question: ConfigQuestion, profile: List<ConfigQuestion>): String? = when (question.type) {
        "profile" -> kotlinx.serialization.json.Json.encodeToString(profile.associate { item ->
            item.id to when (item.type) {
                "text" -> "заполнено"
                "single_choice" -> item.options.first().id
                else -> requireNotNull(item.scale).min.toString()
            }
        })
        "rank_values" -> kotlinx.serialization.json.Json.encodeToString(ValueSelection())
        "text_energy5" -> kotlinx.serialization.json.Json.encodeToString(ExperienceAnswer("ответ", 5))
        else -> null
    }
}
