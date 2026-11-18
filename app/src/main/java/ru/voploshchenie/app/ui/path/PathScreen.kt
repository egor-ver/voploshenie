package ru.voploshchenie.app.ui.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.voploshchenie.app.R
import ru.voploshchenie.app.domain.model.PathProfileData
import ru.voploshchenie.app.ui.components.BrandHeader
import ru.voploshchenie.app.ui.components.PrimaryButton
import ru.voploshchenie.app.ui.result.sphereResource
import ru.voploshchenie.app.ui.theme.Spacing
import androidx.compose.ui.draw.drawBehind
import ru.voploshchenie.app.ui.theme.QuizSpacing
import ru.voploshchenie.app.ui.theme.PrintSpacing
import ru.voploshchenie.app.ui.theme.printTitle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.background

@Composable
fun PathRoute(onStartTest: () -> Unit, onRecommendations: () -> Unit, viewModel: PathViewModel = hiltViewModel(), onResult: (String) -> Unit = {}, onGames: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val hasDraft by viewModel.hasDraft.collectAsStateWithLifecycle()
    PathScreen(state, onStartTest, onRecommendations, viewModel::refresh, hasDraft, onResult, onGames)
}

@Composable
fun PathScreen(state: PathUiState, onStartTest: () -> Unit, onRecommendations: () -> Unit, onRetry: () -> Unit = {}, hasDraft: Boolean = false, onResult: (String) -> Unit = {}, onGames: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.step * 2)) {
        BrandHeader()
        Text(stringResource(R.string.path_title), style = MaterialTheme.typography.printTitle)
        Text(stringResource(R.string.path_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (state) {
            PathUiState.Loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).testTag("path_loading"))
                Spacer(Modifier.weight(1f))
                PrimaryButton(stringResource(if (hasDraft) R.string.continue_test else R.string.take_test), onStartTest)
            }
            PathUiState.Error -> {
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.path_load_error), color = MaterialTheme.colorScheme.error)
                PrimaryButton(stringResource(R.string.retry), onRetry)
            }
            PathUiState.Empty -> {
                PathTimeline(null, Modifier.weight(1f))
                ru.voploshchenie.app.ui.games.GamesBanner(onGames)
                PrimaryButton(stringResource(if (hasDraft) R.string.continue_test else R.string.take_test), onStartTest)
            }
            is PathUiState.Content -> {
                PathTimeline(state.data, Modifier.weight(1f))
                state.sessionId?.let { id -> TextButton(onClick = { onResult(id) }) { Text(stringResource(R.string.open_result)) } }
                if (hasDraft) TextButton(onClick = onStartTest) { Text(stringResource(R.string.continue_test)) }
                ru.voploshchenie.app.ui.games.GamesBanner(onGames)
                PrimaryButton(stringResource(R.string.path_cta_collection), onRecommendations)
            }
        }
    }
}

@Composable
private fun PathTimeline(data: PathProfileData?, modifier: Modifier = Modifier) {
    val titles = listOf(R.string.path_step_test, R.string.path_step_skills, R.string.path_step_opportunities, R.string.path_step_internship, R.string.path_step_work)
    val descriptions = listOf(R.string.path_test_description, R.string.path_try_project, R.string.path_opportunity_description, R.string.path_internship_description, R.string.path_work_description)
    Box(modifier.fillMaxWidth()) {
        val accent = MaterialTheme.colorScheme.primary
        PathTimelineBackground()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(Spacing.none)) {
            titles.forEachIndexed { index, title ->
                PathTimelineStep(index, title, descriptions[index], data, accent)
            }
        }
    }
}

@Composable
private fun PathTimelineBackground() {
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)
    Canvas(Modifier.fillMaxSize()) {
        val step = PrintSpacing.grid.toPx()
        for (x in 0..(size.width / step).toInt()) drawLine(grid, Offset(x * step, 0f), Offset(x * step, size.height), 1.dp.toPx())
        for (y in 0..(size.height / step).toInt()) drawLine(grid, Offset(0f, y * step), Offset(size.width, y * step), 1.dp.toPx())

    }
}

