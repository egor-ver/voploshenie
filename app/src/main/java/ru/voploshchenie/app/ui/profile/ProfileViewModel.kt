package ru.voploshchenie.app.ui.profile

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import ru.voploshchenie.app.domain.repository.TestSessionRepository
import ru.voploshchenie.app.domain.model.TestConfig

@HiltViewModel
class ProfileViewModel @Inject constructor(private val sessions: TestSessionRepository, private val config: TestConfig) : ViewModel() {
    suspend fun startNewSession(): String = sessions.continueTest()?.first?.id ?: sessions.start(config.version.toString()).id
}
