package ru.voploshchenie.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Карточка «чертёж»: белая плоскость с тонкой рамкой, клетчатым фоном и скруглением в правом верхнем углу. */
@Composable
fun PrintCard(modifier: Modifier = Modifier, radius: Dp = 28.dp, content: @Composable () -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = radius, bottomEnd = 0.dp, bottomStart = 0.dp),
        border = BorderStroke(1.dp, line)) {
        Box(Modifier.drawBehind {
            val step = 24.dp.toPx()
            // Сетка проступает справа сверху и затухает к левому нижнему углу, как на вебе.
            val fade = Brush.radialGradient(listOf(line.copy(alpha = .9f), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 1.1f)
            var x = 0f
            while (x < size.width) { drawLine(fade, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(fade, Offset(0f, y), Offset(size.width, y), 1f); y += step }
        }) { content() }
    }
}

/** Листок календаря 7×2 с одной закрашенной клеткой: картинка пустого экрана «Дедлайны». */
@Composable
fun CalendarSheetArt(modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.primary.copy(alpha = .35f)
    Column(modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(topEnd = 14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { col ->
                    val on = row == 1 && col == 0
                    Box(Modifier.size(14.dp).background(if (on) MaterialTheme.colorScheme.secondary else Color.Transparent)
                        .then(if (on) Modifier else Modifier.drawBehind {
                            drawRect(outline, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                        }))
                }
            }
        }
    }
}
