package ru.voploshchenie.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.voploshchenie.app.R
import ru.voploshchenie.app.ui.components.BrandHeader
import ru.voploshchenie.app.ui.components.PrimaryButton
import ru.voploshchenie.app.ui.result.sphereResource
import ru.voploshchenie.app.ui.path.PathUiState
import ru.voploshchenie.app.ui.path.PathViewModel
import ru.voploshchenie.app.ui.theme.Spacing
import ru.voploshchenie.app.ui.theme.QuizSpacing
import ru.voploshchenie.app.ui.theme.PrintSpacing
import ru.voploshchenie.app.ui.result.SphereDots
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment

@Composable
fun ProfileRoute(onStartTest: (String) -> Unit, onRecommendations: () -> Unit, path: PathViewModel = hiltViewModel(), viewModel: ProfileViewModel = hiltViewModel(), onResult: (String) -> Unit = {}, onGames: () -> Unit = {}) {
    val state by path.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, path) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) path.refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val hasDraft by path.hasDraft.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    ProfileScreen(state, onRepeatTest = { scope.launch { onStartTest(viewModel.startNewSession()) } },
        onRecommendations, path::refresh, hasDraft, onResult, onGames)
}

@Composable
fun ProfileScreen(state: PathUiState, onRepeatTest: () -> Unit, onRecommendations: () -> Unit, onRetry: () -> Unit = {}, hasDraft: Boolean = false, onResult: (String) -> Unit = {}, onGames: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.step * 2)) {
        BrandHeader()
        if (state !is PathUiState.Content) Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineLarge)
        when (state) {
            PathUiState.Loading -> {
                CircularProgressIndicator(Modifier.testTag("profile_loading"))
                Spacer(Modifier.weight(1f))
                PrimaryButton(stringResource(if (hasDraft) R.string.continue_test else R.string.take_test), onRepeatTest)
            }
            PathUiState.Error -> TextButton(onClick = onRetry) { Text(stringResource(R.string.path_load_error)) }
            PathUiState.Empty -> {
                Text(stringResource(R.string.profile_no_result), style = MaterialTheme.typography.bodyLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.step * 3)) {
                    ru.voploshchenie.app.ui.components.ProfileEmptyArt(Modifier.padding(top = Spacing.step * 2))
                    ru.voploshchenie.app.ui.components.ProfilePreview()
                }
                PrimaryButton(stringResource(if (hasDraft) R.string.continue_test else R.string.take_test), onRepeatTest)
            }
            is PathUiState.Content -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.step * 2)) {
                    val data = state.data
                    val interests = data.topSpheres.map { stringResource(sphereResource(it)) }.joinToString(" · ")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        ProfileAvatar(data.name.orEmpty())
                        Spacer(Modifier.width(Spacing.step * 2))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.step)) {
                            Text(data.name?.takeIf(String::isNotBlank) ?: stringResource(R.string.profile_unnamed),
                                style = MaterialTheme.typography.headlineMedium.copy(fontSize = PrintSpacing.nameFont))
                            Text(stringResource(labelResource(data.labelCode)), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground)
                    Text(stringResource(R.string.profile_direction).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    Text(stringResource(sphereResource(data.topSphere)), style = MaterialTheme.typography.titleLarge)
                    data.percent?.let { SphereDots(it, expanded = true) }
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.step)) {
                        Text(stringResource(R.string.profile_interests), style = MaterialTheme.typography.titleMedium)
                        Text(interests, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.step)) {
                        Text(stringResource(R.string.profile_next_step), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.profile_next_step_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                    state.sessionId?.let { id -> TextButton(onClick = { onResult(id) }, contentPadding = PaddingValues(Spacing.none)) { Text(stringResource(R.string.open_result)) } }
                    TextButton(onClick = onRepeatTest, contentPadding = PaddingValues(0.dp)) { Text(stringResource(if (hasDraft) R.string.continue_test else R.string.profile_repeat_test)) }
                }
                ru.voploshchenie.app.ui.games.GamesBanner(onGames)
                PrimaryButton(stringResource(R.string.path_cta_collection), onRecommendations)
            }
        }
    }
}

private fun labelResource(code: Char?): Int = when (code) {
    'R' -> R.string.label_practical
    'I' -> R.string.label_ideas_researcher
    'A' -> R.string.label_creator
    'S' -> R.string.label_mentor
    'E' -> R.string.label_organizer
    'C' -> R.string.label_systematizer
    else -> R.string.label_practical
}

@Composable
private fun ProfileAvatar(name: String) {
    val shape = RoundedCornerShape(topEnd = PrintSpacing.avatarCorner, bottomStart = PrintSpacing.avatarCorner)
    val initials = name.trim().split(Regex("\\s+")).filter(String::isNotEmpty).take(2).joinToString("") { it.first().uppercase() }
    Box(Modifier.size(PrintSpacing.avatarWidth, PrintSpacing.avatarHeight)) {
        Surface(Modifier.align(Alignment.TopEnd).size(PrintSpacing.avatarFaceWidth, PrintSpacing.avatarFaceHeight), shape = shape, color = androidx.compose.ui.graphics.Color.Transparent, border = BorderStroke(QuizSpacing.stroke, MaterialTheme.colorScheme.onBackground)) {}
        Surface(Modifier.align(Alignment.BottomStart).size(PrintSpacing.avatarFaceWidth, PrintSpacing.avatarFaceHeight), shape = shape, color = MaterialTheme.colorScheme.primary) {
            Box(contentAlignment = Alignment.Center) { Text(initials, style = MaterialTheme.typography.displayLarge.copy(fontSize = PrintSpacing.avatarFont), color = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}
