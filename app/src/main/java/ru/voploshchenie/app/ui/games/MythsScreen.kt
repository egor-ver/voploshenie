package ru.voploshchenie.app.ui.games

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.snap
import androidx.compose.runtime.remember
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.voploshchenie.app.R
import ru.voploshchenie.app.domain.games.MythsRules
import ru.voploshchenie.app.domain.model.Myth
import ru.voploshchenie.app.ui.theme.Spacing
import ru.voploshchenie.app.ui.theme.secondaryText

@Composable
internal fun MythsRoute(onBack: () -> Unit, onWordle: () -> Unit, onOpportunities: () -> Unit, viewModel: MythsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MythsScreen(state, onBack, onWordle, onOpportunities, viewModel::say, viewModel::next, viewModel::toggleHow)
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun MythsScreen(
    state: MythsUiState,
    onBack: () -> Unit = {},
    onWordle: () -> Unit = {},
    onOpportunities: () -> Unit = {},
    onSay: (Boolean) -> Unit = {},
    onNext: () -> Unit = {},
    onToggleHow: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
        .padding(horizontal = Spacing.screen).testTag("myths_screen")) {
        GameTopBar(state.streak, onBack)
        Spacer(Modifier.height(8.dp))
        GameTitle(stringResource(if (state.finished) R.string.myths_results else R.string.games_myths))
        if (!state.finished) {
            Spacer(Modifier.height(12.dp))
            HowToPanel(stringArrayResource(R.array.myths_how).toList(), state.howOpen, onToggleHow)
        }
        Spacer(Modifier.height(16.dp))
        if (state.loaded) Ruler(state)
        Spacer(Modifier.height(18.dp))
        if (!state.loaded) return@Column
        if (state.finished) Results(state, onWordle, onOpportunities, onBack)
        else {
            AnimatedContent(
                targetState = state.progress.index,
                transitionSpec = {
                    (fadeIn(tween(420)) + slideInHorizontally(tween(480, easing = FastOutSlowInEasing)) { it / 5 }) togetherWith fadeOut(tween(160))
                },
                label = "myth-card",
            ) { index -> state.cards.getOrNull(index)?.let { MythCard(it, index) } }
            Spacer(Modifier.height(16.dp))
            Choices(state, onSay)
            // Вердикт при уходе со сворачиванием должен остаться от отвеченной карточки, а не подхватить следующую.
            val held = remember { arrayOfNulls<Pair<Myth, Boolean?>>(1) }
            val myth = state.current
            if (state.answered && myth != null) held[0] = myth to state.progress.answers.getOrNull(state.progress.index)
            AnimatedVisibility(state.answered, enter = fadeIn(tween(350)) + expandVertically(tween(380, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(220))) {
                held[0]?.let { (shown, said) -> Verdict(shown, said, state.progress.index == MythsRules.PER_DAY - 1, onNext) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Ruler(state: MythsUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(MythsRules.PER_DAY) { i ->
            val answered = i < state.progress.answers.size
            val target = when {
                answered && state.progress.answers[i] == state.cards.getOrNull(i)?.truth -> MaterialTheme.colorScheme.secondary
                answered -> MaterialTheme.colorScheme.error
                i == state.progress.index && !state.finished -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            val color by animateColorAsState(target, tween(350), label = "ruler")
            Box(Modifier.weight(1f).height(6.dp).background(color))
        }
    }
}

@Composable
private fun MythCard(myth: Myth, index: Int) {
    Surface(Modifier.fillMaxWidth().testTag("myth_card"), shape = leaf(28), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Box {
            Text("%02d".format(index + 1), Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), fontSize = 76.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-3).sp)
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                Text(stringResource(R.string.myths_kicker, index + 1, MythsRules.PER_DAY).uppercase(), color = MaterialTheme.colorScheme.secondaryText,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 1.2.sp), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(48.dp))
                Text(myth.statement, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 25.sp, lineHeight = 29.sp, letterSpacing = (-0.8).sp, fontWeight = FontWeight.ExtraBold))
            }
        }
    }
}

@Composable
private fun Choices(state: MythsUiState, onSay: (Boolean) -> Unit) {
    val myth = state.current ?: return
    val said = state.progress.answers.getOrNull(state.progress.index)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AnswerButton(true, stringResource(R.string.myths_true), myth, said, state.answered, onSay, Modifier.weight(1f))
        AnswerButton(false, stringResource(R.string.myths_false), myth, said, state.answered, onSay, Modifier.weight(1f))
    }
}

