package ru.voploshchenie.app.ui.path

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
import ru.voploshchenie.app.domain.usecase.resultDetails
import ru.voploshchenie.app.domain.model.PathProfileData

sealed interface PathUiState {
    data object Loading : PathUiState
    data object Empty : PathUiState
    data class Content(val data: PathProfileData, val sessionId: String? = null) : PathUiState
    data object Error : PathUiState
}

@HiltViewModel
class PathViewModel @Inject constructor(
    private val sessions: TestSessionRepository,
    private val config: TestConfig,
) : ViewModel() {
    private val mutableState = MutableStateFlow<PathUiState>(PathUiState.Loading)
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    private val mutableDraft = MutableStateFlow(false)
    val hasDraft = mutableDraft.asStateFlow()

    init { viewModelScope.launch { sessions.observeChanges().collect { refresh() } } }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            mutableState.value = PathUiState.Loading
            try {
                mutableDraft.value = sessions.continueTest() != null
                val result = sessions.latestResult()
                if (result == null) mutableState.value = PathUiState.Empty
                else {
                    val answers = sessions.answers(result.sessionId)
                    val details = result.resultDetails(answers, config)
                    val data = pathProfileData(details.result, details.name, config)
                    mutableState.value = PathUiState.Content(data, result.sessionId)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.value = PathUiState.Error }
        }
    }
}
