package ru.voploshchenie.app.ui.opportunities

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.voploshchenie.app.domain.model.*
import ru.voploshchenie.app.domain.model.TestConfig
import ru.voploshchenie.app.domain.repository.CatalogRepository
import ru.voploshchenie.app.domain.repository.SavedRepository
import ru.voploshchenie.app.domain.repository.TestSessionRepository
import ru.voploshchenie.app.domain.usecase.CatalogSelection
import ru.voploshchenie.app.domain.usecase.catalogResult
import ru.voploshchenie.app.domain.usecase.resultAnswers
import java.time.LocalDate
import kotlinx.serialization.json.*

sealed interface OpportunitiesUiState {
    data object Loading : OpportunitiesUiState
    data object Error : OpportunitiesUiState
    data class Content(
        val items: List<CatalogItem>,
        val result: CatalogResult? = null,
        val recommendations: List<CatalogRecommendation> = emptyList(),
        val savedIds: Set<String> = emptySet(),
        val query: String = "",
        val type: Int = 0,
        val interests: Boolean = result != null,
        val sorting: Boolean = false,
        val sortChoice: Boolean = interests,
        val recommendationsOnly: Boolean = false,
        val similarTo: String? = null,
        val profileCourse: String? = null,
        val profileCity: String? = null,
        val locationPreference: String? = null,
    ) : OpportunitiesUiState
}

@HiltViewModel
class OpportunitiesViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val repository: TestSessionRepository,
    private val savedRepository: SavedRepository,
    private val config: TestConfig,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow<OpportunitiesUiState>(OpportunitiesUiState.Loading)
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var selection: CatalogSelection? = null

    init { retry() }

    fun retry() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val previous = mutableState.value as? OpportunitiesUiState.Content
            if (previous == null) mutableState.value = OpportunitiesUiState.Loading
            try {
                val items = catalog.all()
                selection = CatalogSelection(items)
                val savedResult = repository.latestResult()
                val result = savedResult?.catalogResult()
                val profileAnswers = savedResult?.let { resultAnswers(repository.answers(it.sessionId), config) }
                val profileValue = profileAnswers?.profile.orEmpty()
                val content = OpportunitiesUiState.Content(items, result,
                    recommendations = result?.let { requireNotNull(selection).recommendations(it) }.orEmpty(),
                    query = savedStateHandle["query"] ?: "",
                    type = savedStateHandle["type"] ?: 0,
                    interests = (savedStateHandle["interests"] ?: (result != null)) && result != null,
                    recommendationsOnly = savedStateHandle["recommendationsOnly"] ?: false,
                    similarTo = savedStateHandle["similarTo"],
                    profileCourse = profileValue["PRF02"]?.jsonPrimitive?.contentOrNull,
                    profileCity = profileValue["PRF04"]?.jsonPrimitive?.contentOrNull,
                    locationPreference = profileAnswers?.nad?.get("NAD02"),
                    savedIds = previous?.savedIds.orEmpty(),
                    sorting = savedStateHandle["sorting"] ?: false,
                    sortChoice = (savedStateHandle["sortChoice"] ?: (result != null)) && result != null)
                mutableState.value = select(content)
                savedRepository.observeAll().collect { saved ->
                    update { it.copy(savedIds = saved.map { row -> row.item_id }.toSet()) }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.value = OpportunitiesUiState.Error }
        }
    }

    fun search(query: String) { savedStateHandle["query"] = query; update { select(it.copy(query = query)) } }
    fun filter(type: Int) {
        if (type !in catalogTypes.indices) return
        savedStateHandle["type"] = type
        update { select(it.copy(type = type)) }
    }
    fun reset() { savedStateHandle["query"] = ""; savedStateHandle["type"] = 0; update { select(it.copy(query = "", type = 0)) } }
    fun showSort() = update {
        savedStateHandle["sorting"] = true
        savedStateHandle["sortChoice"] = it.interests
        it.copy(sorting = true, sortChoice = it.interests)
    }
    fun hideSort() { savedStateHandle["sorting"] = false; update { it.copy(sorting = false) } }
    fun chooseSort(interests: Boolean) = update {
        val selected = interests && it.result != null
        savedStateHandle["sortChoice"] = selected
        it.copy(sortChoice = selected)
    }
    fun sort(interests: Boolean) = update {
        val enabled = interests && it.result != null
        savedStateHandle["interests"] = enabled
        savedStateHandle["sorting"] = false
        select(it.copy(interests = enabled, sorting = false))
    }

    fun toggleSaved(id: String) = viewModelScope.launch {
        val content = mutableState.value as? OpportunitiesUiState.Content ?: return@launch
        if (content.items.none { it.id == id }) return@launch
        try {
            if (id in content.savedIds) savedRepository.remove(id)
            else savedRepository.save(SavedItem(id, System.currentTimeMillis()))
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutableState.value = OpportunitiesUiState.Error }
    }

    private fun update(transform: (OpportunitiesUiState.Content) -> OpportunitiesUiState.Content) {
        (mutableState.value as? OpportunitiesUiState.Content)?.let { mutableState.value = transform(it) }
    }

    private fun select(content: OpportunitiesUiState.Content): OpportunitiesUiState.Content {
        val selection = requireNotNull(selection)
        if (content.similarTo != null) {
            val item = selection.all().firstOrNull { it.id == content.similarTo }
            return content.copy(items = item?.let(selection::similar).orEmpty())
        }
        val candidates = selection.filter(catalogTypes[content.type], content.query,
            if (content.recommendationsOnly) content.recommendations.map { it.item } else selection.all())
        val ordered = when {
            // подборка уже упорядочена по интересам, с добором из соседних сфер
            content.recommendationsOnly && content.interests -> candidates
            content.interests && content.result != null -> selection.byInterests(content.result, candidates)
            else -> selection.byNearestDate(LocalDate.now(), candidates)
        }
        return content.copy(items = if (content.recommendationsOnly && content.interests)
            selection.byCourse(content.profileCourse, ordered) else ordered)
    }
}
