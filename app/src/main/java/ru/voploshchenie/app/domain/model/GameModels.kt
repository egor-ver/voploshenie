package ru.voploshchenie.app.domain.model

/** Мини-игры: модели контента и прогресса. Контент хранится в assets, прогресс — на устройстве. */
enum class GameId(val key: String) { Wordle("wordle"), Myths("myths"), Resume("resume") }

data class WordEntry(val word: String, val display: String?, val topic: String, val hint: String) {
    val shown: String get() = display ?: word
}

data class Myth(val id: String, val truth: Boolean, val statement: String, val why: String)

sealed interface ResumeItem {
    data class Heading(val title: String) : ResumeItem
    data class Line(val text: String, val weak: Boolean, val why: String?, val fix: String?) : ResumeItem
}

data class ResumeCase(val id: String, val role: String, val name: String, val sub: String, val items: List<ResumeItem>) {
    val lines: List<ResumeItem.Line> get() = items.filterIsInstance<ResumeItem.Line>()
    val weakCount: Int get() = lines.count { it.weak }
}

enum class LetterMark { Correct, Present, Absent }
enum class WordleStatus { Playing, Won, Lost }

data class WordleProgress(val date: String, val guesses: List<String> = emptyList(), val status: WordleStatus = WordleStatus.Playing)
data class MythsProgress(val date: String, val index: Int = 0, val answers: List<Boolean> = emptyList())
data class ResumeProgress(val date: String, val picked: Set<Int> = emptySet(), val checked: Boolean = false)

/** Серия: сколько дней подряд сыграна игра; [last] — дата последней партии (ISO). */
data class GameStats(val streak: Int = 0, val best: Int = 0, val last: String = "", val played: Int = 0, val wins: Int = 0)
