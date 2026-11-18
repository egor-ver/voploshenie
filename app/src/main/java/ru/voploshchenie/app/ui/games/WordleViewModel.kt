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
import ru.voploshchenie.app.domain.games.WordleRules
import ru.voploshchenie.app.domain.model.GameId
import ru.voploshchenie.app.domain.model.GameStats
import ru.voploshchenie.app.domain.model.LetterMark
import ru.voploshchenie.app.domain.model.WordEntry
import ru.voploshchenie.app.domain.model.WordleProgress
import ru.voploshchenie.app.domain.model.WordleStatus
import ru.voploshchenie.app.domain.repository.GameContentRepository
import ru.voploshchenie.app.domain.repository.GameProgressRepository
import ru.voploshchenie.app.domain.repository.TodayProvider

internal data class WordleUiState(
    val loaded: Boolean = false,
    val entry: WordEntry? = null,
    val guesses: List<String> = emptyList(),
    val marks: List<List<LetterMark>> = emptyList(),
    val input: String = "",
    val status: WordleStatus = WordleStatus.Playing,
    val keyStates: Map<Char, LetterMark> = emptyMap(),
    val stats: GameStats = GameStats(),
    val streak: Int = 0,
    val howOpen: Boolean = false,
    /** Номер только что принятой строки: по нему проигрывается переворот плиток (иначе −1). */
    val revealRow: Int = -1,
    /** Меняется при попытке ввести неполное слово — строка «дрожит». */
    val shakeNonce: Int = 0,
    val needLetters: Boolean = false,
) {
    val length: Int get() = entry?.word?.length ?: 5
    val finished: Boolean get() = status != WordleStatus.Playing
}

@HiltViewModel
class WordleViewModel @Inject constructor(
    private val content: GameContentRepository,
    private val progressRepository: GameProgressRepository,
    private val todayProvider: TodayProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WordleUiState())
    internal val state = mutableState.asStateFlow()
    private var progress: WordleProgress? = null

    init {
        viewModelScope.launch {
            val today = todayProvider.today()
            val entry = WordleRules.wordForDay(content.words(), today)
            val saved = progressRepository.wordle()
            // Сохранённая партия подходит, только если она сегодняшняя и длина слов совпала (список могли обновить).
            val current = saved?.takeIf { it.date == today.toString() && it.guesses.all { guess -> guess.length == entry.word.length } }
                ?: WordleRules.fresh(today)
            progress = current
            val stats = progressRepository.stats(GameId.Wordle)
            val seen = progressRepository.howToSeen(GameId.Wordle)
            if (!seen) progressRepository.markHowToSeen(GameId.Wordle)
            mutableState.value = WordleUiState(true, entry, howOpen = !seen, stats = stats, streak = GameStreaks.current(stats, today))
                .withProgress(current, entry)
        }
    }

    private fun WordleUiState.withProgress(progress: WordleProgress, entry: WordEntry) = copy(
        guesses = progress.guesses,
        marks = progress.guesses.map { WordleRules.score(it, entry.word) },
        status = progress.status,
        keyStates = WordleRules.keyStates(progress.guesses, entry.word),
    )

    fun onKey(letter: Char) = mutableState.update { s ->
        if (!s.loaded || s.finished || s.input.length >= s.length) s
        else s.copy(input = s.input + WordleRules.normalize(letter.toString()), revealRow = -1, needLetters = false)
    }

    fun onBackspace() = mutableState.update { s ->
        if (s.finished || s.input.isEmpty()) s else s.copy(input = s.input.dropLast(1), revealRow = -1, needLetters = false)
    }

    fun onEnter() {
        val s = mutableState.value
        val entry = s.entry ?: return
        if (!s.loaded || s.finished) return
        if (s.input.length < s.length) {
            mutableState.update { it.copy(needLetters = true, shakeNonce = it.shakeNonce + 1, revealRow = -1) }
            return
        }
        val before = progress ?: return
        val next = WordleRules.submit(before, s.input, entry.word)
        progress = next
        mutableState.update { it.copy(input = "", needLetters = false, revealRow = next.guesses.size - 1).withProgress(next, entry) }
        viewModelScope.launch {
            progressRepository.saveWordle(next)
            if (next.status != WordleStatus.Playing) {
                val today = todayProvider.today()
                val stats = GameStreaks.recordPlay(progressRepository.stats(GameId.Wordle), today, won = next.status == WordleStatus.Won)
                progressRepository.saveStats(GameId.Wordle, stats)
                mutableState.update { it.copy(stats = stats, streak = GameStreaks.current(stats, today)) }
            }
        }
    }

    fun toggleHow() = mutableState.update { it.copy(howOpen = !it.howOpen) }

    /** Текст результата для «Поделиться». */
    fun shareText(): String? {
        val entry = state.value.entry ?: return null
        val current = progress ?: return null
        return WordleRules.shareText(current, entry.word)
    }
}
