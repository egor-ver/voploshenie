package ru.voploshchenie.app.domain.games

import java.time.LocalDate
import ru.voploshchenie.app.domain.model.GameStats

/** Серия дней. Проигрыш в «Слове дня» её обнуляет, в остальных играх серия — про привычку играть каждый день. */
object GameStreaks {
    fun recordPlay(stats: GameStats, date: LocalDate, won: Boolean, keepOnLoss: Boolean = false): GameStats {
        if (stats.last == date.toString()) return stats
        val continued = stats.last == date.minusDays(1).toString()
        val streak = if (won || keepOnLoss) (if (continued) stats.streak else 0) + 1 else 0
        return GameStats(streak, maxOf(stats.best, streak), date.toString(), stats.played + 1, stats.wins + if (won) 1 else 0)
    }

    /** Серия, которая ещё жива: сыграно сегодня или вчера. */
    fun current(stats: GameStats, today: LocalDate): Int =
        if (stats.last == today.toString() || stats.last == today.minusDays(1).toString()) stats.streak else 0
}
