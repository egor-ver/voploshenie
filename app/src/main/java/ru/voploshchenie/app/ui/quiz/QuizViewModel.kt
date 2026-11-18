package ru.voploshchenie.app.ui.quiz

import ru.voploshchenie.app.domain.model.TestScreen
import ru.voploshchenie.app.domain.model.TestConfig
import ru.voploshchenie.app.domain.model.TestSessionState
import ru.voploshchenie.app.domain.repository.TestSessionRepository
import ru.voploshchenie.app.domain.scoring.isScreenComplete
import ru.voploshchenie.app.domain.scoring.TestAnswers
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import ru.voploshchenie.app.domain.usecase.CompleteSession
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

data class TestProgress(val part: String, val number: Int, val total: Int, val percent: Int)

data class TestState(
    val screens: List<TestScreen> = emptyList(),
    val index: Int = 0,
    val session: TestSessionState? = null,
    val finished: Boolean = false,
    val error: Throwable? = null,
) {
    val screen: TestScreen? get() = screens.getOrNull(index)
    val canNext: Boolean get() = !finished && session != null && screen?.isScreenComplete(session.answers) == true
    /** На последнем экране результат можно открыть и у уже завершённой попытки — иначе повторный вход упирается в тупик. */
    val canComplete: Boolean get() = index == screens.lastIndex && session != null && screen?.isScreenComplete(session.answers) == true
    val canBack: Boolean get() = session != null && index > 0
    val progress: TestProgress? get() = screen?.let { current ->
        val partScreens = screens.filter { it.part == current.part }
        TestProgress(current.part, partScreens.indexOf(current) + 1, partScreens.size,
            if (finished) 100 else index * 100 / screens.size)
    }
}

@HiltViewModel
class QuizViewModel(
    private val config: TestConfig,
    private val repository: TestSessionRepository,
    private val resumeCurrentScreen: Boolean,
    private val completion: CompleteSession = CompleteSession(config, repository),
    private val requestedSessionId: String? = null,
    private val newSeed: () -> Long = { Random.nextLong() },
) : ViewModel() {
    @Inject
    constructor(config: TestConfig, repository: TestSessionRepository,
        completion: CompleteSession,
        savedStateHandle: SavedStateHandle,
    ) : this(config, repository, resumeCurrentScreen = false, completion = completion,
        requestedSessionId = savedStateHandle.get<String>("sessionId"))

    private val mutableState = MutableStateFlow(TestState())
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()

    init {
        action {
            val session = requestedSessionId?.let { id ->
                val created = requireNotNull(repository.session(id))
                repository.load(id)
                    ?: TestSessionState(created.shuffleSeed, currentScreenId = created.currentScreen, sessionId = id)
            } ?: repository.load() ?: TestSessionState(newSeed())
            val screens = screens(session.shuffle_seed)
            val first = screens.indexOfFirst {
                !it.isScreenComplete(session.answers) ||
                    (it.part != "profile" && it.questions.isNotEmpty() &&
                        it.questions.all { q -> q.type in listOf("text", "text_energy5") } &&
                        it.id !in session.visitedScreens)
            }
            val savedIndex = if (resumeCurrentScreen) screens.indexOfFirst { it.id == session.currentScreenId } else -1
            persist(TestState(screens,
                if (savedIndex >= 0) savedIndex else if (session.finished || first < 0) screens.lastIndex else first,
                session, session.finished || (savedIndex < 0 && first < 0)))
        }
    }

    fun updateAnswers(update: (TestAnswers) -> TestAnswers) = action {
        val current = mutableState.value
        val session = current.session ?: return@action
        persist(current.copy(session = session.copy(answers = update(session.answers)), finished = false))
    }

    fun selectPair(position: Int) = action {
        val current = mutableState.value
        val screen = current.screen ?: return@action
        val type = screen.pairTypes.getOrNull(position) ?: return@action
        val session = current.session ?: return@action
        val id = screen.questions.single().id
        val answers = session.answers
        val updated = if (screen.part == "consistency") answers.copy(checks = answers.checks + (id to type))
            else answers.copy(pairs = answers.pairs + (id to type))
        persist(current.copy(session = session.copy(answers = updated), finished = false))
    }

    fun next() = action {
        val current = mutableState.value
        if (!current.canNext) return@action
        val session = requireNotNull(current.session)
        val visited = session.visitedScreens + requireNotNull(current.screen).id
        val last = current.index == current.screens.lastIndex
        persist(current.copy(index = if (last) current.index else current.index + 1,
            session = session.copy(visitedScreens = visited), finished = last))
    }

    fun back() = action {
        val current = mutableState.value
        if (current.canBack) persist(current.copy(index = current.index - 1, finished = false))
    }

    suspend fun complete(): String = mutex.withLock {
        completion(requireNotNull(mutableState.value.session))
    }

    suspend fun awaitSaved() { mutex.withLock { } }

    private suspend fun persist(updated: TestState) {
        val session = requireNotNull(updated.session).copy(
            currentScreenId = requireNotNull(updated.screen).id, finished = updated.finished)
        val saved = repository.save(session)
        mutableState.value = updated.copy(session = saved, error = null)
    }

    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        mutex.withLock {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutableState.value = mutableState.value.copy(error = error) }
        }
    }

    private fun screens(seed: Long): List<TestScreen> {
        val random = Random(seed)
        return config.parts.flatMap { part ->
            val questions = if (part.id == "interests") part.questions.shuffled(random) else part.questions
            when (part.id) {
                "profile", "values" -> listOf(TestScreen(part.id, part.id, questions))
                "needs" -> requireNotNull(part.steps).map { step ->
                    TestScreen("${part.id}/${step.id}", part.id,
                        questions.filter { it.id in step.question_ids }, step)
                }
                else -> questions.map { question ->
                    val types = if (question.type == "pair")
                        listOf(requireNotNull(question.type_a), requireNotNull(question.type_b)).shuffled(random)
                    else emptyList()
                    TestScreen(question.id, part.id, listOf(question), pairTypes = types)
                }
            }
        }
    }
}
