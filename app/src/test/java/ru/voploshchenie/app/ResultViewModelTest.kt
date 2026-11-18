package ru.voploshchenie.app

import ru.voploshchenie.app.domain.model.StoredTestAnswer
import ru.voploshchenie.app.domain.model.TestSessionState
import ru.voploshchenie.app.domain.scoring.TestAnswers
import ru.voploshchenie.app.domain.scoring.ExperienceAnswer
import ru.voploshchenie.app.domain.scoring.ScoringEngine
import ru.voploshchenie.app.domain.usecase.scoringAnswers
import ru.voploshchenie.app.domain.usecase.resultAnswers
import ru.voploshchenie.app.ui.result.ResultSummary
import ru.voploshchenie.app.ui.theme.AppTheme
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-mdpi")
class ResultViewModelTest {
    private val config by lazy { ru.voploshchenie.app.data.local.asset.ConfigLoader.load(compose.activity) }
    private val database by lazy {
        androidx.room.Room.inMemoryDatabaseBuilder(compose.activity,
            ru.voploshchenie.app.data.local.db.TestDatabase::class.java).build()
    }
    private val repository by lazy {
        ru.voploshchenie.app.data.repository.RoomTestSessionRepository(database.testDao(), config.version.toString(), config)
    }
    @org.junit.After fun closeDatabase() { database.close() }

    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun demo() = compose.activity.assets.open("demo_answers.json").bufferedReader().use {
        scoringAnswers(Json.decodeFromString<Map<String, String>>(it.readText()), config)
    }
    private val state = LazyListState()

    private fun show(answers: TestAnswers) {
        val result = ScoringEngine.compute(answers, config)
        compose.runOnUiThread { compose.activity.setContent { AppTheme { ResultSummary(result, answers, config, state) } } }
        compose.waitForIdle()
    }

    private fun shown(text: String) {
        compose.onNodeWithTag("result").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertExists()
    }

    private fun absentEverywhere(vararg texts: String) {
        for (index in 0 until state.layoutInfo.totalItemsCount) {
            compose.onNodeWithTag("result").performScrollToIndex(index)
            texts.forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        }
    }

    @Test fun energyThreeAndBlankOpenAreHidden() {
        show(demo().copy(experience = mapOf("EXP01" to ExperienceAnswer("низкая энергия", 3)),
            open = mapOf("OPEN01" to "", "OPEN04" to " \n ")))
        absentEverywhere("Что вас заряжает", "низкая энергия", "Ваши слова")
    }

    @Test fun energyFourAndFiveAndAllNonblankOpenAreShown() {
        show(demo().copy(experience = mapOf(
            "EXP01" to ExperienceAnswer("энергия четыре", 4),
            "EXP02" to ExperienceAnswer("энергия пять", 5),
            "EXP03" to ExperienceAnswer("энергия три", 3)),
            open = mapOf("OPEN01" to "слова первого", "OPEN04" to "слова четвёртого", "OPEN06" to "слова шестого", "OPEN05" to " ")))
        shown("Что вас заряжает")
        shown("энергия четыре")
        shown("энергия пять")
        shown("Ваши слова")
        shown("слова первого")
        shown("слова четвёртого")
        shown("слова шестого")
        absentEverywhere("энергия три", config.questions.getValue("happiness").first { it.id == "OPEN05" }.fields.getValue("Текст"))
    }

    private fun mismatches(count: Int): TestAnswers {
        val answers = demo()
        val checks = config.questions.getValue("consistency")
        return answers.copy(checks = checks.mapIndexed { index, question ->
            val original = answers.pairs.getValue(requireNotNull(question.linked))
            question.id to if (index < count) {
                if (original == question.type_a) requireNotNull(question.type_b) else requireNotNull(question.type_a)
            } else original
        }.toMap())
    }

    @Test fun oneMismatchDoesNotWarn() {
        val answers = mismatches(1)
        show(answers)
        val warning = config.analyst.getValue("consistency_warning").jsonPrimitive.content
        absentEverywhere(warning)
    }

    @Test fun consistencyBlockIsHiddenWithoutWarningOrProfileSummary() {
        show(mismatches(1).copy(profile = emptyMap()))
        absentEverywhere("Согласованность")
    }

