package ru.voploshchenie.app.ui.saved

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
import ru.voploshchenie.app.domain.usecase.DeadlineGroup
import ru.voploshchenie.app.domain.usecase.groupDeadlines
import java.time.LocalDate

internal sealed interface SavedUiState {
    data object Loading : SavedUiState
    data object Error : SavedUiState
    data class Content(
        val saved: List<SavedItem>,
        val items: List<CatalogItem>,
        val deadlines: List<DeadlineGroup> = groupDeadlines(items.filter { item -> saved.any { it.item_id == item.id } }, LocalDate.now()),
    ) : SavedUiState
}

@HiltViewModel
class SavedViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val repository: SavedRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<SavedUiState>(SavedUiState.Loading)
    internal val state = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init { retry() }

    fun retry() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableState.value = SavedUiState.Loading
            try {
                val items = catalog.all()
                repository.observeAll().collect { saved ->
                    val ids = saved.mapTo(hashSetOf()) { it.item_id }
                    val deadlines = groupDeadlines(items.filter { it.id in ids }, LocalDate.now())
                    mutableState.value = SavedUiState.Content(saved, items, deadlines)
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.value = SavedUiState.Error }
        }
    }
}
