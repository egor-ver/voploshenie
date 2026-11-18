package ru.voploshchenie.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.voploshchenie.app.data.local.asset.ConfigLoader
import ru.voploshchenie.app.domain.model.*
import ru.voploshchenie.app.domain.scoring.ScoringEngine
import ru.voploshchenie.app.domain.usecase.CatalogSelection
import ru.voploshchenie.app.domain.usecase.resultName
import ru.voploshchenie.app.domain.usecase.scoringAnswers
import ru.voploshchenie.app.ui.opportunities.*
import ru.voploshchenie.app.ui.result.*
import ru.voploshchenie.app.ui.saved.*
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class A04ViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var nextKey = 0
    private val config by lazy { ConfigLoader.decode(File("src/main/assets/test_config.json").readText()) }
    private val answers by lazy { scoringAnswers(Json.decodeFromString<Map<String, String>>(File("src/main/assets/demo_answers.json").readText()), config) }
    private val score by lazy { ScoringEngine.compute(answers, config) }
    private val catalog by lazy { ru.voploshchenie.app.data.local.asset.CatalogLoader.parse(File("src/main/assets/catalog.json").readText()) }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }
    private fun <T : ViewModel> keep(vm: T): T { store.put((nextKey++).toString(), vm); return vm }
    private fun resultRow() = SavedTestResult("session", 123, config.version.toString(), Json.encodeToString(score), score.mismatches)
    private fun opportunities(source: FakeCatalog = FakeCatalog(catalog), results: FakeResults = FakeResults(), saved: FakeSaved = FakeSaved(),
        handle: SavedStateHandle = SavedStateHandle()) = keep(OpportunitiesViewModel(source, results, saved, config, handle))
    private fun content(vm: OpportunitiesViewModel) = vm.state.value as OpportunitiesUiState.Content

    @Test fun resultLoadsRequestedSessionAndLegacyAnswers() = runTest(dispatcher) {
        val results = FakeResults().apply {
            row = resultRow()
            rows = Json.decodeFromString<Map<String, String>>(File("src/main/assets/demo_answers.json").readText())
                .map { (id, value) -> StoredTestAnswer("session", id, value, 0) }
        }
        val vm = keep(ResultViewModel(results, config, SavedStateHandle(mapOf("sessionId" to "session"))))
        assertEquals(ResultUiState.Loading, vm.state.value)
        advanceUntilIdle()
        val state = vm.state.value as ResultUiState.Content
        assertEquals("session", results.requestedSession)
        assertEquals(score, state.result)
        assertEquals(answers, state.answers)
        assertEquals(resultName(results.rows, answers), state.name)
        assertEquals(123L, state.computedAt)
    }

    @Test fun missingResultCanBeRetriedAndMalformedPayloadIsError() = runTest(dispatcher) {
        val results = FakeResults()
        val vm = keep(ResultViewModel(results, config, SavedStateHandle(mapOf("sessionId" to "session"))))
        advanceUntilIdle()
        assertEquals(ResultUiState.Error, vm.state.value)
        results.row = resultRow()
        vm.retry(); advanceUntilIdle()
        assertEquals(score, (vm.state.value as ResultUiState.Content).result)
        results.row = resultRow().copy(payload = "invalid")
        vm.retry(); advanceUntilIdle()
        assertEquals(ResultUiState.Error, vm.state.value)
    }

    @Test fun resultRepositoryFailureCanBeRetried() = runTest(dispatcher) {
        val results = FakeResults().apply { fail = true; row = resultRow() }
        val vm = keep(ResultViewModel(results, config, SavedStateHandle(mapOf("sessionId" to "session"))))
        advanceUntilIdle()
        assertEquals(ResultUiState.Error, vm.state.value)
        results.fail = false
        vm.retry(); advanceUntilIdle()
        assertTrue(vm.state.value is ResultUiState.Content)
    }

    @Test fun queryAndTypeCombineAndResetRestoresAllItems() = runTest(dispatcher) {
        val vm = opportunities()
        assertEquals(OpportunitiesUiState.Loading, vm.state.value)
        advanceUntilIdle()
        vm.filter(1)
        assertTrue(content(vm).items.isNotEmpty())
        assertTrue(content(vm).items.all { it.type == "olympiad" })
        val target = content(vm).items.first()
        vm.search(" ${target.title.uppercase()} ")
        assertEquals(listOf(target.id), content(vm).items.map { it.id })
        vm.search("∅∅∅")
        assertTrue(content(vm).items.isEmpty())
        vm.reset()
        assertEquals(catalog.map { it.id }.toSet(), content(vm).items.map { it.id }.toSet())
        assertEquals("", content(vm).query)
        assertEquals(0, content(vm).type)
    }

    @Test fun searchAndTypeSurviveViewModelRecreation() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val vm = opportunities(handle = handle)
        advanceUntilIdle()
        vm.filter(1); vm.search("олимпиада"); vm.sort(false)
        val restored = opportunities(handle = SavedStateHandle(mapOf("query" to handle.get<String>("query"),
            "type" to handle.get<Int>("type"), "interests" to handle.get<Boolean>("interests"))))
        advanceUntilIdle()
        assertEquals(content(vm), content(restored))
    }

    @Test fun sortingRequiresResultAndKeepsSearchAndType() = runTest(dispatcher) {
        val vm = opportunities()
        advanceUntilIdle()
        vm.filter(1); vm.search("олимпиада"); vm.showSort()
        assertTrue(content(vm).sorting)
        vm.sort(true)
        assertFalse(content(vm).interests)
        assertFalse(content(vm).sorting)
        assertEquals(1, content(vm).type)
        assertEquals("олимпиада", content(vm).query)
        vm.filter(-1)
        assertEquals(1, content(vm).type)
    }

    @Test fun recommendationsIncludeWholeCatalogAndRetainFallbackLabelsWhenFiltered() = runTest(dispatcher) {
        val results = FakeResults().apply { row = resultRow().copy(payload = Json.encodeToString(score.copy(topSpheres = listOf("biology", "finance", "it")))) }
        val vm = opportunities(results = results, handle = SavedStateHandle(mapOf("recommendationsOnly" to true)))
        advanceUntilIdle()
        val state = content(vm)
        assertTrue(state.recommendationsOnly)
        assertEquals(catalog.map { it.id }.toSet(), state.items.map { it.id }.toSet())
        assertTrue(state.recommendations.any { it.fromSphere != null })
        assertEquals(state.recommendations.map { it.item.id }, state.items.map { it.id })
        vm.filter(1)
        assertEquals(state.recommendations.filter { it.item.type == "olympiad" }.map { it.item.id }, content(vm).items.map { it.id })
        vm.reset()
        assertEquals(state.items, content(vm).items)
    }

    @Test fun dateSortReordersRecommendationsWithoutChangingThem() = runTest(dispatcher) {
        val results = FakeResults().apply { row = resultRow() }
        val vm = opportunities(results = results, handle = SavedStateHandle(mapOf("recommendationsOnly" to true)))
        advanceUntilIdle()
        val byInterests = content(vm).items
        vm.sort(false)
        val byDate = content(vm).items
        assertEquals(byInterests.map { it.id }.toSet(), byDate.map { it.id }.toSet())
        assertEquals(CatalogSelection(catalog).byNearestDate(java.time.LocalDate.now(), byInterests).map { it.id }, byDate.map { it.id })
        vm.sort(true)
        assertEquals(byInterests, content(vm).items)
    }

    @Test fun recommendationsWithoutResultAreEmpty() = runTest(dispatcher) {
        val vm = opportunities(handle = SavedStateHandle(mapOf("recommendationsOnly" to true)))
        advanceUntilIdle()
        assertTrue(content(vm).items.isEmpty())
        assertNull(content(vm).result)
    }

    @Test fun profileCourseAndConstraintLoadFromLegacyAndCurrentAnswerRows() = runTest(dispatcher) {
        val legacy = FakeResults().apply {
            row = resultRow()
            rows = listOf(
                StoredTestAnswer("session", "profile", Json.encodeToString(mapOf(
                    "PRF02" to "3 курс бакалавриата/специалитета", "PRF04" to " Казань ", "PRF06" to "ищу стажировку или практику")), 0),
                StoredTestAnswer("session", "NAD02", "b", 0),
            )
        }
        val oldState = SavedStateHandle(mapOf("recommendationsOnly" to true))
        val oldVm = opportunities(results = legacy, handle = oldState)
        advanceUntilIdle()
        assertEquals("3 курс бакалавриата/специалитета", content(oldVm).profileCourse)
        assertEquals(" Казань ", content(oldVm).profileCity)
        assertEquals("b", content(oldVm).locationPreference)

        val current = FakeResults().apply {
            row = resultRow()
            rows = listOf(
                StoredTestAnswer("session", "__format", "\"T02\"", 0),
                StoredTestAnswer("session", "PRF02", Json.encodeToString("2 курс магистратуры"), 0),
                StoredTestAnswer("session", "PRF04", Json.encodeToString("Омск"), 0),
                StoredTestAnswer("session", "NAD02", Json.encodeToString("a"), 0),
            )
        }
        val newVm = opportunities(results = current, handle = SavedStateHandle(mapOf("recommendationsOnly" to true)))
        advanceUntilIdle()
        assertEquals("2 курс магистратуры", content(newVm).profileCourse)
        assertEquals("Омск", content(newVm).profileCity)
        assertEquals("a", content(newVm).locationPreference)
        val recommendations = content(newVm).recommendations.map { it.item }
        assertEquals(recommendations.map { it.id }.toSet(), content(newVm).items.map { it.id }.toSet())
        newVm.sort(false)
        assertEquals(CatalogSelection(catalog).byNearestDate(java.time.LocalDate.now(), recommendations).map { it.id }, content(newVm).items.map { it.id })
        newVm.sort(true)
        assertEquals(recommendations.map { it.id }.toSet(), content(newVm).items.map { it.id }.toSet())
    }


    @Test fun masterCoursePrioritizesExplicitMatchAndPreservesUnmatchedOrder() = runTest(dispatcher) {
        val sphere = score.topSpheres.first()
        val candidates = listOf(
            catalog.first().copy(id = "unparsed-first", spheres = listOf(sphere), audience_text = "\u0414\u043b\u044f \u0441\u0442\u0443\u0434\u0435\u043d\u0442\u043e\u0432",
                dates = listOf(CatalogDate("apply_deadline", "2027-01-01", "day"))),
            catalog.first().copy(id = "master-match", spheres = listOf(sphere), audience_text = "2 \u043a\u0443\u0440\u0441 \u043c\u0430\u0433\u0438\u0441\u0442\u0440\u0430\u0442\u0443\u0440\u044b",
                dates = listOf(CatalogDate("apply_deadline", "2027-02-01", "day"))),
            catalog.first().copy(id = "undergrad", spheres = listOf(sphere), audience_text = "2 \u043a\u0443\u0440\u0441 \u0431\u0430\u043a\u0430\u043b\u0430\u0432\u0440\u0438\u0430\u0442\u0430/\u0441\u043f\u0435\u0446\u0438\u0430\u043b\u0438\u0442\u0435\u0442\u0430",
                dates = listOf(CatalogDate("apply_deadline", "2027-03-01", "day"))),
        )
        val results = FakeResults().apply {
            row = resultRow().copy(payload = Json.encodeToString(score.copy(topSpheres = listOf(sphere))))
            rows = listOf(StoredTestAnswer("session", "__format", "\"T02\"", 0),
                StoredTestAnswer("session", "PRF02", Json.encodeToString("2 \u043a\u0443\u0440\u0441 \u043c\u0430\u0433\u0438\u0441\u0442\u0440\u0430\u0442\u0443\u0440\u044b"), 0))
        }
        val vm = opportunities(source = FakeCatalog(candidates), results = results,
            handle = SavedStateHandle(mapOf("recommendationsOnly" to true)))
        advanceUntilIdle()
        assertEquals(listOf("master-match", "unparsed-first", "undergrad"), content(vm).items.map { it.id })
        vm.sort(false)
        assertEquals(listOf("unparsed-first", "master-match", "undergrad"), content(vm).items.map { it.id })
        vm.sort(true)
        assertEquals(listOf("master-match", "unparsed-first", "undergrad"), content(vm).items.map { it.id })
    }

    @Test fun malformedSavedProfileIsReportedInsteadOfIgnored() = runTest(dispatcher) {
        val results = FakeResults().apply {
            row = resultRow()
            rows = listOf(StoredTestAnswer("session", "__format", "\"T02\"", 0),
                StoredTestAnswer("session", "PRF02", "{", 0))
        }
        val vm = opportunities(results = results)
        advanceUntilIdle()
        assertEquals(OpportunitiesUiState.Error, vm.state.value)
    }

    @Test fun similarItemsExcludeSourceAndRequireSameTypeAndSphere() = runTest(dispatcher) {
        val item = catalog.first()
        val vm = opportunities(handle = SavedStateHandle(mapOf("similarTo" to item.id)))
        advanceUntilIdle()
        assertEquals(catalog.filter { it.id != item.id && it.type == item.type && it.spheres.any(item.spheres::contains) }, content(vm).items)
        assertFalse(content(vm).items.any { it.id == item.id })
    }

    @Test fun catalogFailureRetriesWithoutLosingFilters() = runTest(dispatcher) {
        val source = FakeCatalog(catalog).apply { fail = true }
        val vm = opportunities(source = source, handle = SavedStateHandle(mapOf("query" to "олимпиада", "type" to 1)))
        advanceUntilIdle()
        assertEquals(OpportunitiesUiState.Error, vm.state.value)
        source.fail = false
        vm.retry(); advanceUntilIdle()
        assertEquals("олимпиада", content(vm).query)
        assertTrue(content(vm).items.isNotEmpty())
        assertTrue(content(vm).items.all { it.type == "olympiad" && it.title.contains("олимпиада", true) })
    }

    @Test fun savingAndRemovingUpdateCatalogThroughRepositoryFlow() = runTest(dispatcher) {
        val saved = FakeSaved()
        val vm = opportunities(saved = saved)
        advanceUntilIdle()
        val id = content(vm).items.first().id
        vm.toggleSaved(id); advanceUntilIdle()
        assertEquals(setOf(id), content(vm).savedIds)
        assertEquals(id, saved.items.value.single().item_id)
        vm.toggleSaved(id); advanceUntilIdle()
        assertTrue(content(vm).savedIds.isEmpty())
        assertTrue(saved.items.value.isEmpty())
        saved.fail = true
        vm.toggleSaved(id); advanceUntilIdle()
        assertEquals(OpportunitiesUiState.Error, vm.state.value)
    }

    @Test fun detailObservesSavedStatusAndComputesMatch() = runTest(dispatcher) {
        val item = catalog.first()
        val saved = FakeSaved()
        val results = FakeResults().apply { row = resultRow() }
        val vm = keep(OpportunityDetailViewModel(FakeCatalog(catalog), results, saved, SavedStateHandle(mapOf("itemId" to item.id))))
        assertEquals(OpportunityDetailUiState.Loading, vm.state.value)
        advanceUntilIdle()
        val content = vm.state.value as OpportunityDetailUiState.Content
        assertEquals(item, content.item)
        assertEquals(score.spheres.filter { it.id in item.spheres }.maxOfOrNull { it.percent } ?: 0, content.matchPercent)
        vm.toggleSaved(); advanceUntilIdle()
        assertTrue((vm.state.value as OpportunityDetailUiState.Content).saved)
        vm.toggleSaved(); advanceUntilIdle()
        assertFalse((vm.state.value as OpportunityDetailUiState.Content).saved)
    }

    @Test fun missingDetailCanBeRetriedWhenCatalogChanges() = runTest(dispatcher) {
        val item = catalog.first()
        val source = FakeCatalog()
        val vm = keep(OpportunityDetailViewModel(source, FakeResults(), FakeSaved(), SavedStateHandle(mapOf("itemId" to item.id))))
        advanceUntilIdle()
        assertEquals(OpportunityDetailUiState.Error, vm.state.value)
        source.items = listOf(item)
        vm.retry(); advanceUntilIdle()
        assertEquals(item, (vm.state.value as OpportunityDetailUiState.Content).item)
        assertNull((vm.state.value as OpportunityDetailUiState.Content).matchPercent)
    }

    @Test fun savedObservesOrderAndKeepsUnavailableEntries() = runTest(dispatcher) {
        val saved = FakeSaved()
        val vm = keep(SavedViewModel(FakeCatalog(catalog.take(1)), saved))
        assertEquals(SavedUiState.Loading, vm.state.value)
        advanceUntilIdle()
        assertTrue((vm.state.value as SavedUiState.Content).saved.isEmpty())
        val rows = catalog.take(2).reversed().map { SavedItem(it.id, 0) }
        saved.items.value = rows
        advanceUntilIdle()
        val state = vm.state.value as SavedUiState.Content
        assertEquals(rows, state.saved)
        assertEquals(catalog.take(1), state.items)
        saved.remove(rows.first().item_id)
        advanceUntilIdle()
        assertEquals(rows.drop(1), (vm.state.value as SavedUiState.Content).saved)
    }

    @Test fun savedCatalogFailureCanBeRetried() = runTest(dispatcher) {
        val source = FakeCatalog(catalog).apply { fail = true }
        val vm = keep(SavedViewModel(source, FakeSaved()))
        advanceUntilIdle()
        assertEquals(SavedUiState.Error, vm.state.value)
        source.fail = false
        vm.retry(); advanceUntilIdle()
        assertEquals(catalog, (vm.state.value as SavedUiState.Content).items)
    }
}
