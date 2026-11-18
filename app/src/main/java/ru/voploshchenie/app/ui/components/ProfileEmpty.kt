package ru.voploshchenie.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.voploshchenie.app.R
import ru.voploshchenie.app.ui.theme.secondaryText

/** Иллюстрация пустого профиля: «карточка профиля» с аватаром, полосами сфер и штампом-галочкой. Рисуется в сетке 320×188, как на вебе. */
@Composable
fun ProfileEmptyArt(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier.fillMaxWidth().aspectRatio(320f / 188f)) {
        val k = size.width / 320f
        fun p(v: Float) = v * k
        // Сетка точек справа сверху, затухающая к низу.
        for (i in 0 until 15) for (j in 0 until 12) {
            val fade = 1f - (i / 15f) * .55f - (j / 12f) * .75f
            if (fade > .05f) drawCircle(scheme.primary.copy(alpha = (fade * .55f).coerceIn(0f, 1f)), p(1.6f), Offset(p(170f + 2f + i * 10f), p(2f + j * 10f)))
        }
        // Подложка и карточка с «листом» в правом верхнем углу.
        drawRect(scheme.primaryContainer, Offset(p(40f), p(46f)), Size(p(244f), p(132f)))
        val card = Path().apply {
            moveTo(p(18f), p(22f)); lineTo(p(244f), p(22f)); arcTo(androidx.compose.ui.geometry.Rect(p(220f), p(22f), p(268f), p(70f)), -90f, 90f, false)
            lineTo(p(268f), p(154f)); lineTo(p(18f), p(154f)); close()
        }
        drawPath(card, scheme.surface)
        drawPath(card, scheme.onBackground, style = Stroke(p(1.5f)))
        // Аватар.
        drawCircle(scheme.primaryContainer, p(26f), Offset(p(68f), p(76f)))
        drawCircle(scheme.primary, p(10f), Offset(p(68f), p(68f)), style = Stroke(p(3f)))
        drawArc(scheme.primary, 180f, 180f, false, Offset(p(50f), p(82f)), Size(p(36f), p(32f)), style = Stroke(p(3f), cap = StrokeCap.Round))
        // Имя и подпись.
        drawRect(scheme.onBackground, Offset(p(112f), p(52f)), Size(p(92f), p(8f)))
        drawRect(scheme.outlineVariant, Offset(p(112f), p(68f)), Size(p(62f), p(6f)))
        // Полосы сфер.
        drawRect(scheme.secondary, Offset(p(40f), p(116f)), Size(p(188f), p(9f)))
        drawRect(scheme.primary, Offset(p(40f), p(132f)), Size(p(132f), p(9f)))
        drawRect(scheme.outlineVariant, Offset(p(40f), p(148f)), Size(p(84f), p(9f)))
        // Штамп с галочкой.
        rotate(-8f, Offset(p(229f), p(137f))) {
            drawRect(scheme.secondary, Offset(p(206f), p(118f)), Size(p(46f), p(38f)), style = Stroke(p(2.5f)))
            val tick = Path().apply { moveTo(p(216f), p(138f)); lineTo(p(224f), p(146f)); lineTo(p(238f), p(130f)) }
            drawPath(tick, scheme.secondary, style = Stroke(p(3.5f)))
        }
    }
}

/** Что появится в профиле после теста: три пункта с номерами и тонкими линиями, как на вебе. */
@Composable
fun ProfilePreview(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val items = listOf(
        R.string.profile_preview_1_title to R.string.profile_preview_1_text,
        R.string.profile_preview_2_title to R.string.profile_preview_2_text,
        R.string.profile_preview_3_title to R.string.profile_preview_3_text,
    )
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.onBackground))
        items.forEachIndexed { i, (title, text) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
                Text("%02d".format(i + 1), Modifier.width(48.dp), color = scheme.primary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
        }
    }
}
