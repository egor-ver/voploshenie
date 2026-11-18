package ru.voploshchenie.app.domain.repository

import java.time.LocalDate
import ru.voploshchenie.app.domain.model.GameId
import ru.voploshchenie.app.domain.model.GameStats
import ru.voploshchenie.app.domain.model.Myth
import ru.voploshchenie.app.domain.model.MythsProgress
import ru.voploshchenie.app.domain.model.ResumeCase
import ru.voploshchenie.app.domain.model.ResumeProgress
import ru.voploshchenie.app.domain.model.WordEntry
import ru.voploshchenie.app.domain.model.WordleProgress

/** Источник контента игр (слова, утверждения, резюме). */
interface GameContentRepository {
    suspend fun words(): List<WordEntry>
    suspend fun myths(): List<Myth>
    suspend fun resumes(): List<ResumeCase>
}

/** Прогресс игр на устройстве: текущая партия, серия и признак «Как играть уже показан». */
interface GameProgressRepository {
    suspend fun wordle(): WordleProgress?
    suspend fun saveWordle(progress: WordleProgress)
    suspend fun myths(): MythsProgress?
    suspend fun saveMyths(progress: MythsProgress)
    suspend fun resume(): ResumeProgress?
    suspend fun saveResume(progress: ResumeProgress)
    suspend fun stats(game: GameId): GameStats
    suspend fun saveStats(game: GameId, stats: GameStats)
    suspend fun howToSeen(game: GameId): Boolean
    suspend fun markHowToSeen(game: GameId)
}

/** Текущая дата — отдельным интерфейсом, чтобы логику «игра дня» можно было проверять без часов системы. */
fun interface TodayProvider {
    fun today(): LocalDate
}
