package ru.voploshchenie.app

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.voploshchenie.app.domain.games.DailyPick
import ru.voploshchenie.app.domain.games.GameStreaks
import ru.voploshchenie.app.domain.games.MythsRules
import ru.voploshchenie.app.domain.games.ResumeRules
import ru.voploshchenie.app.domain.games.WordleRules
import ru.voploshchenie.app.domain.model.CatalogDate
import ru.voploshchenie.app.domain.model.CatalogItem
import ru.voploshchenie.app.domain.model.GameStats
import ru.voploshchenie.app.domain.model.LetterMark
import ru.voploshchenie.app.domain.model.Myth
import ru.voploshchenie.app.domain.model.ResumeCase
import ru.voploshchenie.app.domain.model.ResumeItem
import ru.voploshchenie.app.domain.model.WordEntry
import ru.voploshchenie.app.domain.model.WordleStatus
import ru.voploshchenie.app.domain.usecase.DateGroup
import ru.voploshchenie.app.domain.usecase.dateGroup
import ru.voploshchenie.app.domain.usecase.groupByDate

class GamesDomainTest {
    private val day = LocalDate.of(2026, 11, 18)

    @Test fun hashMatchesFnv1aAndWebImplementation() {
        assertEquals(0xE40C292CL, DailyPick.hash("a"))
        assertEquals(2166136261L, DailyPick.hash(""))
        // Кириллица считается по кодовым точкам, как в web/js/games/store.js.
        assertEquals(DailyPick.hash("слово"), DailyPick.hash("слово"))
    }

    @Test fun pickWalksAllItemsPerLapWithoutRepeats() {
        val items = (1..7).map { "w$it" }
        val lap = (0L until 7L).map { DailyPick.pick(items, it) { s -> s } }
        assertEquals(items.toSet(), lap.toSet())
        assertEquals(7, lap.distinct().size)
        // Отрицательные индексы не ломают выбор.
        assertTrue(DailyPick.pick(items, -3) { it } in items)
    }

    @Test fun wordleMarksHandleDuplicateLetters() {
        // Вторая «а» в догадке не подсвечивается: в ответе «кора» она одна и уже занята точным попаданием.
        assertEquals(listOf(LetterMark.Absent, LetterMark.Present, LetterMark.Present, LetterMark.Correct), WordleRules.score("арка", "кора"))
        assertEquals(List(4) { LetterMark.Correct }, WordleRules.score("кора", "кора"))
    }

    @Test fun wordleSubmitTracksStatusAndIgnoresFinishedGame() {
        var p = WordleRules.fresh(day)
        repeat(5) { p = WordleRules.submit(p, "аааа", "бббб") }
        assertEquals(WordleStatus.Playing, p.status)
        p = WordleRules.submit(p, "аааа", "бббб")
        assertEquals(WordleStatus.Lost, p.status)
        assertEquals(6, WordleRules.submit(p, "бббб", "бббб").guesses.size)
        assertEquals(WordleStatus.Won, WordleRules.submit(WordleRules.fresh(day), "бббб", "бббб").status)
        // Слово неверной длины не принимается.
        assertEquals(0, WordleRules.submit(WordleRules.fresh(day), "ааа", "бббб").guesses.size)
    }

    @Test fun wordleNormalizeFoldsYo() {
        assertEquals("елка", WordleRules.normalize("Ёлка"))
    }

    @Test fun wordleKeyboardKeepsBestMark() {
        val states = WordleRules.keyStates(listOf("ваза", "кара"), "кара")
        assertEquals(LetterMark.Correct, states['к'])
        assertEquals(LetterMark.Absent, states['в'])
    }

    @Test fun wordForDayIsDeterministic() {
        val words = listOf("кофе", "лето", "вода", "небо", "море").map { WordEntry(it, null, "t", "h") }
        assertEquals(WordleRules.wordForDay(words, day), WordleRules.wordForDay(words, day))
    }

    @Test fun mythsFiveCardsAndScoring() {
        val myths = (1..20).map { Myth("m$it", it % 2 == 0, "s$it", "w$it") }
        val cards = MythsRules.cardsForDay(myths, day)
        assertEquals(MythsRules.PER_DAY, cards.size)
        var p = MythsRules.fresh(day)
        cards.forEach { card ->
            p = MythsRules.answer(p, card.truth)
            // Повторный ответ на ту же карточку игнорируется.
            assertEquals(p, MythsRules.answer(p, !card.truth))
            p = MythsRules.next(p)
        }
        assertTrue(MythsRules.isFinished(p))
        assertEquals(MythsRules.PER_DAY, MythsRules.score(p, cards))
    }

