package ru.voploshchenie.app.domain.model

import kotlinx.serialization.Serializable
import ru.voploshchenie.app.domain.scoring.TestAnswers

@Serializable
data class TestSessionState(
    val shuffle_seed: Long,
    val answers: TestAnswers = TestAnswers(),
    val visitedScreens: Set<String> = emptySet(),
    val currentScreenId: String? = null,
    val finished: Boolean = false,
    val sessionId: String? = null,
)
