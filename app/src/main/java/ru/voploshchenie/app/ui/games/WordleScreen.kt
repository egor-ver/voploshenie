package ru.voploshchenie.app.ui.games

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import ru.voploshchenie.app.R
import ru.voploshchenie.app.domain.games.WordleRules
import ru.voploshchenie.app.domain.model.LetterMark
import ru.voploshchenie.app.domain.model.WordleStatus
import ru.voploshchenie.app.ui.theme.Spacing
import ru.voploshchenie.app.ui.theme.deadline

@Composable
internal fun WordleRoute(onBack: () -> Unit, onNextGame: () -> Unit, viewModel: WordleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    WordleScreen(state, onBack, onNextGame, viewModel::onKey, viewModel::onBackspace, viewModel::onEnter, viewModel::toggleHow) {
        viewModel.shareText()?.let { shareText(context, it) }
    }
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private val keyRows = listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю")

@Composable
internal fun WordleScreen(
    state: WordleUiState,
    onBack: () -> Unit = {},
    onNextGame: () -> Unit = {},
    onKey: (Char) -> Unit = {},
    onBackspace: () -> Unit = {},
    onEnter: () -> Unit = {},
    onToggleHow: () -> Unit = {},
    onShare: () -> Unit = {},
) {
    val entry = state.entry
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = Spacing.screen).testTag("wordle_screen")) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            GameTopBar(state.streak, onBack)
            Spacer(Modifier.height(8.dp))
            GameTitle(stringResource(R.string.games_wordle))
            Spacer(Modifier.height(12.dp))
            HowToPanel(stringArrayResource(R.array.wordle_how).toList(), state.howOpen, onToggleHow)
            if (entry != null) {
                Spacer(Modifier.height(12.dp))
                ClueCard(stringResource(R.string.wordle_clue, entry.topic), entry.hint, pluralStringResource(R.plurals.wordle_meta, state.length, state.length))
                Spacer(Modifier.height(12.dp))
                Legend()
                NoteLine(if (state.needLetters) pluralStringResource(R.plurals.wordle_need, state.length, state.length) else "")
                Board(state)
                if (state.finished) EndPanel(state, onNextGame, onShare)
                Spacer(Modifier.height(12.dp))
            }
        }
        if (entry != null && !state.finished) Keyboard(state, onKey, onBackspace, onEnter)
    }
}

@Composable
private fun Legend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(MaterialTheme.colorScheme.secondary, R.string.wordle_legend_correct)
        LegendItem(MaterialTheme.colorScheme.primary, R.string.wordle_legend_present)
        LegendItem(MaterialTheme.colorScheme.outlineVariant, R.string.wordle_legend_absent)
    }
}

@Composable
private fun LegendItem(color: Color, label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).background(color))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NoteLine(text: String) {
    Text(text, Modifier.fillMaxWidth().height(28.dp).padding(top = 6.dp), textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun Board(state: WordleUiState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 50.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(WordleRules.MAX_TRIES) { row ->
            val submitted = row < state.guesses.size
            val current = row == state.guesses.size && !state.finished
            val text = when { submitted -> state.guesses[row]; current -> state.input; else -> "" }
            BoardRow(
                text = text, length = state.length,
                marks = if (submitted) state.marks[row] else null,
                reveal = submitted && row == state.revealRow,
                shake = if (current) state.shakeNonce else 0,
            )
        }
    }
}

@Composable
private fun BoardRow(text: String, length: Int, marks: List<LetterMark>?, reveal: Boolean, shake: Int) {
    val shiftPx = with(LocalDensity.current) { 1.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake > 0) for (value in listOf(-8f, 7f, -5f, 4f, 0f)) offsetX.animateTo(value, tween(60))
    }
    Row(Modifier.fillMaxWidth().offset { IntOffset((offsetX.value * shiftPx).roundToInt(), 0) }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(length) { i -> Tile(text.getOrNull(i), marks?.get(i), reveal, i) }
    }
}

@Composable
private fun RowScope.Tile(letter: Char?, mark: LetterMark?, reveal: Boolean, index: Int) {
    val rotation = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    var colored by remember { mutableStateOf(!reveal) }
    LaunchedEffect(reveal) {
        if (reveal) {
            colored = false
            delay(index * 300L)
            rotation.animateTo(90f, tween(170))
            colored = true
            rotation.animateTo(0f, tween(170))
        } else colored = true
    }
    LaunchedEffect(letter) {
        if (letter != null && mark == null) { pop.snapTo(0.86f); pop.animateTo(1f, tween(140)) }
    }
    val scheme = MaterialTheme.colorScheme
    val shown = if (colored) mark else null
    val background = when (shown) {
        LetterMark.Correct -> scheme.secondary
        LetterMark.Present -> scheme.primary
        LetterMark.Absent -> scheme.outlineVariant
        null -> scheme.surface
    }
    val textColor = when (shown) {
        LetterMark.Correct -> scheme.onSecondary
        LetterMark.Present -> scheme.onPrimary
        LetterMark.Absent -> scheme.onSurfaceVariant
        null -> scheme.onSurface
    }
    val border = when {
        shown != null -> background
        letter != null -> scheme.onSurface
        else -> scheme.outlineVariant
    }
    val density = LocalDensity.current.density
    Box(
        Modifier.weight(1f).aspectRatio(1f)
            .graphicsLayer { rotationX = rotation.value; scaleX = pop.value; scaleY = pop.value; cameraDistance = 12f * density }
            .background(background).then(Modifier.background(Color.Transparent))
            .semantics { contentDescription = letter?.toString() ?: "" },
        contentAlignment = Alignment.Center,
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Transparent, border = BorderStroke(1.5.dp, border)) {}
        Text(letter?.uppercaseChar()?.toString() ?: "", color = textColor, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun Keyboard(state: WordleUiState, onKey: (Char) -> Unit, onBackspace: () -> Unit, onEnter: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp).testTag("wordle_keyboard"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keyRows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (index == 2) EnterKey(stringResource(R.string.wordle_enter), state.length > 0 && state.input.length == state.length, onEnter)
                row.forEach { letter -> LetterKey(letter, state.keyStates[letter], onKey) }
                if (index == 2) WideKey(null, onBackspace)
            }
        }
    }
}