@Composable
private fun PathTimelineStep(index: Int, title: Int, description: Int, data: PathProfileData?, accent: androidx.compose.ui.graphics.Color) {
    val complete = index == 0 && data != null
    val current = (data == null && index == 0) || (data != null && index == 1)
    val railColor = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().heightIn(min = if (current) PrintSpacing.pathCurrentHeight else if (index == 0) PrintSpacing.pathFirstHeight else PrintSpacing.pathStepHeight)
        .drawBehind {
            val x = (PrintSpacing.rail - PrintSpacing.leafCorner).toPx() / 2
            val currentX = x + PrintSpacing.currentNode.toPx()
            val line = Path().apply {
                moveTo(if (index == 1 && data != null) currentX else x, if (index == 0) PrintSpacing.grid.toPx() else 0f)
                when {
                    index == 0 && data != null -> { lineTo(x, size.height * .7f); lineTo(currentX, size.height * .7f); lineTo(currentX, size.height) }
                    index == 1 && data != null -> { lineTo(currentX, size.height * .7f); lineTo(x, size.height * .7f); lineTo(x, size.height) }
                    else -> lineTo(x, size.height)
                }
            }
            drawPath(line, if (complete) accent else railColor, style = Stroke(QuizSpacing.stroke.toPx() * if (complete) 2.5f else 1.5f,
                pathEffect = if (complete) null else PathEffect.dashPathEffect(floatArrayOf(Spacing.step.toPx() / 2, Spacing.step.toPx() / 2))))
        }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(PrintSpacing.rail).align(Alignment.Top).padding(top = Spacing.step * 2, start = if (current && data != null) Spacing.step * 4 else Spacing.none, end = if (current && data != null) Spacing.none else PrintSpacing.leafCorner), contentAlignment = Alignment.Center) {
            when {
                current -> Surface(Modifier.size(PrintSpacing.currentNode).testTag("path_step_${index + 1}_current"), color = MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(topEnd = PrintSpacing.leafCorner, bottomStart = PrintSpacing.leafCorner)) {}
                complete -> Surface(Modifier.size(PrintSpacing.node).testTag("path_step_${index + 1}_complete"), color = accent, shape = RectangleShape) {}
                else -> Surface(Modifier.size(PrintSpacing.node).testTag("path_step_${index + 1}_pending"), color = MaterialTheme.colorScheme.surface, shape = RectangleShape, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {}
            }
        }
        val detail = when {
            index == 0 && data != null -> stringResource(sphereResource(data.topSphere))
            index == 1 && !data?.experiment.isNullOrBlank() -> data?.experiment.orEmpty()
            else -> stringResource(description)
        }
        if (index >= 2) {
            Row(Modifier.weight(1f).padding(top = Spacing.step * 2), horizontalArrangement = Arrangement.spacedBy(Spacing.step)) {
                Text("%02d".format(index + 1), color = accent, style = MaterialTheme.typography.titleLarge)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.step / 2)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else Column(Modifier.weight(1f).then(if (current) Modifier.background(MaterialTheme.colorScheme.primaryContainer).drawBehind {
            drawLine(accent, Offset.Zero, Offset(size.width, 0f), QuizSpacing.stroke.toPx())
            drawLine(accent, Offset(0f, size.height), Offset(size.width, size.height), QuizSpacing.stroke.toPx())
        }.padding(Spacing.step * 1.5f) else Modifier), verticalArrangement = Arrangement.spacedBy(Spacing.step / 2)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("%02d".format(index + 1), Modifier.weight(1f), color = accent, style = MaterialTheme.typography.headlineMedium)
                if (current) Text(stringResource(R.string.path_now).uppercase(), Modifier.background(accent).padding(Spacing.step / 2), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.step / 2)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                if (complete) Text(stringResource(R.string.path_passed), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
            }
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
