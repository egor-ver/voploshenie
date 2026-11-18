package ru.voploshchenie.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ru.voploshchenie.app.domain.model.*
import ru.voploshchenie.app.domain.repository.*
import ru.voploshchenie.app.domain.usecase.CatalogSelection
import ru.voploshchenie.app.ui.opportunities.OpportunitiesUiState
import java.time.LocalDate

internal class FakeCatalog(var items: List<CatalogItem> = emptyList()) : CatalogRepository {
    var fail = false
    override suspend fun all(): List<CatalogItem> {
        check(!fail)
        return items
    }
}

internal class FakeSaved : SavedRepository {
    val items = MutableStateFlow<List<SavedItem>>(emptyList())
    var fail = false
    override fun observeAll() = items
    override fun observeSaved(id: String) = items.map { rows -> rows.any { it.item_id == id } }
    override suspend fun save(item: SavedItem) {
        check(!fail)
        items.value = items.value.filterNot { it.item_id == item.item_id } + item
    }
    override suspend fun remove(id: String) {
        check(!fail)
        items.value = items.value.filterNot { it.item_id == id }
    }
}

internal class FakeResults : TestSessionRepository {
    var row: SavedTestResult? = null
    var rows: List<StoredTestAnswer> = emptyList()
    var fail = false
    var requestedSession: String? = null
    override suspend fun latestResult(): SavedTestResult? { check(!fail); return row }
    override suspend fun result(sessionId: String): SavedTestResult? { requestedSession = sessionId; check(!fail); return row }
    override suspend fun answers(sessionId: String): List<StoredTestAnswer> { requestedSession = sessionId; return rows }
    override suspend fun load(): TestSessionState? = error("Unexpected load")
    override suspend fun load(sessionId: String): TestSessionState? = error("Unexpected load")
    override suspend fun save(session: TestSessionState): TestSessionState = error("Unexpected save")
    override suspend fun start(configVersion: String, shuffleSeed: Long): TestSession = error("Unexpected start")
    override suspend fun saveScreen(sessionId: String, screen: String, answers: Map<String, String>, replaceAnswers: Boolean): Unit = error("Unexpected saveScreen")
    override suspend fun continueTest(): Pair<TestSession, List<StoredTestAnswer>>? = error("Unexpected continueTest")
    override suspend fun finish(sessionId: String, payload: String, mismatches: Int): SavedTestResult = error("Unexpected finish")
    override suspend fun session(sessionId: String): TestSession? = error("Unexpected session")
}

internal fun catalogState(items: List<CatalogItem>, result: CatalogResult? = null, recommendationsOnly: Boolean = false): OpportunitiesUiState.Content {
    val selection = CatalogSelection(items)
    val picks = result?.let(selection::recommendations).orEmpty()
    val sorted = if (recommendationsOnly) picks.map { it.item }
        else if (result != null) selection.byInterests(result) else selection.byNearestDate(LocalDate.now())
    return OpportunitiesUiState.Content(sorted, result, picks, recommendationsOnly = recommendationsOnly)
}
