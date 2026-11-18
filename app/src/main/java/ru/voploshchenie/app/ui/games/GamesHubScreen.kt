package ru.voploshchenie.app.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.voploshchenie.app.R
import ru.voploshchenie.app.domain.games.MythsRules
import ru.voploshchenie.app.ui.components.BrandHeader
import ru.voploshchenie.app.ui.theme.Spacing
import ru.voploshchenie.app.ui.theme.printTitle
import ru.voploshchenie.app.ui.theme.secondaryText
import ru.voploshchenie.app.ui.theme.deadline

const val GamesRoute = "games"
const val WordleGameRoute = "games/wordle"
const val MythsGameRoute = "games/myths"
const val ResumeGameRoute = "games/resume"

@Composable
internal fun GamesHubRoute(onOpen: (String) -> Unit, viewModel: GamesHubViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Возвращаемся из игры — перечитываем прогресс, чтобы статусы карточек были свежими.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    GamesHubScreen(state, onOpen)
}

@Composable
internal fun GamesHubScreen(state: GamesHubState, onOpen: (String) -> Unit = {}) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
        .padding(Spacing.screen).testTag("games_hub")) {
        BrandHeader()
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.games_title), style = MaterialTheme.typography.printTitle, color = MaterialTheme.colorScheme.onBackground)
            Text(".", style = MaterialTheme.typography.printTitle, color = MaterialTheme.colorScheme.secondaryText)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.games_sub), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        TodayStrip(state)
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GameCard(0, "wordle", R.string.games_wordle, stringResource(R.string.games_wordle_text), MaterialTheme.colorScheme.secondary,
                chip(state.wordle, R.string.chip_new, R.string.chip_wordle_live, doneRes = if (state.wordle.a > 0) R.string.chip_wordle_won else R.string.chip_wordle_lost)) { onOpen(WordleGameRoute) }
            GameCard(1, "myths", R.string.games_myths, stringResource(R.string.games_myths_text, MythsRules.PER_DAY), MaterialTheme.colorScheme.primary,
                chip(state.myths, R.string.chip_myths_new, R.string.chip_myths_live, doneRes = R.string.chip_myths_done, newArg = MythsRules.PER_DAY)) { onOpen(MythsGameRoute) }
            GameCard(2, "resume", R.string.games_resume, stringResource(R.string.games_resume_text), MaterialTheme.colorScheme.deadline,
                chip(state.resume, R.string.chip_new, R.string.chip_resume_live, doneRes = R.string.chip_resume_done)) { onOpen(ResumeGameRoute) }
        }
        Spacer(Modifier.height(22.dp))
        Text(stringResource(if (state.done == 3) R.string.games_foot_done else R.string.games_foot_new), Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

private data class ChipSpec(val kind: SummaryKind, val text: String)

@Composable
private fun chip(summary: GameSummary, newRes: Int, liveRes: Int, doneRes: Int, newArg: Int? = null): ChipSpec = when (summary.kind) {
    SummaryKind.New -> ChipSpec(summary.kind, if (newArg != null) stringResource(newRes, newArg) else stringResource(newRes))
    SummaryKind.Live -> ChipSpec(summary.kind, if (liveRes == R.string.chip_resume_live) stringResource(liveRes, summary.a) else stringResource(liveRes, summary.a, summary.b))
    SummaryKind.Done -> ChipSpec(summary.kind, when (doneRes) {
        R.string.chip_wordle_won -> stringResource(doneRes, summary.a)
        R.string.chip_wordle_lost -> stringResource(doneRes)
        else -> stringResource(doneRes, summary.a, summary.b)
    })
}

@Composable
private fun TodayStrip(state: GamesHubState) {
    Surface(Modifier.fillMaxWidth(), shape = leaf(18), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.games_today).uppercase(), color = MaterialTheme.colorScheme.secondaryText, style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 1.2.sp), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            listOf(state.wordle, state.myths, state.resume).forEach { summary ->
                Box(Modifier.padding(end = 6.dp).width(26.dp).height(6.dp)
                    .background(if (summary.kind == SummaryKind.Done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant))
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.games_done_of, state.done), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun GameCard(order: Int, art: String, title: Int, text: String, accent: Color, chip: ChipSpec, onClick: () -> Unit) {
    // Вход: карточки по очереди выезжают снизу и проявляются.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(order * 120L); enter.animateTo(1f, tween(560, easing = FastOutSlowInEasing)) }
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("game_card_$art")
        .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 40f },
        shape = leaf(28), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(84.dp).background(accent.copy(alpha = 0.12f))) { GameArt(art, Modifier.fillMaxSize()) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 23.sp), fontWeight = FontWeight.ExtraBold)
                    Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 18.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChipView(chip)
                }
                Icon(painterResource(R.drawable.arrow_right), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChipView(chip: ChipSpec) {
    val scheme = MaterialTheme.colorScheme
    val (background, content, border) = when (chip.kind) {
        SummaryKind.New -> Triple(Color.Transparent, scheme.secondaryText, scheme.secondaryText)
        SummaryKind.Live -> Triple(Color.Transparent, scheme.primary, scheme.primary)
        SummaryKind.Done -> Triple(scheme.primary, scheme.onPrimary, scheme.primary)
    }
    Surface(color = background, contentColor = content, border = BorderStroke(1.dp, border)) {
        Text(chip.text.uppercase(), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
    }
}

/** Иллюстрации карточек, нарисованные на Canvas: плитки Wordle, карточки «✓ / ✕» и лист резюме с лупой. */
@Composable
private fun GameArt(kind: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val ok = scheme.secondary
    val mid = scheme.primary
    val off = scheme.outlineVariant
    val line = scheme.onSurfaceVariant
    val bad = scheme.error
    val page = scheme.surface
    Canvas(modifier) {
        val u = size.width / 84f
        fun square(x: Float, y: Float, w: Float, color: Color) = drawRect(color, Offset(x * u, y * u), Size(w * u, w * u))
        when (kind) {
            "wordle" -> {
                val colors = listOf(ok, mid, off, mid, ok, ok, ok, off, page)
                colors.forEachIndexed { i, c ->
                    val x = 6f + (i % 3) * 25f
                    val y = 6f + (i / 3) * 25f
                    square(x, y, 22f, c)
                    if (c == page) drawRect(line, Offset(x * u, y * u), Size(22f * u, 22f * u), style = Stroke(1.5f * u))
                }
            }
            "myths" -> {
                drawRect(page, Offset(8 * u, 16 * u), Size(40 * u, 52 * u)); drawRect(line, Offset(8 * u, 16 * u), Size(40 * u, 52 * u), style = Stroke(1.5f * u))
                drawLine(bad, Offset(20 * u, 34 * u), Offset(36 * u, 50 * u), 4.5f * u, StrokeCap.Round)
                drawLine(bad, Offset(36 * u, 34 * u), Offset(20 * u, 50 * u), 4.5f * u, StrokeCap.Round)
                drawRect(ok, Offset(36 * u, 20 * u), Size(40 * u, 52 * u))
                drawLine(scheme.onSecondary, Offset(47 * u, 46 * u), Offset(55 * u, 54 * u), 4.5f * u, StrokeCap.Round)
                drawLine(scheme.onSecondary, Offset(55 * u, 54 * u), Offset(69 * u, 36 * u), 4.5f * u, StrokeCap.Round)
            }
            else -> {
                drawRect(page, Offset(14 * u, 6 * u), Size(50 * u, 68 * u)); drawRect(line, Offset(14 * u, 6 * u), Size(50 * u, 68 * u), style = Stroke(1.5f * u))
                drawRect(off, Offset(22 * u, 16 * u), Size(34 * u, 5 * u))
                drawRect(bad.copy(alpha = 0.4f), Offset(22 * u, 28 * u), Size(34 * u, 9 * u))
                drawRect(off, Offset(22 * u, 44 * u), Size(26 * u, 5 * u))
                drawRect(off, Offset(22 * u, 56 * u), Size(30 * u, 5 * u))
                drawCircle(mid, 11 * u, Offset(58 * u, 58 * u), style = Stroke(4 * u))
                drawLine(mid, Offset(66 * u, 66 * u), Offset(76 * u, 76 * u), 5 * u, StrokeCap.Round)
            }
        }
    }
}

/** Баннер «Игры дня» для экранов «Путь» и «Профиль»: заметная плашка с плитками и стрелкой. */
@Composable
fun GamesBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth().testTag("games_banner"), shape = leaf(22), color = scheme.primaryContainer,
        border = BorderStroke(1.dp, scheme.primary)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(scheme.primary))
            Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(3) { i -> Box(Modifier.size(15.dp).background(listOf(scheme.secondary, scheme.primary, scheme.outlineVariant)[i])) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(3) { i -> Box(Modifier.size(15.dp).background(listOf(scheme.primary, scheme.secondary, scheme.secondary)[i])) } }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.games_banner_title), style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp), color = scheme.onBackground, fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(R.string.games_banner_sub), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                Box(Modifier.size(38.dp).background(scheme.primary, leaf(13)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.arrow_right), null, Modifier.size(18.dp), tint = scheme.onPrimary)
                }
            }
        }
    }
}
