package ru.voploshchenie.app

import ru.voploshchenie.app.data.local.asset.ConfigLoader
import ru.voploshchenie.app.domain.model.TestSession
import ru.voploshchenie.app.domain.model.StoredTestAnswer
import ru.voploshchenie.app.domain.model.SavedTestResult
import ru.voploshchenie.app.domain.model.TestSessionState
import ru.voploshchenie.app.domain.repository.TestSessionRepository
import ru.voploshchenie.app.domain.scoring.isScreenComplete
import ru.voploshchenie.app.domain.scoring.TestAnswers
import ru.voploshchenie.app.domain.scoring.ExperienceAnswer
import ru.voploshchenie.app.domain.scoring.NeedsAnswers
import ru.voploshchenie.app.domain.scoring.ScoringEngine
import ru.voploshchenie.app.ui.quiz.TestProgress
import ru.voploshchenie.app.ui.quiz.QuizViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuizViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val config by lazy { ConfigLoader.decode(File("src/main/assets/test_config.json").readText()) }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun splitsAllPartsAndKeepsTaskOrderAndOptions() = runTest(dispatcher) {
        val vm = QuizViewModel(config, FakeRepository(), resumeCurrentScreen = true) { 73 }
        advanceUntilIdle()
        val screens = vm.state.value.screens
        assertEquals(66, screens.size)
        assertEquals(config.parts.map { it.id }, screens.map { it.part }.distinct())
        assertEquals(6, screens.single { it.part == "profile" }.questions.size)
        assertEquals(10, screens.single { it.part == "values" }.questions.size)
        assertEquals(listOf("ratings", "pick3"), screens.filter { it.part == "needs" }.map { it.step?.id })
        assertEquals(8, screens.single { it.id == "needs/ratings" }.questions.size)
        val tasks = screens.filter { it.part == "tasks" }.flatMap { it.questions }
        assertEquals(config.questions.getValue("tasks"), tasks)
        assertTrue(screens.filter { it.part !in listOf("profile", "values", "needs") }.all { it.questions.size == 1 })
        assertEquals(TestProgress("profile", 1, 1, 0), vm.state.value.progress)
        assertFalse(vm.state.value.canBack)
        vm.back()
        advanceUntilIdle()
        assertEquals(0, vm.state.value.index)
    }

    @Test fun nextIsBlockedAndBackPersistsEditedAnswers() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = QuizViewModel(config, repo, resumeCurrentScreen = true) { 73 }
        advanceUntilIdle()
        assertFalse(vm.state.value.canNext)
        vm.next()
        advanceUntilIdle()
        assertEquals("profile", vm.state.value.screen?.id)
        vm.updateAnswers { it.copy(profile = profile()) }
        vm.next()
        advanceUntilIdle()
        val pair = requireNotNull(vm.state.value.screen)
        assertFalse(vm.state.value.canNext)
        vm.next()
        advanceUntilIdle()
        assertEquals(pair, vm.state.value.screen)
        vm.selectPair(0)
        advanceUntilIdle()
        val savesBeforeBack = repo.saves
        vm.back()
        advanceUntilIdle()
        assertEquals("profile", vm.state.value.screen?.id)
        assertEquals(pair.pairTypes[0], repo.load()?.answers?.pairs?.get(pair.id))
        assertEquals(0, vm.state.value.index)
        assertEquals(savesBeforeBack + 1, repo.saves)
    }

    @Test fun recreationUsesStoredSeedOrderAndCurrentScreen() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = QuizViewModel(config, repo, resumeCurrentScreen = true) { 73 }
        advanceUntilIdle()
        vm.updateAnswers { it.copy(profile = profile()) }
        vm.next()
        advanceUntilIdle()
        val firstPair = requireNotNull(vm.state.value.screen)
        vm.selectPair(1)
        vm.next()
        advanceUntilIdle()
        val before = vm.state.value
        val restored = QuizViewModel(config, repo, resumeCurrentScreen = true) { error("Seed must be loaded") }
        advanceUntilIdle()
        assertEquals(before.screen, restored.state.value.screen)
        assertEquals(before.screens, restored.state.value.screens)
        assertEquals(73L, restored.state.value.session?.shuffle_seed)
        assertEquals(firstPair.pairTypes[1], restored.state.value.session?.answers?.pairs?.get(firstPair.id))
        assertEquals(TestProgress("interests", 2, 15, 3), restored.state.value.progress)
        assertEquals(config.questions.getValue("interests").map { it.id }.toSet(),
            before.screens.filter { it.part == "interests" }.map { it.id }.toSet())
        val other = QuizViewModel(config, FakeRepository(), resumeCurrentScreen = true) { 74 }
        advanceUntilIdle()
        assertNotEquals(before.screens.filter { it.part == "interests" }, other.state.value.screens.filter { it.part == "interests" })
    }

    @Test fun recreationKeepsAnsweredPairBeforeNextAndAfterBack() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = QuizViewModel(config, repo, resumeCurrentScreen = true) { 73 }
        advanceUntilIdle()
        vm.updateAnswers { it.copy(profile = profile()) }
        vm.next()
        vm.selectPair(1)
        advanceUntilIdle()
        val answered = vm.state.value

        val restored = QuizViewModel(config, repo, resumeCurrentScreen = true) { error("Seed must be loaded") }
        advanceUntilIdle()
        assertEquals(answered.screen, restored.state.value.screen)
        assertEquals(answered.screens, restored.state.value.screens)
        assertEquals(answered.progress, restored.state.value.progress)
        assertEquals(answered.screen?.pairTypes?.get(1),
            restored.state.value.session?.answers?.pairs?.get(answered.screen?.id))
        assertTrue(restored.state.value.canNext)

        restored.next()
        restored.back()
        advanceUntilIdle()
        val afterBack = QuizViewModel(config, repo, resumeCurrentScreen = true) { error("Seed must be loaded") }
        advanceUntilIdle()
        assertEquals(answered.screen, afterBack.state.value.screen)
        assertEquals(answered.screens, afterBack.state.value.screens)
        assertEquals(answered.session?.answers, afterBack.state.value.session?.answers)
        assertTrue(afterBack.state.value.canNext)
        assertFalse(afterBack.state.value.finished)
    }

    @Test fun profileOtherRequestRequiresExplanationWithinLimit() = runTest(dispatcher) {
        val vm = QuizViewModel(config, FakeRepository(), resumeCurrentScreen = true) { 73 }
        advanceUntilIdle()
        val request = config.parts.first().questions.single { it.id == "PRF06" }.options.last().id
        for (other in listOf(request, "другое")) {
            for ((explanation, complete) in listOf(
                null to false, "" to false, "   " to false,
                "x" to true, "x".repeat(200) to true, "x".repeat(201) to false,
            )) {
                vm.updateAnswers { it.copy(profile = profile() + ("PRF06" to JsonPrimitive(other)) +
                    (explanation?.let { mapOf("PRF06_other" to JsonPrimitive(it)) } ?: emptyMap())) }
                advanceUntilIdle()
                assertEquals(complete, vm.state.value.canNext)
                assertEquals(complete, isScreenComplete("profile", requireNotNull(vm.state.value.session).answers))
                if (!complete) {
                    vm.next()
                    advanceUntilIdle()
                    assertEquals("profile", vm.state.value.screen?.id)
                }
            }
        }
        vm.updateAnswers { it.copy(profile = profile()) }
        advanceUntilIdle()
        assertTrue(vm.state.value.canNext)
        vm.next()
        advanceUntilIdle()
        assertEquals("interests", vm.state.value.screen?.part)
    }

    @Test fun optionalTextMustBeVisitedAndPartialExperienceBlocksNext() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = QuizViewModel(config, repo, resumeCurrentScreen = true) { 73 }
        advanceUntilIdle()
        val screens = vm.state.value.screens
        val answers = completeAnswers()
        val open = screens.indexOfFirst { it.part == "open" }
        repo.save(TestSessionState(73, answers, screens.take(open).map { it.id }.toSet()))
        val restored = QuizViewModel(config, repo, resumeCurrentScreen = true)
        advanceUntilIdle()
        assertEquals("OPEN01", restored.state.value.screen?.id)
        assertTrue(restored.state.value.canNext)
        restored.next()
        advanceUntilIdle()
        val again = QuizViewModel(config, repo, resumeCurrentScreen = true)
        advanceUntilIdle()
        assertEquals("OPEN02", again.state.value.screen?.id)

        val experience = screens.indexOfFirst { it.part == "experience" }
        repo.save(TestSessionState(73, answers.copy(experience = mapOf("EXP01" to ExperienceAnswer("Кейс", 0))),
            screens.take(experience).map { it.id }.toSet()))
        val partial = QuizViewModel(config, repo, resumeCurrentScreen = true)
        advanceUntilIdle()
        assertEquals("EXP01", partial.state.value.screen?.id)
        assertFalse(partial.state.value.canNext)
        partial.updateAnswers { it.copy(experience = mapOf("EXP01" to ExperienceAnswer("Кейс", 5))) }
        advanceUntilIdle()
        assertTrue(partial.state.value.canNext)
    }

    @Test fun completionChecksCurrentQuestionAndBothNeedsSteps() = runTest(dispatcher) {
        val vm = QuizViewModel(config, FakeRepository(), resumeCurrentScreen = true) { 73 }
        advanceUntilIdle()
        val screens = vm.state.value.screens
        val sphere = screens.first { it.part == "sphere_interest" }
        assertTrue(sphere.isScreenComplete(TestAnswers(interest = mapOf(sphere.id to 1))))
        assertFalse(sphere.isScreenComplete(TestAnswers(interest = mapOf(sphere.id to 6))))
        val pair = screens.first { it.part == "interests" }
        assertFalse(pair.isScreenComplete(TestAnswers(pairs = mapOf(pair.id to "B"))))
        val task = screens.first { it.part == "tasks" }
        assertFalse(task.isScreenComplete(TestAnswers(tasks = mapOf(task.id to "unknown"))))
        val ratings = screens.single { it.id == "needs/ratings" }
        val pick = screens.single { it.id == "needs/pick3" }
        val answers = completeAnswers()
        assertTrue(ratings.isScreenComplete(answers.copy(needs = answers.needs.copy(top3 = emptyList()))))
        assertTrue(pick.isScreenComplete(answers.copy(needs = answers.needs.copy(ratings = emptyMap()))))
        assertFalse(ratings.isScreenComplete(answers.copy(needs = answers.needs.copy(ratings = answers.needs.ratings - "NEED08"))))
        assertFalse(pick.isScreenComplete(answers.copy(needs = answers.needs.copy(top3 = listOf("NEED01", "NEED01", "NEED02")))))
        assertFalse(pick.isScreenComplete(answers.copy(needs = answers.needs.copy(top3 = listOf("NEED01", "NEED02", "unknown")))))
        val text = screens.first { it.part == "happiness" }
        assertTrue(text.isScreenComplete(answers.copy(open = mapOf(text.id to "x".repeat(1000)))))
        assertFalse(text.isScreenComplete(answers.copy(open = mapOf(text.id to "x".repeat(1001)))))
    }

    @Test fun fullTraversalStoresRiasecTypesAndFinishesAtHundredPercent() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = QuizViewModel(config, repo, resumeCurrentScreen = true) { 73 }
        advanceUntilIdle()
        vm.updateAnswers { completeAnswers() }
        advanceUntilIdle()
        for (screen in vm.state.value.screens) {
            assertEquals(screen, vm.state.value.screen)
            if (screen.pairTypes.isNotEmpty()) vm.selectPair(0)
            vm.next()
            advanceUntilIdle()
        }
        assertTrue(vm.state.value.finished)
        assertFalse(vm.state.value.canNext)
        assertEquals(100, vm.state.value.progress?.percent)
        val saved = requireNotNull(repo.load())
        assertEquals(66, saved.visitedScreens.size)
        for (screen in vm.state.value.screens.filter { it.pairTypes.isNotEmpty() }) {
            val values = if (screen.part == "consistency") saved.answers.checks else saved.answers.pairs
            assertEquals(screen.pairTypes[0], values[screen.id])
        }
        val result = ScoringEngine.compute(saved.answers, config)
        assertEquals(15, result.riasec.values.sum())
        val expected = saved.answers.pairs.values.groupingBy { it }.eachCount()
        result.riasec.forEach { (type, count) -> assertEquals(expected[type] ?: 0, count) }
        val restored = QuizViewModel(config, repo, resumeCurrentScreen = true)
        advanceUntilIdle()
        assertTrue(restored.state.value.finished)
        assertEquals(vm.state.value.screen, restored.state.value.screen)
        // повторный вход в пройденный тест не должен упираться в неактивную «Показать результат»
        assertTrue(restored.state.value.canComplete)
    }

    @Test fun saveFailureDoesNotAdvanceAndCanBeRetried() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = QuizViewModel(config, repo, resumeCurrentScreen = true) { 73 }
        advanceUntilIdle()
        vm.updateAnswers { it.copy(profile = profile()) }
        advanceUntilIdle()
        repo.failSave = true
        vm.next()
        advanceUntilIdle()
        assertEquals(0, vm.state.value.index)
        assertNotNull(vm.state.value.error)
        assertTrue(requireNotNull(repo.load()).visitedScreens.isEmpty())
        repo.failSave = false
        vm.next()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.index)
        assertNull(vm.state.value.error)
    }

    private fun profile() = mapOf(
        "PRF02" to JsonPrimitive(config.parts.first().questions.first { it.id == "PRF02" }.options.first().id),
        "PRF03" to JsonPrimitive("Информатика"), "PRF04" to JsonPrimitive("Казань"),
        "PRF05" to JsonPrimitive(6),
        "PRF06" to JsonPrimitive(config.parts.first().questions.first { it.id == "PRF06" }.options.first().id),
    )

    private fun completeAnswers() = TestAnswers(
        profile = profile(),
        pairs = config.questions.getValue("interests").associate { it.id to requireNotNull(it.type_a) },
        checks = config.questions.getValue("consistency").associate { it.id to requireNotNull(it.type_a) },
        interest = config.questions.getValue("sphere_interest").associate { it.id to 3 },
        tasks = config.questions.getValue("tasks").associate { it.id to it.options.first().id },
        valuesTop3 = listOf("VAL01", "VAL02", "VAL03"), valuesLeast2 = listOf("VAL04", "VAL05"),
        nad = config.parts.first { it.id == "constraints" }.questions.filter { it.type == "single_choice" }
            .associate { it.id to it.options.first().id },
        needs = NeedsAnswers((1..8).associate { "NEED%02d".format(it) to 3 }, listOf("NEED01", "NEED02", "NEED03")),
    )

    private class FakeRepository : TestSessionRepository {
        override suspend fun start(configVersion: String, shuffleSeed: Long): TestSession = error("Unexpected start")
        override suspend fun saveScreen(sessionId: String, screen: String, answers: Map<String, String>, replaceAnswers: Boolean): Unit = error("Unexpected saveScreen")
        override suspend fun continueTest(): Pair<TestSession, List<StoredTestAnswer>>? = error("Unexpected continueTest")
        override suspend fun finish(sessionId: String, payload: String, mismatches: Int): SavedTestResult = error("Unexpected finish")
        override suspend fun latestResult(): SavedTestResult? = error("Unexpected latestResult")
        override suspend fun result(sessionId: String): SavedTestResult? = error("Unexpected result")
        override suspend fun answers(sessionId: String): List<StoredTestAnswer> = error("Unexpected answers")
        override suspend fun session(sessionId: String): TestSession? = error("Unexpected session")

        private var payload: String? = null
        var saves = 0
        var failSave = false
        override suspend fun load(): TestSessionState? = payload?.let { Json.decodeFromString<TestSessionState>(it) }
        override suspend fun load(sessionId: String): TestSessionState? = load()?.takeIf { it.sessionId == sessionId }
        override suspend fun save(session: TestSessionState): TestSessionState {
            if (failSave) error("Save failed")
            payload = Json.encodeToString(session)
            saves++
            return session
        }
    }
}

