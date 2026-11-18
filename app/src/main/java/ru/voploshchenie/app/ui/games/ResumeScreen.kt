package ru.voploshchenie.app.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.voploshchenie.app.R
import ru.voploshchenie.app.domain.model.ResumeItem
import ru.voploshchenie.app.ui.theme.Spacing
import ru.voploshchenie.app.ui.theme.deadline
import ru.voploshchenie.app.ui.theme.secondaryText

@Composable
internal fun ResumeRoute(onBack: () -> Unit, onOpportunities: () -> Unit, viewModel: ResumeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResumeScreen(state, onBack, onOpportunities, viewModel::toggle, viewModel::check, viewModel::toggleHow)
}

@Composable
internal fun ResumeScreen(
    state: ResumeUiState,
    onBack: () -> Unit = {},
    onOpportunities: () -> Unit = {},
    onToggle: (Int) -> Unit = {},
    onCheck: () -> Unit = {},
    onToggleHow: () -> Unit = {},
) {
    val case = state.case
    val checked = state.progress.checked
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("resume_screen")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            GameTopBar(state.streak, onBack)
            Spacer(Modifier.height(8.dp))
            GameTitle(stringResource(R.string.games_resume))
            Spacer(Modifier.height(12.dp))
            HowToPanel(stringArrayResource(R.array.resume_how).toList(), state.howOpen, onToggleHow)
            if (case != null) {
                Spacer(Modifier.height(12.dp))
                ClueCard(stringResource(R.string.resume_clue_kicker, case.role), stringResource(R.string.resume_clue_text), stringResource(R.string.resume_clue_meta, case.weakCount))
                Spacer(Modifier.height(16.dp))
                Sheet(state, onToggle)
                Spacer(Modifier.height(16.dp))
                if (checked) Finish(state, onOpportunities, onBack)
                Spacer(Modifier.height(if (checked) 24.dp else 96.dp))
            }
        }
        if (case != null && !checked) CheckBar(state.progress.picked.size, onCheck, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Sheet(state: ResumeUiState, onToggle: (Int) -> Unit) {
    val case = state.case ?: return
    Surface(Modifier.fillMaxWidth(), shape = leaf(24), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 8.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 0.dp)) {
                Text(case.name, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 20.sp, lineHeight = 22.sp), fontWeight = FontWeight.ExtraBold)
                Text(case.sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface))
            }
            var lineIndex = -1
            case.items.forEach { item ->
                when (item) {
                    is ResumeItem.Heading -> Text(item.title.uppercase(), Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
                        color = MaterialTheme.colorScheme.secondaryText, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    is ResumeItem.Line -> {
                        lineIndex += 1
                        LineRow(lineIndex, item, state, onToggle)
                    }
                }
            }
        }
    }
}

@Composable
private fun LineRow(index: Int, line: ResumeItem.Line, state: ResumeUiState, onToggle: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val picked = index in state.progress.picked
    val checked = state.progress.checked
    val tone = when {
        !checked -> if (picked) scheme.primary else Color.Transparent
        line.weak && picked -> scheme.secondary
        line.weak -> scheme.error
        picked -> scheme.deadline
        else -> Color.Transparent
    }
    val background by animateColorAsState(if (tone == Color.Transparent) Color.Transparent else tone.copy(alpha = if (checked) 0.14f else 0.10f), tween(250), label = "line-bg")
    val bar by animateColorAsState(tone, tween(250), label = "line-bar")
    val box by animateColorAsState(if (picked) tone else Color.Transparent, tween(250), label = "line-box")
    Column {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 2.dp).background(background)
            .clickable(enabled = !checked, role = Role.Checkbox) { onToggle(index) }.testTag("resume_line_$index"),
            verticalAlignment = Alignment.Top) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(bar))
            Row(Modifier.padding(start = 6.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
                Surface(Modifier.size(20.dp), color = box, border = BorderStroke(1.5.dp, if (tone == Color.Transparent) scheme.outline else tone)) {
                    Box(contentAlignment = Alignment.Center) {
                        val mark = when {
                            !checked -> if (picked) "✓" else ""
                            line.weak -> if (picked) "✓" else "✕"
                            picked -> "!"
                            else -> ""
                        }
                        val markColor = if (checked && line.weak && !picked) scheme.error else if (picked && !checked) scheme.onPrimary else if (checked && picked && line.weak) scheme.onSecondary else if (checked && picked) scheme.onPrimary else scheme.onSurface
                        Text(mark, color = markColor, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(line.text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.5.sp, lineHeight = 20.sp), color = scheme.onSurface)
            }
        }
        AnimatedVisibility(checked && line.weak, enter = fadeIn(tween(350)) + expandVertically(tween(400, easing = FastOutSlowInEasing))) {
            Column(Modifier.padding(start = 40.dp, end = 4.dp, bottom = 8.dp)) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(scheme.outlineVariant))
                    Column(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
                        Text(buildWhy(stringResource(R.string.resume_why), line.why.orEmpty()), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 19.sp), color = scheme.onSurfaceVariant)
                        line.fix?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(R.string.resume_fix, it), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 19.sp), color = scheme.secondaryText, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun buildWhy(head: String, text: String) = androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
    append(head)
    pop()
    append(" $text")
}

@Composable
private fun CheckBar(count: Int, onCheck: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.padding(horizontal = Spacing.screen, vertical = 12.dp).fillMaxWidth(), shape = leaf(18), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 6.dp) {
        Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.resume_marked, count), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GameButton(stringResource(R.string.resume_check), onCheck, Modifier.width(170.dp).height(48.dp), enabled = count > 0)
        }
    }
}

@Composable
private fun Finish(state: ResumeUiState, onOpportunities: () -> Unit, onGames: () -> Unit) {
    val score = state.score ?: return
    val verdict = when {
        score.perfect -> R.string.resume_verdict_perfect
        score.hits == score.total -> R.string.resume_verdict_all
        score.hits > 0 -> R.string.resume_verdict_some
        else -> R.string.resume_verdict_none
    }
    Surface(Modifier.fillMaxWidth().testTag("resume_score"), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${score.hits}", color = MaterialTheme.colorScheme.primary, fontSize = 64.sp, lineHeight = 56.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-3).sp)
                Text("/${score.total}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 3.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(verdict), style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, lineHeight = 19.sp))
                    if (score.falseAlarms > 0) Text(stringResource(R.string.resume_extra, score.falseAlarms), Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.deadline, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    GameButton(stringResource(R.string.games_open_opportunities), onOpportunities, accent = true)
    GameLink(stringResource(R.string.games_to_games), onGames)
}