    @Test fun changeDirectionMovesConsistencyBeforeTopSpheres() {
        val base = demo()
        val answers = base.copy(profile = base.profile + ("PRF06" to JsonPrimitive("хочу сменить направление")))
        val result = ScoringEngine.compute(answers, config)
        compose.activity.setContent { AppTheme { ResultSummary(result, answers, config, LazyListState()) } }
        compose.waitForIdle()
        val consistency = compose.onNodeWithTag("result_consistency_heading").fetchSemanticsNode().boundsInRoot.top
        val sphere = compose.onNodeWithTag("result_sphere_${result.topSpheres.first()}").fetchSemanticsNode().boundsInRoot.top
        assertTrue(consistency < sphere)
        val specialty = answers.profile.getValue("PRF03").jsonPrimitive.content
        compose.onNodeWithTag("result").performScrollToNode(hasText(specialty, substring = true))
        compose.onNodeWithText(specialty, substring = true).assertExists()
    }

    @Test fun specialtyRatingChangesConsistencySummary() {
        val base = demo()
        val low = base.copy(profile = base.profile + ("PRF05" to JsonPrimitive("2")))
        val high = base.copy(profile = base.profile + ("PRF05" to JsonPrimitive("9")))
        val result = ScoringEngine.compute(base, config)
        val topSpheres = result.topSpheres.joinToString(", ") { compose.activity.getString(ru.voploshchenie.app.ui.result.sphereResource(it)) }
        val specialty = base.profile.getValue("PRF03").jsonPrimitive.content
        val expectedLow = compose.activity.getString(R.string.result_specialty_consistency, specialty, "2", topSpheres)
        val expectedHigh = compose.activity.getString(R.string.result_specialty_consistency, specialty, "9", topSpheres)
        compose.activity.setContent { AppTheme { ResultSummary(result, low, config, LazyListState()) } }
        compose.onNodeWithTag("result").performScrollToNode(hasText(specialty, substring = true))
        compose.onNodeWithText(expectedLow).assertExists()
        compose.activity.setContent { AppTheme { ResultSummary(result, high, config, LazyListState()) } }
        compose.onNodeWithTag("result").performScrollToNode(hasText(specialty, substring = true))
        compose.onNodeWithText(expectedHigh).assertExists()
        compose.onNodeWithText(expectedLow).assertDoesNotExist()
    }

    @Test fun internshipRequestOpensRecommendationsWithInternshipFilter() {
        val answers = demo().copy(profile = demo().profile + ("PRF06" to JsonPrimitive("ищу стажировку или практику")))
        val result = ScoringEngine.compute(answers, config)
        var internshipFilter = false
        compose.activity.setContent {
            AppTheme { ResultSummary(result, answers, config, LazyListState(), onOpportunities = { internshipFilter = it }) }
        }
        compose.onNodeWithTag("result_open_opportunities").performClick()
        assertTrue(internshipFilter)
    }

    @Test fun twoMismatchesWarnAndTaskSolutionsAreAbsent() {
        val answers = mismatches(2)
        show(answers)
        shown(requireNotNull(ScoringEngine.compute(answers, config).consistencyWarning))
        absentEverywhere(*config.questions.getValue("tasks").flatMap { q ->
            listOf(q.fields.getValue("Задание и варианты"), q.options.first { it.id == q.correct }.text)
        }.toTypedArray())
    }

    @Test fun valuesConditionsNadAndEntryUseAnswerTexts() {
        val answers = demo().copy(nad = demo().nad + ("NAD07" to "свободный ответ НАДО"))
        show(answers)
        shown(requireNotNull(ScoringEngine.compute(answers, config).anchor))
        answers.valuesTop3.forEach { id -> shown(config.questions.getValue("values").first { it.id == id }.fields.getValue("Название")) }
        answers.needs.top3.forEach { id -> shown(config.questions.getValue("needs").first { it.id == id }.fields.getValue("Условие")) }
        shown("свободный ответ НАДО")
        config.texts.getValue("spheres").jsonObject.getValue("table").jsonArray[2].jsonObject.getValue("Вход и первые пробы").jsonPrimitive.content.let(::shown)
    }

    @Test fun finishedSessionAnswersReadBothStorageFormats() = runBlocking {
        val raw = compose.activity.assets.open("demo_answers.json").bufferedReader().use { Json.decodeFromString<Map<String, String>>(it.readText()) }
        fun rows(values: Map<String, String>) = values.map { (id, value) -> StoredTestAnswer("session", id, value, 0) }
        val answers = resultAnswers(rows(raw), config)
        assertEquals(demo(), answers)
        val sessionRepository = repository
        val session = sessionRepository.save(
            TestSessionState(1, answers, currentScreenId = "CHK05"))
        assertEquals(answers, requireNotNull(sessionRepository.load()).answers)
        repository.finish(requireNotNull(session.sessionId), "{}", 0)
        val loaded = resultAnswers(repository.answers(session.sessionId), config)
        assertEquals(answers, loaded)
    }
}

