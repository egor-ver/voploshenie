package ru.voploshchenie.app.data.games

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.voploshchenie.app.di.IoDispatcher
import ru.voploshchenie.app.domain.model.GameId
import ru.voploshchenie.app.domain.model.GameStats
import ru.voploshchenie.app.domain.model.MythsProgress
import ru.voploshchenie.app.domain.model.ResumeProgress
import ru.voploshchenie.app.domain.model.WordleProgress
import ru.voploshchenie.app.domain.model.WordleStatus
import ru.voploshchenie.app.domain.repository.GameProgressRepository

/** Прогресс игр хранится на устройстве в SharedPreferences (небольшие JSON-значения). */
@Singleton
class PrefsGameProgressRepository @Inject constructor(
    @ApplicationContext context: Context,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
) : GameProgressRepository {
    private val prefs = context.getSharedPreferences("voplo_games", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private suspend inline fun <reified T> load(key: String): T? = withContext(dispatcher) {
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }
    }

    private suspend inline fun <reified T> store(key: String, value: T) = withContext(dispatcher) {
        prefs.edit().putString(key, json.encodeToString(value)).apply()
    }

    override suspend fun wordle() = load<WordleDto>("wordle.game")?.let { WordleProgress(it.date, it.guesses, runCatching { WordleStatus.valueOf(it.status) }.getOrDefault(WordleStatus.Playing)) }
    override suspend fun saveWordle(progress: WordleProgress) = store("wordle.game", WordleDto(progress.date, progress.guesses, progress.status.name))

    override suspend fun myths() = load<MythsDto>("myths.game")?.let { MythsProgress(it.date, it.index, it.answers) }
    override suspend fun saveMyths(progress: MythsProgress) = store("myths.game", MythsDto(progress.date, progress.index, progress.answers))

    override suspend fun resume() = load<ResumeProgressDto>("resume.game")?.let { ResumeProgress(it.date, it.picked.toSet(), it.checked) }
    override suspend fun saveResume(progress: ResumeProgress) = store("resume.game", ResumeProgressDto(progress.date, progress.picked.sorted(), progress.checked))

    override suspend fun stats(game: GameId) = load<StatsDto>("${game.key}.stats")?.let { GameStats(it.streak, it.best, it.last, it.played, it.wins) } ?: GameStats()
    override suspend fun saveStats(game: GameId, stats: GameStats) = store("${game.key}.stats", StatsDto(stats.streak, stats.best, stats.last, stats.played, stats.wins))

    override suspend fun howToSeen(game: GameId) = withContext(dispatcher) { prefs.getBoolean("seen.${game.key}", false) }
    override suspend fun markHowToSeen(game: GameId) = withContext(dispatcher) { prefs.edit().putBoolean("seen.${game.key}", true).apply() }
}

@Serializable
private data class WordleDto(val date: String, val guesses: List<String>, val status: String)

@Serializable
private data class MythsDto(val date: String, val index: Int, val answers: List<Boolean>)

@Serializable
private data class ResumeProgressDto(val date: String, val picked: List<Int>, val checked: Boolean)

@Serializable
private data class StatsDto(val streak: Int, val best: Int, val last: String, val played: Int, val wins: Int)
