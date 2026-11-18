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
import ru.voploshchenie.app.domain.model.CatalogItem
import ru.voploshchenie.app.domain.model.SavedItem
import ru.voploshchenie.app.domain.repository.CatalogRepository
import ru.voploshchenie.app.domain.repository.SavedRepository
import ru.voploshchenie.app.domain.repository.TestSessionRepository
import ru.voploshchenie.app.domain.usecase.catalogResult
import ru.voploshchenie.app.domain.usecase.matchPercent
import ru.voploshchenie.app.domain.usecase.opportunityClosed
import java.time.LocalDate

sealed interface OpportunityDetailUiState {
    data object Loading : OpportunityDetailUiState
    data object Error : OpportunityDetailUiState
    data class Content(val item: CatalogItem, val saved: Boolean = false, val closed: Boolean = false,
        val matchPercent: Int? = null) : OpportunityDetailUiState
}

@HiltViewModel
class OpportunityDetailViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val repository: TestSessionRepository,
    private val savedRepository: SavedRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val itemId = requireNotNull(savedStateHandle.get<String>("itemId"))
    private val mutableState = MutableStateFlow<OpportunityDetailUiState>(OpportunityDetailUiState.Loading)
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init { retry() }

    fun retry() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val previous = mutableState.value as? OpportunityDetailUiState.Content
            if (previous == null) mutableState.value = OpportunityDetailUiState.Loading
            try {
                val item = catalog.all().firstOrNull { it.id == itemId }
                if (item == null) { mutableState.value = OpportunityDetailUiState.Error; return@launch }
                val result = repository.latestResult()?.catalogResult()
                val content = OpportunityDetailUiState.Content(item, closed = opportunityClosed(item, LocalDate.now()),
                    matchPercent = result?.let { matchPercent(item, it) })
                savedRepository.observeSaved(itemId).collect { saved -> mutableState.value = content.copy(saved = saved) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.value = OpportunityDetailUiState.Error }
        }
    }

    fun toggleSaved() = viewModelScope.launch {
        val content = mutableState.value as? OpportunityDetailUiState.Content ?: return@launch
        try {
            if (content.saved) savedRepository.remove(itemId)
            else savedRepository.save(SavedItem(itemId, System.currentTimeMillis()))
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutableState.value = OpportunityDetailUiState.Error }
    }
}
