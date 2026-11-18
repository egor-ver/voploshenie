package ru.voploshchenie.app.data.games

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.voploshchenie.app.di.IoDispatcher
import ru.voploshchenie.app.domain.model.Myth
import ru.voploshchenie.app.domain.model.ResumeCase
import ru.voploshchenie.app.domain.model.ResumeItem
import ru.voploshchenie.app.domain.model.WordEntry
import ru.voploshchenie.app.domain.repository.GameContentRepository

/** Контент игр из assets/games (выгружается из web/ скриптом tools/export_games_data.mjs). */
@Singleton
class AssetGameContentRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
) : GameContentRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var cachedWords: List<WordEntry>? = null
    private var cachedMyths: List<Myth>? = null
    private var cachedResumes: List<ResumeCase>? = null

    private fun read(name: String) = context.assets.open("games/$name").bufferedReader().use { it.readText() }

    override suspend fun words(): List<WordEntry> = lock.withLock {
        cachedWords ?: withContext(dispatcher) {
            json.decodeFromString<List<WordContentDto>>(read("words.json")).map { WordEntry(it.word, it.display, it.topic, it.hint) }
        }.also { cachedWords = it }
    }

    override suspend fun myths(): List<Myth> = lock.withLock {
        cachedMyths ?: withContext(dispatcher) {
            json.decodeFromString<List<MythContentDto>>(read("myths.json")).map { Myth(it.id, it.truth, it.statement, it.why) }
        }.also { cachedMyths = it }
    }

    override suspend fun resumes(): List<ResumeCase> = lock.withLock {
        cachedResumes ?: withContext(dispatcher) {
            json.decodeFromString<List<ResumeCaseDto>>(read("resumes.json")).map { dto ->
                ResumeCase(dto.id, dto.role, dto.name, dto.sub, dto.items.map { item ->
                    if (item.heading != null) ResumeItem.Heading(item.heading)
                    else ResumeItem.Line(item.text.orEmpty(), item.weak, item.why, item.fix)
                })
            }
        }.also { cachedResumes = it }
    }
}

@Serializable
private data class WordContentDto(val word: String, val display: String? = null, val topic: String, val hint: String)

@Serializable
private data class MythContentDto(val id: String, val truth: Boolean, val statement: String, val why: String)

@Serializable
private data class ResumeItemContentDto(
    val heading: String? = null,
    val text: String? = null,
    val weak: Boolean = false,
    val why: String? = null,
    val fix: String? = null,
)

@Serializable
private data class ResumeCaseDto(val id: String, val role: String, val name: String, val sub: String, val items: List<ResumeItemContentDto>)
