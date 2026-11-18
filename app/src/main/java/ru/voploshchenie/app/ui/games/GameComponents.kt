package ru.voploshchenie.app.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.voploshchenie.app.R
import ru.voploshchenie.app.ui.theme.Spacing
import ru.voploshchenie.app.ui.theme.deadline
import ru.voploshchenie.app.ui.theme.secondaryText

/** «Лист»: квадратные углы и один скруглённый верхний правый — фирменный срез дизайна «Печать и чертёж». */
internal fun leaf(radius: Int) = RoundedCornerShape(topStart = 0.dp, topEnd = radius.dp, bottomEnd = 0.dp, bottomStart = 0.dp)

/** Верх игрового экрана: «← Игры» и плашка серии. */
@Composable
internal fun GameTopBar(streak: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Surface(onClick = onBack, shape = leaf(14), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Row(Modifier.heightIn(min = 44.dp).padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.arrow_right), null, Modifier.size(18.dp).rotate(180f))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.games_back), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        StreakBadge(streak)
    }
}

@Composable
internal fun StreakBadge(streak: Int) {
    val active = streak > 0
    val tone = if (active) MaterialTheme.colorScheme.deadline else MaterialTheme.colorScheme.onSurfaceVariant
    val label = if (active) pluralStringResource(R.plurals.streak_days, streak, streak) else stringResource(R.string.streak_start)
    Surface(shape = leaf(14), color = if (active) tone.copy(alpha = 0.11f) else Color.Transparent, contentColor = tone,
        border = BorderStroke(1.dp, if (active) tone else MaterialTheme.colorScheme.outline),
        modifier = Modifier.semantics { contentDescription = label }) {
        Row(Modifier.height(36.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Flame(tone.copy(alpha = if (active) 1f else 0.55f))
            Spacer(Modifier.width(6.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), fontWeight = FontWeight.Bold)
        }
    }
}

/** Огонёк серии, нарисованный контуром (без иконочных шрифтов). */
@Composable
internal fun Flame(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, 0f)
            cubicTo(w * 0.55f, h * 0.22f, w * 0.88f, h * 0.36f, w * 0.88f, h * 0.64f)
            cubicTo(w * 0.88f, h * 0.86f, w * 0.7f, h, w * 0.5f, h)
            cubicTo(w * 0.3f, h, w * 0.12f, h * 0.86f, w * 0.12f, h * 0.64f)
            cubicTo(w * 0.12f, h * 0.48f, w * 0.26f, h * 0.38f, w * 0.34f, h * 0.28f)
            cubicTo(w * 0.36f, h * 0.4f, w * 0.44f, h * 0.44f, w * 0.44f, h * 0.44f)
            cubicTo(w * 0.4f, h * 0.24f, w * 0.46f, h * 0.1f, w * 0.5f, 0f)
            close()
        }
        drawPath(path, color, style = Fill)
    }
}

/** Свёртываемая памятка «Как играть»: при первом заходе раскрыта, дальше свёрнута. */
@Composable
internal fun HowToPanel(steps: List<String>, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val chevron by animateFloatAsState(if (open) 180f else 0f, tween(300, easing = FastOutSlowInEasing), label = "how-chevron")
    Surface(modifier.fillMaxWidth().animateContentSize(tween(340, easing = FastOutSlowInEasing)), shape = leaf(16),
        color = if (open) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
        border = BorderStroke(1.dp, if (open) accent else MaterialTheme.colorScheme.outline)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).heightIn(min = 44.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).background(accent, leaf(8)), contentAlignment = Alignment.Center) {
                    Text("?", color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.how_title), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Icon(painterResource(R.drawable.arrow_right), null, Modifier.size(18.dp).rotate(90f + chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(open, enter = fadeIn(tween(260)) + expandVertically(tween(340, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(220))) {
                Column(Modifier.padding(start = 12.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    steps.forEachIndexed { index, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                                Text("${index + 1}", color = accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(step, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }
    }
}

/** Карточка-подсказка: цветная полоса слева, мелкая подпись, крупный текст и справка. */
@Composable
internal fun ClueCard(kicker: String, text: String, meta: String, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.secondary
    Surface(modifier.fillMaxWidth(), shape = leaf(20), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row {
            Box(Modifier.width(4.dp).heightIn(min = 70.dp).background(accent))
            Column(Modifier.padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(kicker.uppercase(), color = MaterialTheme.colorScheme.secondaryText, style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 1.2.sp), fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 23.sp), color = MaterialTheme.colorScheme.onSurface)
                Text(meta, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Крупная кнопка «лист» для результатов игр: главная — заливкой, второстепенная — контуром. */
@Composable
internal fun GameButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, accent: Boolean = false) {
    val fill = when { accent -> MaterialTheme.colorScheme.secondary; primary -> MaterialTheme.colorScheme.primary; else -> Color.Transparent }
    val content = when { accent -> MaterialTheme.colorScheme.onSecondary; primary -> MaterialTheme.colorScheme.onPrimary; else -> MaterialTheme.colorScheme.onBackground }
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(56.dp), shape = leaf(22),
        color = fill.copy(alpha = if (enabled) 1f else 0.4f), contentColor = content,
        border = if (primary || accent) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start)
            Icon(painterResource(R.drawable.arrow_right), null, Modifier.size(20.dp))
        }
    }
}

/** Ссылка-текст под кнопками. */
@Composable
internal fun GameLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(text, modifier.clickable(role = Role.Button, onClick = onClick).heightIn(min = 44.dp).padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
}

/** Заголовок экрана игры: крупно, с точкой акцентом, как на остальных экранах. */
@Composable
internal fun GameTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 34.sp, letterSpacing = (-1.4).sp, fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onBackground)
        Text(".", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.secondaryText)
    }
}

internal val ScreenPadding = Spacing.screen
internal fun Modifier.gap(top: Int) = this.padding(top = top.dp)
internal val HalfStep = Spacing.step / 2
