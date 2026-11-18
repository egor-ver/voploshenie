package ru.voploshchenie.app.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.voploshchenie.app.domain.games.GameStreaks
import ru.voploshchenie.app.domain.games.MythsRules
import ru.voploshchenie.app.domain.games.ResumeRules
import ru.voploshchenie.app.domain.games.WordleRules
import ru.voploshchenie.app.domain.model.GameId
import ru.voploshchenie.app.domain.model.WordleStatus
import ru.voploshchenie.app.domain.repository.GameContentRepository
import ru.voploshchenie.app.domain.repository.GameProgressRepository
import ru.voploshchenie.app.domain.repository.TodayProvider

/** Состояние карточки игры в хабе: ещё не начата, идёт или завершена (с числами для подписи). */
internal enum class SummaryKind { New, Live, Done }

internal data class GameSummary(val kind: SummaryKind, val a: Int = 0, val b: Int = 0, val streak: Int = 0)

internal data class GamesHubState(
    val wordle: GameSummary = GameSummary(SummaryKind.New),
    val myths: GameSummary = GameSummary(SummaryKind.New),
    val resume: GameSummary = GameSummary(SummaryKind.New),
    val loaded: Boolean = false,
) {
    val done: Int get() = listOf(wordle, myths, resume).count { it.kind == SummaryKind.Done }
}

@HiltViewModel
class GamesHubViewModel @Inject constructor(
    private val content: GameContentRepository,
    private val progress: GameProgressRepository,
    private val todayProvider: TodayProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(GamesHubState())
    internal val state = mutableState.asStateFlow()

    init { refresh() }

    /** Перечитывает прогресс: вызывается при возврате на экран из игры. */
    fun refresh() {
        viewModelScope.launch {
            val today = todayProvider.today()
            val date = today.toString()
            val wordle = progress.wordle()?.takeIf { it.date == date }
            val myths = progress.myths()?.takeIf { it.date == date }
            val resume = progress.resume()?.takeIf { it.date == date }
            val streaks = GameId.entries.associateWith { GameStreaks.current(progress.stats(it), today) }

            val wordleSummary = when {
                wordle == null || wordle.guesses.isEmpty() -> GameSummary(SummaryKind.New)
                wordle.status == WordleStatus.Playing -> GameSummary(SummaryKind.Live, wordle.guesses.size + 1, WordleRules.MAX_TRIES)
                else -> GameSummary(SummaryKind.Done, if (wordle.status == WordleStatus.Won) wordle.guesses.size else 0)
            }.copy(streak = streaks.getValue(GameId.Wordle))
            val mythsSummary = when {
                myths == null || (myths.index == 0 && myths.answers.isEmpty()) -> GameSummary(SummaryKind.New)
                !MythsRules.isFinished(myths) -> GameSummary(SummaryKind.Live, myths.index + 1, MythsRules.PER_DAY)
                else -> GameSummary(SummaryKind.Done, MythsRules.score(myths, MythsRules.cardsForDay(content.myths(), today)), MythsRules.PER_DAY)
            }.copy(streak = streaks.getValue(GameId.Myths))
            val resumeSummary = when {
                resume == null || resume.picked.isEmpty() && !resume.checked -> GameSummary(SummaryKind.New)
                !resume.checked -> GameSummary(SummaryKind.Live, resume.picked.size)
                else -> ResumeRules.score(resume, ResumeRules.caseForDay(content.resumes(), today)).let { GameSummary(SummaryKind.Done, it.hits, it.total) }
            }.copy(streak = streaks.getValue(GameId.Resume))
            mutableState.value = GamesHubState(wordleSummary, mythsSummary, resumeSummary, loaded = true)
        }
    }
}
