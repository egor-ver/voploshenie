package ru.voploshchenie.app.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.voploshchenie.app.domain.games.GameStreaks
import ru.voploshchenie.app.domain.games.ResumeRules
import ru.voploshchenie.app.domain.games.ResumeScore
import ru.voploshchenie.app.domain.model.GameId
import ru.voploshchenie.app.domain.model.ResumeCase
import ru.voploshchenie.app.domain.model.ResumeProgress
import ru.voploshchenie.app.domain.repository.GameContentRepository
import ru.voploshchenie.app.domain.repository.GameProgressRepository
import ru.voploshchenie.app.domain.repository.TodayProvider

internal data class ResumeUiState(
    val loaded: Boolean = false,
    val case: ResumeCase? = null,
    val progress: ResumeProgress = ResumeProgress(""),
    val streak: Int = 0,
    val howOpen: Boolean = false,
) {
    val score: ResumeScore? get() = case?.let { ResumeRules.score(progress, it) }
}

@HiltViewModel
class ResumeViewModel @Inject constructor(
    private val content: GameContentRepository,
    private val progressRepository: GameProgressRepository,
    private val todayProvider: TodayProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ResumeUiState())
    internal val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val today = todayProvider.today()
            val case = ResumeRules.caseForDay(content.resumes(), today)
            val saved = progressRepository.resume()?.takeIf { it.date == today.toString() && it.picked.all { i -> i in case.lines.indices } }
                ?: ResumeRules.fresh(today)
            val stats = progressRepository.stats(GameId.Resume)
            val seen = progressRepository.howToSeen(GameId.Resume)
            if (!seen) progressRepository.markHowToSeen(GameId.Resume)
            mutableState.value = ResumeUiState(true, case, saved, GameStreaks.current(stats, today), howOpen = !seen)
        }
    }

    fun toggle(index: Int) {
        val s = mutableState.value
        val case = s.case ?: return
        change(ResumeRules.toggle(s.progress, index, case))
    }

    fun check() {
        val checked = ResumeRules.check(mutableState.value.progress)
        if (!change(checked)) return
        viewModelScope.launch {
            val today = todayProvider.today()
            // Серия — про привычку: день засчитывается независимо от результата.
            val stats = GameStreaks.recordPlay(progressRepository.stats(GameId.Resume), today, won = true, keepOnLoss = true)
            progressRepository.saveStats(GameId.Resume, stats)
            mutableState.update { it.copy(streak = GameStreaks.current(stats, today)) }
        }
    }

    fun toggleHow() = mutableState.update { it.copy(howOpen = !it.howOpen) }

    private fun change(next: ResumeProgress): Boolean {
        if (next == mutableState.value.progress) return false
        mutableState.update { it.copy(progress = next) }
        viewModelScope.launch { progressRepository.saveResume(next) }
        return true
    }
}
