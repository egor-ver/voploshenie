package ru.voploshchenie.app.ui.components

import ru.voploshchenie.app.ui.theme.ComponentSpacing
import ru.voploshchenie.app.ui.theme.dateStatus
import ru.voploshchenie.app.ui.theme.brand
import ru.voploshchenie.app.ui.theme.kicker
import ru.voploshchenie.app.ui.theme.secondaryText
import ru.voploshchenie.app.R
import ru.voploshchenie.app.ui.theme.Spacing
import ru.voploshchenie.app.ui.theme.CardRadius
import ru.voploshchenie.app.ui.theme.CatalogSpacing
import ru.voploshchenie.app.ui.theme.QuizSpacing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    // Как на вебе: сплошная кнопка со скруглённым правым верхним углом, без смещённого контура.
    // Скругление рисуется фоном, а не обрезкой формы: так область нажатия остаётся прямоугольной.
    val fill = MaterialTheme.colorScheme.secondary.copy(alpha = if (enabled) 1f else DisabledAlpha)
    Surface(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(ComponentSpacing.primaryHeight).drawBehind {
            val r = 24.dp.toPx()
            drawPath(androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f); lineTo(size.width - r, 0f)
                arcTo(androidx.compose.ui.geometry.Rect(size.width - 2 * r, 0f, size.width, 2 * r), -90f, 90f, false)
                lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }, fill)
        },
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSecondary,
    ) {
        Row(Modifier.padding(horizontal = Spacing.screen), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Icon(painterResource(R.drawable.arrow_right), null, Modifier.size(QuizSpacing.arrowSize))
        }
    }
}

/** Второстепенное действие рядом с фирменной кнопкой: та же высота, без заливки. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(QuizSpacing.touch),
        shape = RoundedCornerShape(CardRadius),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(QuizSpacing.stroke, MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) { Text(text, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
fun BrandHeader(modifier: Modifier = Modifier) {
    Row(modifier.height(QuizSpacing.logoHeight), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.logo_mark), null, Modifier.size(QuizSpacing.logoHeight), tint = Color.Unspecified)
        Spacer(Modifier.width(Spacing.step / 2))
        Text(stringResource(R.string.brand_name), style = MaterialTheme.typography.brand, color = MaterialTheme.colorScheme.onBackground)
    }
}

private const val DisabledAlpha = 0.38f

@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondaryText) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.kicker,
    )
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.Checkbox, onClick = onClick),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        border = if (selected) null else BorderStroke(QuizSpacing.stroke, MaterialTheme.colorScheme.outline),
    ) {
        Box(Modifier.padding(horizontal = Spacing.step * 2), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** «Сохранить» с сердцем: в карточке — кнопка с рамкой, в списке ([compact]) — лёгкое текстовое действие. */
@Composable
fun SaveButton(saved: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = if (compact) modifier.heightIn(min = QuizSpacing.touch)
            else modifier.width(ComponentSpacing.saveWidth).height(ComponentSpacing.saveHeight),
        shape = RoundedCornerShape(CardRadius),
        color = if (compact) Color.Transparent else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        border = if (compact) null else BorderStroke(QuizSpacing.stroke, MaterialTheme.colorScheme.outline),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(if (saved) R.drawable.heart_filled else R.drawable.heart), contentDescription = null, modifier = Modifier.size(ComponentSpacing.iconSize))
            Spacer(Modifier.width(ComponentSpacing.iconGap))
            Text(stringResource(if (saved) R.string.saved_status else R.string.save), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun OpportunityCard(
    kicker: String,
    title: String,
    meta: String,
    day: String,
    month: String,
    modifier: Modifier = Modifier,
    status: (@Composable () -> Unit)? = null,
    accent: Color? = null,
) {
    val dateWidth = if (day.isEmpty()) CatalogSpacing.undatedWidth else CatalogSpacing.dateWidth
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val background = MaterialTheme.colorScheme.background
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(CardRadius), color = MaterialTheme.colorScheme.surface) {
        Row(modifier = Modifier.heightIn(min = CatalogSpacing.cardHeight).drawWithContent {
            drawContent()
            val seam = size.width - dateWidth.toPx()
            drawLine(dividerColor, Offset(seam, 0f), Offset(seam, size.height), QuizSpacing.stroke.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(ComponentSpacing.dividerDash.toPx(), ComponentSpacing.dividerDash.toPx())))
            drawCircle(background, Spacing.step.toPx(), Offset(seam, 0f))
            drawCircle(background, Spacing.step.toPx(), Offset(seam, size.height))
            // Полоса цвета типа слева: стажировка, олимпиада, курс и событие различаются с первого взгляда.
            accent?.let { drawRect(it, Offset.Zero, androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
        }, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(Spacing.step * 2), verticalArrangement = Arrangement.spacedBy(Spacing.step)) {
                Kicker(kicker, color = accent ?: MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.screen))
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                status?.invoke()
            }
            Column(Modifier.width(dateWidth).padding(Spacing.step), horizontalAlignment = Alignment.CenterHorizontally) {
                if (day.isNotEmpty()) Text(day, Modifier.width(IntrinsicSize.Max), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.displayLarge)
                Text(month, Modifier.width(IntrinsicSize.Max), color = if (day.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = if (day.isEmpty()) MaterialTheme.typography.dateStatus else MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}
