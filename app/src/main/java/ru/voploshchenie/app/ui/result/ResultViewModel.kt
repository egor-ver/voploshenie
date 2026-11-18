package ru.voploshchenie.app.ui.result

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
import ru.voploshchenie.app.domain.model.TestConfig
import ru.voploshchenie.app.domain.repository.TestSessionRepository
import ru.voploshchenie.app.domain.scoring.ScoringResult
import ru.voploshchenie.app.domain.scoring.TestAnswers
import ru.voploshchenie.app.domain.usecase.resultDetails

sealed interface ResultUiState {
    data object Loading : ResultUiState
    data object Error : ResultUiState
    data class Content(val result: ScoringResult, val answers: TestAnswers, val config: TestConfig,
        val name: String?, val computedAt: Long) : ResultUiState
}

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val repository: TestSessionRepository,
    private val config: TestConfig,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val sessionId = requireNotNull(savedStateHandle.get<String>("sessionId"))
    private val mutableState = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init { retry() }

    fun retry() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableState.value = ResultUiState.Loading
            try {
                val row = repository.result(sessionId)
                mutableState.value = if (row == null) ResultUiState.Error else {
                    val rows = repository.answers(sessionId)
                    val data = row.resultDetails(rows, config)
                    ResultUiState.Content(data.result, data.answers, config, data.name, data.computedAt)
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.value = ResultUiState.Error }
        }
    }
}
