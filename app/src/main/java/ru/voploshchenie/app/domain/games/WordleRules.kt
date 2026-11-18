package ru.voploshchenie.app.domain.games

import java.time.LocalDate
import ru.voploshchenie.app.domain.model.LetterMark
import ru.voploshchenie.app.domain.model.WordEntry
import ru.voploshchenie.app.domain.model.WordleProgress
import ru.voploshchenie.app.domain.model.WordleStatus

/** Правила «Слова дня»: подбор слова, метки букв, состояние партии. Без Android — проверяется обычными тестами. */
object WordleRules {
    const val MAX_TRIES = 6

    fun normalize(text: String): String = text.lowercase().replace('ё', 'е')

    fun wordForDay(words: List<WordEntry>, date: LocalDate): WordEntry =
        DailyPick.pick(words, DailyPick.dayNumber(date)) { it.word }

    /** Честные метки Wordle: повторяющиеся буквы не «горят» лишний раз. */
    fun score(guess: String, answer: String): List<LetterMark> {
        val marks = MutableList(answer.length) { LetterMark.Absent }
        val left = HashMap<Char, Int>()
        for (i in answer.indices) {
            if (guess[i] == answer[i]) marks[i] = LetterMark.Correct else left.merge(answer[i], 1, Int::plus)
        }
        for (i in answer.indices) {
            if (marks[i] == LetterMark.Correct) continue
            val remaining = left[guess[i]] ?: 0
            if (remaining > 0) { marks[i] = LetterMark.Present; left[guess[i]] = remaining - 1 }
        }
        return marks
    }

    /** Лучшая метка для каждой буквы клавиатуры (Correct сильнее Present, Present сильнее Absent). */
    fun keyStates(guesses: List<String>, answer: String): Map<Char, LetterMark> {
        val states = HashMap<Char, LetterMark>()
        guesses.forEach { guess ->
            score(guess, answer).forEachIndexed { i, mark ->
                val current = states[guess[i]]
                if (current == null || mark.ordinal < current.ordinal) states[guess[i]] = mark
            }
        }
        return states
    }

    fun fresh(date: LocalDate) = WordleProgress(date.toString())

    /** Принять слово нужной длины. Партия, которая уже закончилась, не меняется. */
    fun submit(progress: WordleProgress, guess: String, answer: String): WordleProgress {
        if (progress.status != WordleStatus.Playing || guess.length != answer.length) return progress
        val guesses = progress.guesses + guess
        val status = when {
            guess == answer -> WordleStatus.Won
            guesses.size >= MAX_TRIES -> WordleStatus.Lost
            else -> WordleStatus.Playing
        }
        return progress.copy(guesses = guesses, status = status)
    }

    fun shareText(progress: WordleProgress, answer: String): String {
        val symbol = mapOf(LetterMark.Correct to "🟩", LetterMark.Present to "🟪", LetterMark.Absent to "⬜")
        val rows = progress.guesses.joinToString("\n") { guess -> score(guess, answer).joinToString("") { symbol.getValue(it) } }
        val parts = progress.date.split('-')
        val result = if (progress.status == WordleStatus.Won) progress.guesses.size.toString() else "X"
        return "Воплощение · Слово дня ${parts[2]}.${parts[1]}\n$result/$MAX_TRIES\n$rows"
    }
}