@Composable
private fun AnswerButton(value: Boolean, label: String, myth: Myth, said: Boolean?, answered: Boolean, onSay: (Boolean) -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val picked = answered && said == value
    val key = answered && myth.truth == value
    val right = answered && said == myth.truth
    val tone = if (value) scheme.secondary else scheme.error
    val target = when { picked && right -> scheme.secondary.copy(alpha = 0.24f); picked -> scheme.error.copy(alpha = 0.14f); else -> Color.Transparent }
    val background by animateColorAsState(target, if (answered) tween(300) else snap(), label = "myth-bg")
    val alpha by animateFloatAsState(if (answered && !picked && !key) 0.42f else 1f, if (answered) tween(300) else snap(), label = "myth-alpha")
    val borderColor by animateColorAsState(if (picked || key) tone else scheme.outline, if (answered) tween(300) else snap(), label = "myth-border")
    Surface(onClick = { onSay(value) }, enabled = !answered, modifier = modifier.height(72.dp).testTag(if (value) "myth_true" else "myth_false"),
        shape = leaf(22), color = background, contentColor = scheme.onBackground, border = BorderStroke(1.5.dp, borderColor)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(if (value) "✓" else "✕", color = tone.copy(alpha = alpha), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(10.dp))
            Text(label, color = scheme.onBackground.copy(alpha = alpha), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun Verdict(myth: Myth, said: Boolean?, last: Boolean, onNext: () -> Unit) {
    val right = said == myth.truth
    val tone = if (right) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    Column(Modifier.padding(top = 14.dp)) {
        Surface(Modifier.fillMaxWidth().testTag("myth_verdict"), color = tone.copy(alpha = 0.10f)) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(tone))
                Column(Modifier.padding(start = 14.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Stamp(stringResource(if (right) R.string.myths_verdict_right else R.string.myths_verdict_wrong), tone)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(if (myth.truth) R.string.myths_is_true else R.string.myths_is_myth),
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 22.sp, lineHeight = 24.sp), fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(myth.why, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        GameButton(stringResource(if (last) R.string.myths_show_results else R.string.myths_next), onNext)
    }
}

/** Штамп «Верно / Не угадали»: двойная рамка и лёгкий наклон. */
@Composable
internal fun Stamp(text: String, tone: Color, modifier: Modifier = Modifier) {
    Box(modifier.rotate(-5f)) {
        Surface(color = Color.Transparent, border = BorderStroke(1.dp, tone), contentColor = tone) {
            Box(Modifier.padding(3.dp)) {
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, tone), contentColor = tone) {
                    Text(text.uppercase(), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun Results(state: MythsUiState, onWordle: () -> Unit, onOpportunities: () -> Unit, onGames: () -> Unit) {
    val score = state.score
    val verdict = when { score >= 4 -> R.string.myths_verdict_high; score >= 2 -> R.string.myths_verdict_mid; else -> R.string.myths_verdict_low }
    Row(verticalAlignment = Alignment.Bottom) {
        Text("$score", color = MaterialTheme.colorScheme.primary, fontSize = 88.sp, lineHeight = 80.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-4).sp)
        Text("/${MythsRules.PER_DAY}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Spacer(Modifier.width(18.dp))
        Text(stringResource(verdict), Modifier.weight(1f).padding(bottom = 6.dp), style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp, lineHeight = 21.sp))
    }
    Spacer(Modifier.height(18.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onBackground))
    state.cards.forEachIndexed { i, card ->
        val ok = state.progress.answers.getOrNull(i) == card.truth
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
            Text(if (ok) "✓" else "✕", Modifier.width(26.dp), color = if (ok) MaterialTheme.colorScheme.secondaryText else MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(card.statement, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(if (card.truth) R.string.myths_true else R.string.myths_false).uppercase(), Modifier.padding(start = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
    Spacer(Modifier.height(22.dp))
    GameButton(stringResource(R.string.games_open_opportunities), onOpportunities, accent = true)
    GameLink(stringResource(R.string.myths_play_wordle), onWordle)
    GameLink(stringResource(R.string.games_to_games), onGames)
}
