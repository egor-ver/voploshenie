package ru.voploshchenie.app

import ru.voploshchenie.app.domain.model.TestConfig
import ru.voploshchenie.app.domain.scoring.ScoringResult
import ru.voploshchenie.app.domain.model.experienceAnswer
import ru.voploshchenie.app.domain.model.rankingAnswer
import ru.voploshchenie.app.domain.model.selectedIds
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config
import kotlin.random.Random

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w390dp-h844dp-mdpi")
class MainFlowE2ETest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @javax.inject.Inject lateinit var database: ru.voploshchenie.app.data.local.db.TestDatabase
    @javax.inject.Inject lateinit var testConfig: ru.voploshchenie.app.domain.model.TestConfig
    @javax.inject.Inject lateinit var testRepository: ru.voploshchenie.app.domain.repository.TestSessionRepository
    @org.junit.Before fun inject() { hilt.inject() }
    @org.junit.After fun closeDatabase() { database.close() }

    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun newRealAttemptCompletesAllPartsWithRoomBackedUiAnswers() {
        val config = testConfig
        val repository = testRepository
        assertNull(runBlocking { repository.continueTest() })
        val demo = compose.activity.assets.open("demo_answers.json").bufferedReader().use {
            Json.decodeFromString<Map<String, String>>(it.readText())
        }
        compose.onNodeWithText(compose.activity.getString(R.string.take_test)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.test_start)).fetchSemanticsNodes().any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) } }
        compose.onNodeWithText(compose.activity.getString(R.string.test_start)).performClick()
        compose.waitUntil(10_000) { runBlocking { repository.continueTest() != null } }
        val roomSession = requireNotNull(runBlocking { repository.continueTest() })
        val sessionId = roomSession.first.id
        val expectedOrder = expectedScreens(config, roomSession.first.shuffleSeed)
        val expectedPairOrder = expectedPairOrder(config, roomSession.first.shuffleSeed)
        val questionMap = config.parts.flatMap { it.questions }.associateBy { it.id }
        val profileValues = Json.decodeFromString<Map<String, String>>(demo.getValue("profile"))
        val ranking = rankingAnswer(demo.getValue("values"))
        val visited = mutableListOf<String>()
        val interestRows = linkedMapOf<String, String>()
        var finish = false

        while (!finish) {
            val (session, rows) = requireNotNull(runBlocking { repository.continueTest() })
            val id = session.currentScreen
            visited += id
            val progress = compose.onNodeWithTag("quiz_progress").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
            assertTrue("Missing overall progress at $id: $progress", progress.contains("${(visited.size - 1) * 100 / expectedOrder.size}%"))
            val part = partFor(id, config)
            val partScreens = expectedOrder.filter { partFor(it, config) == part }
            val partNumber = partScreens.indexOf(id) + 1
            assertTrue("Missing part or question number/total at $id: $progress", progress.split(", ").first() == "%02d".format(partNumber) && progress.contains("из ${partScreens.size} · Часть"))

            if (id == "profile") {
                for ((field, value) in profileValues) {
                    val question = questionMap.getValue(field)
                    when (question.type) {
                        "text" -> enterText("text_$field", value)
                        "single_choice" -> clickTag("choice_$value")
                        "scale", "scale10" -> clickTag("${field}_$value")
                    }
                }
            } else if (id == "values") {
                ranking.top.forEachIndexed { rank, value ->
                    clickTag("${value}_${rank + 1}")
                    compose.waitUntil("saved ${ranking.top.take(rank + 1)} in values", 10_000) {
                        runBlocking { repository.continueTest()?.second?.firstOrNull { it.questionId == "valuesTop3" }?.value }
                            ?.let { Json.decodeFromString<List<String>>(it).getOrNull(rank) == value } == true
                    }
                }
                ranking.least.forEachIndexed { index, value ->
                    clickTag("${value}_least")
                    compose.waitUntil("saved ${ranking.least.take(index + 1)} as least in values", 10_000) {
                        runBlocking { repository.continueTest()?.second?.firstOrNull { it.questionId == "valuesLeast2" }?.value }
                            ?.let { value in Json.decodeFromString<List<String>>(it) } == true
                    }
                }
            } else if (id == "needs/ratings") {
                (1..8).forEach { number ->
                    val key = "NEED%02d".format(number)
                    clickTag("${key}_${demo.getValue(key)}")
                }
            } else if (id == "needs/pick3") {
                val expected = selectedIds(demo.getValue("needs/pick3"))
                expected.forEach { selected ->
                    clickTag("pick_$selected")
                    compose.waitUntil("saved $selected in needs/pick3", 10_000) {
                        runBlocking { repository.continueTest()?.second?.firstOrNull { it.questionId == "needsTop3" }?.value }
                            ?.let { selected in Json.decodeFromString<List<String>>(it) } == true
                    }
                }
                assertEquals(expected, Json.decodeFromString<List<String>>(runBlocking {
                    repository.continueTest()!!.second.single { it.questionId == "needsTop3" }.value
                }))
            } else {
                val question = questionMap.getValue(id)
                val raw = demo.getValue(id)
                when (question.type) {
                    "pair" -> {
                        val riasec = if (raw == "A") question.type_a else question.type_b
                        clickTag("choice_$riasec")
                        interestRows[id] = requireNotNull(riasec)
                    }
                    "single_choice" -> clickTag("choice_$raw")
                    "likert5", "scale" -> clickTag("${id}_$raw")
                    "text" -> enterText("text_$id", raw)
                    "text_energy5" -> {
                        val answer = experienceAnswer(raw)
                        enterText("text_$id", answer.text)
                        compose.waitUntil("save $id text", 10_000) {
                            runBlocking { repository.continueTest()?.second?.firstOrNull { it.questionId == id }?.value }
                                ?.let { experienceAnswer(it).text == answer.text } == true
                        }
                        clickTag("${id}_energy_${answer.energy}")
                        compose.waitUntil("select $id energy", 10_000) {
                            compose.onAllNodesWithTag("${id}_energy_${answer.energy}").fetchSemanticsNodes().any {
                                it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Selected) &&
                                    it.config[androidx.compose.ui.semantics.SemanticsProperties.Selected]
                            }
                        }
                        var storedAnswer: String? = null
                        try {
                            compose.waitUntil("save $id answer", 10_000) {
                                storedAnswer = runBlocking { repository.continueTest()?.second?.firstOrNull { it.questionId == id }?.value }
                                storedAnswer?.let { experienceAnswer(it) == answer } == true
                            }
                        } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
                            throw AssertionError("$id stored=$storedAnswer expected=$answer", error)
                        }
                    }
                }
            }

            val nextId = expectedOrder.getOrNull(expectedOrder.indexOf(id) + 1)
            val last = nextId == null
            val button = compose.activity.getString(if (last) R.string.test_show_result else R.string.test_next)
            compose.waitUntil("enabled $button for $id", 10_000) {
                compose.onAllNodesWithText(button).fetchSemanticsNodes().any {
                    !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
                }
            }
            val persistedRows = runBlocking { repository.continueTest()!!.second.associate { it.questionId to it.value } }
            if (id.startsWith("OPEN")) {
                val expectedText = demo.getValue(id)
                compose.waitUntil(10_000) {
                    runBlocking { repository.continueTest()?.second?.firstOrNull { it.questionId == id }?.value }
                        ?.let { Json.parseToJsonElement(it).jsonPrimitive.content == expectedText } == true
                }
                if (id == expectedOrder.first { it.startsWith("OPEN") }) {
                    compose.activityRule.scenario.recreate()
                    compose.waitUntil(10_000) { compose.onAllNodesWithTag("question_$id").fetchSemanticsNodes().isNotEmpty() }
                    assertEquals(expectedText, Json.parseToJsonElement(runBlocking {
                        repository.continueTest()!!.second.single { it.questionId == id }.value
                    }).jsonPrimitive.content)
                    compose.onNodeWithText(compose.activity.getString(R.string.test_back)).performClick()
                    val previous = expectedOrder[expectedOrder.indexOf(id) - 1]
                    compose.waitUntil(10_000) { runBlocking { repository.continueTest()?.first?.currentScreen == previous } }
                    compose.onNodeWithText(compose.activity.getString(R.string.test_next)).performClick()
                    compose.waitUntil(10_000) { runBlocking { repository.continueTest()?.first?.currentScreen == id } }
                    assertEquals(expectedText, Json.parseToJsonElement(runBlocking {
                        repository.continueTest()!!.second.single { it.questionId == id }.value
                    }).jsonPrimitive.content)
                }
            }
            if (id.startsWith("INT") || id.startsWith("CHK")) {
                val answer = Json.parseToJsonElement(persistedRows.getValue(id)).jsonPrimitive.content
                assertEquals(interestRows.getValue(id), answer)
            }
            if (id.startsWith("INT") && id == expectedOrder.first { it.startsWith("INT") }) {
                val beforeRecreate = runBlocking { repository.continueTest()!! }
                assertPairOrder(id, expectedPairOrder.getValue(id))
                compose.activityRule.scenario.recreate()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("question_$id").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(beforeRecreate, runBlocking { repository.continueTest() })
                assertPairOrder(id, expectedPairOrder.getValue(id))
                compose.onNodeWithTag("choice_${interestRows.getValue(id)}").assertIsSelected()
                val backButton = compose.onNodeWithText(compose.activity.getString(R.string.test_back))
                backButton.assertIsEnabled()
                backButton.performClick()
                val previous = expectedOrder[expectedOrder.indexOf(id) - 1]
                compose.waitUntil(10_000) { runBlocking { repository.continueTest()?.first?.currentScreen == previous } }
                val savedBeforeBack = runBlocking { repository.continueTest()!! }
                assertEquals("\"${interestRows.getValue(id)}\"", savedBeforeBack.second.single { it.questionId == id }.value)
                compose.onNodeWithText(compose.activity.getString(R.string.test_next)).performClick()
                compose.waitUntil(10_000) { runBlocking { repository.continueTest()?.first?.currentScreen == id } }
            }
                compose.onNodeWithText(button).performClick()
            if (last) {
                compose.waitUntil(10_000) { runBlocking { repository.result(sessionId) != null } }
                finish = true
            } else {
                compose.waitUntil("advance $id to $nextId", 10_000) { runBlocking { repository.continueTest()?.first?.currentScreen == nextId } }
                assertEquals("$id should be followed by $nextId", nextId,
                    runBlocking { repository.continueTest()!!.first.currentScreen })
                if (nextId == "values") {
                    compose.activity.onBackPressedDispatcher.onBackPressed()
                    compose.waitUntil(10_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.test_saved)).fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText(compose.activity.getString(R.string.test_leave)).performClick()
                    compose.onNodeWithText(compose.activity.getString(R.string.continue_test)).performClick()
                    compose.waitUntil(10_000) { compose.onAllNodesWithTag("test_start_or_continue").fetchSemanticsNodes().any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) } }
                    compose.onNodeWithTag("test_start_or_continue").performClick()
                    compose.waitForIdle()
                    assertTrue("Continue did not open values; room=${runBlocking { repository.continueTest()?.first?.currentScreen }}\n${compose.onRoot().printToString()}",
                        compose.onAllNodesWithTag("question_values").fetchSemanticsNodes().isNotEmpty())
                    assertEquals("values", runBlocking { repository.continueTest()!!.first.currentScreen })
                }
            }
        }

        val result = Json.decodeFromString<ScoringResult>(requireNotNull(runBlocking { repository.result(sessionId) }).payload)
        assertEquals(listOf(80, 65, 56), result.spheres.take(3).map { it.percent })
        assertEquals(listOf("it", "finance", "engineering"), result.topSpheres)
        val saved = requireNotNull(runBlocking { repository.result(sessionId) })
        assertEquals(config.version.toString(), saved.configVersion)
        assertEquals("completed", runBlocking { testRepository.session(sessionId) }?.status)
        assertNull(runBlocking { repository.continueTest() })
        assertEquals(expectedOrder, visited)
        assertTrue(visited.containsAll(listOf("needs/ratings", "needs/pick3")))
        assertTrue("Expected RIASEC A to be stored", interestRows.values.any { it == "A" })
    }

    private fun expectedScreens(config: TestConfig, seed: Long): List<String> {
        val random = Random(seed)
        return config.parts.flatMap { part ->
            val questions = if (part.id == "interests") part.questions.shuffled(random) else part.questions
            when (part.id) {
                "profile", "values" -> listOf(part.id)
                "needs" -> requireNotNull(part.steps).map { "needs/${it.id}" }
                else -> questions.map { question ->
                    if (question.type == "pair") listOf(requireNotNull(question.type_a), requireNotNull(question.type_b)).shuffled(random)
                    question.id
                }
            }
        }
    }

    private fun expectedPairOrder(config: TestConfig, seed: Long): Map<String, List<String>> {
        val random = Random(seed)
        return buildMap {
            config.parts.forEach { part ->
                val questions = if (part.id == "interests") part.questions.shuffled(random) else part.questions
                questions.filter { it.type == "pair" }.forEach { question ->
                    put(question.id, listOf(requireNotNull(question.type_a), requireNotNull(question.type_b)).shuffled(random))
                }
            }
        }
    }

    private fun assertPairOrder(id: String, types: List<String>) {
        val first = compose.onNodeWithTag("choice_${types.first()}").fetchSemanticsNode().boundsInRoot.top
        val second = compose.onNodeWithTag("choice_${types.last()}").fetchSemanticsNode().boundsInRoot.top
        assertTrue("RIASEC side order changed for $id", first < second)
    }

    private fun partFor(id: String, config: TestConfig): String = when {
        id == "profile" || id == "values" -> id
        id.startsWith("needs/") -> "needs"
        else -> config.parts.first { part -> part.questions.any { it.id == id } }.id
    }

    private fun clickTag(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun enterText(tag: String, value: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextInput(value)
    }
}

