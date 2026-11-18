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
import ru.voploshchenie.app.domain.games.MythsRules
import ru.voploshchenie.app.domain.model.GameId
import ru.voploshchenie.app.domain.model.Myth
import ru.voploshchenie.app.domain.model.MythsProgress
import ru.voploshchenie.app.domain.repository.GameContentRepository
import ru.voploshchenie.app.domain.repository.GameProgressRepository
import ru.voploshchenie.app.domain.repository.TodayProvider

internal data class MythsUiState(
    val loaded: Boolean = false,
    val cards: List<Myth> = emptyList(),
    val progress: MythsProgress = MythsProgress(""),
    val streak: Int = 0,
    val howOpen: Boolean = false,
) {
    val finished: Boolean get() = MythsRules.isFinished(progress)
    val current: Myth? get() = cards.getOrNull(progress.index)
    val answered: Boolean get() = progress.answers.size > progress.index
    val score: Int get() = MythsRules.score(progress, cards)
}

@HiltViewModel
class MythsViewModel @Inject constructor(
    private val content: GameContentRepository,
    private val progressRepository: GameProgressRepository,
    private val todayProvider: TodayProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MythsUiState())
    internal val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val today = todayProvider.today()
            val cards = MythsRules.cardsForDay(content.myths(), today)
            val saved = progressRepository.myths()?.takeIf { it.date == today.toString() } ?: MythsRules.fresh(today)
            val stats = progressRepository.stats(GameId.Myths)
            val seen = progressRepository.howToSeen(GameId.Myths)
            if (!seen) progressRepository.markHowToSeen(GameId.Myths)
            mutableState.value = MythsUiState(true, cards, saved, GameStreaks.current(stats, today), howOpen = !seen)
        }
    }

    fun say(saidTrue: Boolean) = update { MythsRules.answer(it, saidTrue) }

    fun next() = update(afterSave = { progress ->
        // Пять карточек пройдены — день засчитывается в серию.
        if (MythsRules.isFinished(progress)) {
            val today = todayProvider.today()
            val stats = GameStreaks.recordPlay(progressRepository.stats(GameId.Myths), today, won = true)
            progressRepository.saveStats(GameId.Myths, stats)
            mutableState.update { it.copy(streak = GameStreaks.current(stats, today)) }
        }
    }) { MythsRules.next(it) }

    fun toggleHow() = mutableState.update { it.copy(howOpen = !it.howOpen) }

    private fun update(afterSave: suspend (MythsProgress) -> Unit = {}, change: (MythsProgress) -> MythsProgress) {
        val before = mutableState.value.progress
        val after = change(before)
        if (after == before) return
        mutableState.update { it.copy(progress = after) }
        viewModelScope.launch {
            progressRepository.saveMyths(after)
            afterSave(after)
        }
    }
}