    @Test fun mythsCannotAdvanceWithoutAnswer() {
        val p = MythsRules.fresh(day)
        assertEquals(p, MythsRules.next(p))
    }

    private val resume = ResumeCase("r1", "Роль", "Имя", "Подзаголовок", listOf(
        ResumeItem.Heading("Опыт"),
        ResumeItem.Line("слабая", true, "почему", "как лучше"),
        ResumeItem.Line("нормальная", false, null, null),
        ResumeItem.Line("слабая 2", true, "почему", "как лучше"),
    ))

    @Test fun resumeIndexesLinesAndScores() {
        assertEquals(2, resume.weakCount)
        var p = ResumeRules.fresh(day)
        assertEquals(p, ResumeRules.check(p))
        p = ResumeRules.toggle(p, 0, resume)
        p = ResumeRules.toggle(p, 1, resume)
        p = ResumeRules.toggle(p, 1, resume)
        p = ResumeRules.toggle(p, 1, resume)
        assertEquals(setOf(0, 1), p.picked)
        p = ResumeRules.check(p)
        assertTrue(p.checked)
        // После проверки отметки не меняются.
        assertEquals(p, ResumeRules.toggle(p, 2, resume))
        val score = ResumeRules.score(p, resume)
        assertEquals(1, score.hits)
        assertEquals(1, score.falseAlarms)
        assertEquals(1, score.misses)
        assertFalse(score.perfect)
    }

    @Test fun streakContinuesResetsAndIsIdempotentWithinDay() {
        var s = GameStreaks.recordPlay(GameStats(), day, won = true)
        assertEquals(1, s.streak)
        assertEquals(s, GameStreaks.recordPlay(s, day, won = false))
        s = GameStreaks.recordPlay(s, day.plusDays(1), won = true)
        assertEquals(2, s.streak)
        assertEquals(2, s.best)
        val lost = GameStreaks.recordPlay(s, day.plusDays(2), won = false)
        assertEquals(0, lost.streak)
        assertEquals(2, lost.best)
        assertEquals(3, GameStreaks.recordPlay(s, day.plusDays(2), won = false, keepOnLoss = true).streak)
        assertEquals(1, GameStreaks.recordPlay(s, day.plusDays(5), won = true).streak)
        assertEquals(2, GameStreaks.current(s, day.plusDays(2)))
        assertEquals(0, GameStreaks.current(s, day.plusDays(3)))
    }

    private fun item(id: String, rolling: Boolean = false, vararg dates: CatalogDate) = CatalogItem(
        id = id, type = "course", title = id, direction = "d", spheres = listOf("it"), audience_text = "a",
        dates = dates.toList(), is_rolling = rolling, url = "https://example.org", url_kind = "site_home",
    )

    @Test fun datesFallIntoGroups() {
        assertEquals(DateGroup.Ongoing, dateGroup(item("a", false, CatalogDate("start", "2026", "year")), day))
        assertEquals(DateGroup.NextMonth, dateGroup(item("b", false, CatalogDate("apply_deadline", "2026-12-01", "day")), day))
        assertEquals(DateGroup.Month(YearMonth.of(2027, 3)), dateGroup(item("c", false, CatalogDate("start", "2027-03-10", "day")), day))
        assertEquals(DateGroup.Year(2027), dateGroup(item("d", false, CatalogDate("start", "2027", "year")), day))
        assertEquals(DateGroup.Rolling, dateGroup(item("e", true), day))
        assertEquals(DateGroup.Unknown, dateGroup(item("f"), day))
        // Прошедшая дата не считается ближайшей.
        assertEquals(DateGroup.Unknown, dateGroup(item("g", false, CatalogDate("apply_deadline", "2026-01-01", "day")), day))
    }

    @Test fun groupingKeepsFirstSeenOrder() {
        val items = listOf(
            item("a", false, CatalogDate("start", "2027-03-10", "day")),
            item("b", true),
            item("c", false, CatalogDate("start", "2027-03-20", "day")),
        )
        val groups = groupByDate(items, day)
        assertEquals(listOf(DateGroup.Month(YearMonth.of(2027, 3)), DateGroup.Rolling), groups.map { it.first })
        assertEquals(listOf("a", "c"), groups.first().second.map { it.id })
    }
}
