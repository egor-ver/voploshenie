package ru.voploshchenie.app.domain.games

import java.time.LocalDate

/**
 * Выбор «задания дня». Порядок одинаковый у всех пользователей и совпадает с веб-версией:
 * элементы идут по кругу без повторов, на каждом новом круге порядок перемешивается заново.
 */
object DailyPick {
    /** FNV-1a по кодовым точкам — тот же хэш, что в web/js/games/store.js. */
    fun hash(text: String): Long {
        var value = 2166136261L
        text.codePoints().forEach { point -> value = ((value xor point.toLong()) * 16777619L) and 0xFFFFFFFFL }
        return value
    }

    fun dayNumber(date: LocalDate): Long = date.toEpochDay()

    /** Элементы в порядке [lap]-го круга. */
    fun <T> order(items: List<T>, lap: Long, key: (T) -> String): List<T> =
        items.sortedWith(compareBy<T> { hash("$lap:${key(it)}") }.thenBy { key(it) })

    fun <T> pick(items: List<T>, index: Long, key: (T) -> String): T {
        val size = items.size.toLong()
        return order(items, Math.floorDiv(index, size), key)[Math.floorMod(index, size).toInt()]
    }
}
