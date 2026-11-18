package ru.voploshchenie.app.domain.games

import java.time.LocalDate
import ru.voploshchenie.app.domain.model.Myth
import ru.voploshchenie.app.domain.model.MythsProgress

/** Правила «Мифа или правды»: пять утверждений в день, правда и мифы чередуются. */
object MythsRules {
    const val PER_DAY = 5
    private val pattern = listOf(false, true, false, true, false)

    private fun deck(myths: List<Myth>, lap: Long): List<Myth> {
        val truths = DailyPick.order(myths.filter { it.truth }, lap) { it.id }.toMutableList()
        val lies = DailyPick.order(myths.filterNot { it.truth }, lap) { it.id }.toMutableList()
        val out = ArrayList<Myth>(myths.size)
        while (truths.isNotEmpty() || lies.isNotEmpty()) {
            for (wantTruth in pattern) {
                val first = if (wantTruth) truths else lies
                val second = if (wantTruth) lies else truths
                (first.removeFirstOrNull() ?: second.removeFirstOrNull())?.let(out::add)
            }
        }
        return out
    }

    fun cardsForDay(myths: List<Myth>, date: LocalDate): List<Myth> = List(PER_DAY) { i ->
        val index = DailyPick.dayNumber(date) * PER_DAY + i
        val size = myths.size.toLong()
        deck(myths, Math.floorDiv(index, size))[Math.floorMod(index, size).toInt()]
    }

    fun fresh(date: LocalDate) = MythsProgress(date.toString())

    fun isFinished(progress: MythsProgress) = progress.index >= PER_DAY

    /** Ответ на текущую карточку ([saidTrue] — «Правда»). Повторный ответ игнорируется. */
    fun answer(progress: MythsProgress, saidTrue: Boolean): MythsProgress =
        if (isFinished(progress) || progress.answers.size > progress.index) progress
        else progress.copy(answers = progress.answers + saidTrue)

    fun next(progress: MythsProgress): MythsProgress =
        if (progress.answers.size <= progress.index) progress else progress.copy(index = progress.index + 1)

    fun score(progress: MythsProgress, cards: List<Myth>): Int =
        progress.answers.withIndex().count { (i, said) -> cards.getOrNull(i)?.truth == said }
}