@Composable
private fun RowScope.LetterKey(letter: Char, mark: LetterMark?, onKey: (Char) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(when (mark) {
        LetterMark.Correct -> scheme.secondary
        LetterMark.Present -> scheme.primary
        LetterMark.Absent -> scheme.outlineVariant
        null -> scheme.surface
    }, tween(250), label = "key")
    val content = when (mark) {
        LetterMark.Correct -> scheme.onSecondary
        LetterMark.Present -> scheme.onPrimary
        LetterMark.Absent -> scheme.onSurfaceVariant
        null -> scheme.onSurface
    }
    Surface(onClick = { onKey(letter) }, modifier = Modifier.weight(1f).height(50.dp), color = background, contentColor = content,
        border = BorderStroke(1.dp, if (mark == null) scheme.outline else background)) {
        Box(contentAlignment = Alignment.Center) { Text(letter.uppercaseChar().toString(), fontWeight = FontWeight.Bold, fontSize = 15.sp) }
    }
}

/** «Ввод» выделен цветом: бледный, пока слово не набрано целиком, и яркий, когда его можно отправлять. */
@Composable
private fun RowScope.EnterKey(label: String, ready: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (ready) scheme.secondary else scheme.secondary.copy(alpha = 0.28f), tween(200), label = "enter-fill")
    Surface(onClick = onClick, modifier = Modifier.weight(2.3f).height(50.dp).testTag("wordle_enter"), color = fill,
        contentColor = if (ready) scheme.onSecondary else scheme.onSurface, border = BorderStroke(1.dp, scheme.secondary)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun RowScope.WideKey(label: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.weight(1.6f).height(50.dp), color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Box(contentAlignment = Alignment.Center) {
            if (label != null) Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            else Icon(painterResource(R.drawable.arrow_right), stringResource(R.string.wordle_backspace), Modifier.size(20.dp).graphicsLayer { rotationZ = 180f })
        }
    }
}

@Composable
private fun EndPanel(state: WordleUiState, onNextGame: () -> Unit, onShare: () -> Unit) {
    val entry = state.entry ?: return
    val won = state.status == WordleStatus.Won
    // Панель появляется после переворота плиток, а если партию просто открыли — сразу.
    var visible by remember { mutableStateOf(state.revealRow < 0) }
    LaunchedEffect(state.revealRow) { if (state.revealRow >= 0) { delay(state.length * 300L + 700); visible = true } }
    val tone = if (won) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.deadline
    AnimatedVisibility(visible, enter = fadeIn(tween(450)) + slideInVertically(tween(500, easing = FastOutSlowInEasing)) { it / 6 } + expandVertically(tween(400))) {
        Surface(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("wordle_end"), shape = leaf(28), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, tone)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text((if (won) stringResource(R.string.wordle_won, state.guesses.size, WordleRules.MAX_TRIES) else stringResource(R.string.wordle_lost)).uppercase(),
                    color = tone, style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 1.2.sp), fontWeight = FontWeight.Bold)
                Text(entry.shown.uppercase(), style = MaterialTheme.typography.headlineLarge.copy(fontSize = 44.sp, lineHeight = 44.sp, letterSpacing = (-1.6).sp, fontWeight = FontWeight.ExtraBold))
                StatsRow(state)
                GameButton(stringResource(R.string.wordle_share), onShare, accent = true)
                GameLink(stringResource(R.string.wordle_next_game), onNextGame)
            }
        }
    }
}

@Composable
private fun StatsRow(state: WordleUiState) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().background(Color.Transparent)) {
        Stat(state.streak.toString(), stringResource(R.string.wordle_stat_streak), Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(52.dp).background(line))
        Stat(state.stats.best.toString(), stringResource(R.string.wordle_stat_best), Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(52.dp).background(line))
        Stat(state.stats.played.toString(), stringResource(R.string.wordle_stat_played), Modifier.weight(1f))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp, lineHeight = 28.sp), fontWeight = FontWeight.ExtraBold)
        Text(label.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp, letterSpacing = 1.2.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
