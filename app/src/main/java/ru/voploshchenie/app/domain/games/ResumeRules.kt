package ru.voploshchenie.app.domain.games

import java.time.LocalDate
import ru.voploshchenie.app.domain.model.ResumeCase
import ru.voploshchenie.app.domain.model.ResumeProgress

data class ResumeScore(val hits: Int, val falseAlarms: Int, val total: Int) {
    val misses: Int get() = total - hits
    val perfect: Boolean get() = hits == total && falseAlarms == 0
}

/** Правила «Резюме-детектора»: отметить слабые строки, проверить, посчитать результат. */
object ResumeRules {
    fun caseForDay(cases: List<ResumeCase>, date: LocalDate): ResumeCase =
        DailyPick.pick(cases, DailyPick.dayNumber(date)) { it.id }

    fun fresh(date: LocalDate) = ResumeProgress(date.toString())

    fun toggle(progress: ResumeProgress, index: Int, case: ResumeCase): ResumeProgress {
        if (progress.checked || index !in case.lines.indices) return progress
        val picked = if (index in progress.picked) progress.picked - index else progress.picked + index
        return progress.copy(picked = picked)
    }

    /** Проверка доступна, когда отмечена хотя бы одна строка. */
    fun check(progress: ResumeProgress): ResumeProgress =
        if (progress.checked || progress.picked.isEmpty()) progress else progress.copy(checked = true)

    fun score(progress: ResumeProgress, case: ResumeCase): ResumeScore {
        val lines = case.lines
        val hits = progress.picked.count { lines.getOrNull(it)?.weak == true }
        val falseAlarms = progress.picked.count { lines.getOrNull(it)?.weak == false }
        return ResumeScore(hits, falseAlarms, case.weakCount)
    }
}
