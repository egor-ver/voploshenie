package ru.voploshchenie.app.ui.saved

import ru.voploshchenie.app.ui.theme.ComponentSpacing
import ru.voploshchenie.app.ui.components.formatCatalogDate
import ru.voploshchenie.app.R
import ru.voploshchenie.app.ui.components.BrandHeader
import ru.voploshchenie.app.domain.model.SavedItem
import ru.voploshchenie.app.domain.model.CatalogItem
import ru.voploshchenie.app.ui.components.OpportunityCard
import ru.voploshchenie.app.ui.opportunities.typeLabel
import ru.voploshchenie.app.ui.opportunities.catalogCardDate
import ru.voploshchenie.app.ui.opportunities.formatLabel
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import ru.voploshchenie.app.ui.components.PrimaryButton
import androidx.compose.ui.text.font.FontWeight
import ru.voploshchenie.app.ui.result.sphereResource
import ru.voploshchenie.app.ui.theme.Spacing
import ru.voploshchenie.app.ui.theme.CardRadius
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.platform.testTag
import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun SavedRoute(onItem: (String) -> Unit, onOpportunities: () -> Unit,
    viewModel: SavedViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SavedScreen(state, onItem, onOpportunities, viewModel::retry)
}

@Composable
internal fun SavedScreen(state: SavedUiState, onItem: (String) -> Unit, onOpportunities: () -> Unit, onRetry: () -> Unit = {}) {
    when (val current = state) {
        SavedUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        SavedUiState.Error -> ru.voploshchenie.app.ui.opportunities.CatalogError(onRetry)
        is SavedUiState.Content -> SavedContent(state.saved, onItem, onOpportunities, state.items)
    }
}

@Composable
internal fun SavedContent(saved: List<SavedItem>, onItem: (String) -> Unit, onOpportunities: () -> Unit, catalogItems: List<CatalogItem>) {
    if (saved.isEmpty()) {
        SavedEmpty(onOpportunities)
        return
    }
    val byId = remember(catalogItems) { catalogItems.associateBy { it.id } }
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
        Spacer(Modifier.height(Spacing.screen)); BrandHeader(); Spacer(Modifier.height(Spacing.screen))
        Text(stringResource(R.string.tab_saved), style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(Spacing.step))
        Text(pluralStringResource(R.plurals.saved_count, saved.size, saved.size), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.screen), contentPadding = PaddingValues(top = Spacing.screen)) {
            items(saved, key = { it.item_id }) { row ->
                val item = byId[row.item_id]
                if (item == null) Text(stringResource(R.string.saved_unavailable), Modifier.fillMaxWidth().padding(vertical = Spacing.screen), style = MaterialTheme.typography.titleLarge)
                else SavedOpportunity(item, onClick = { onItem(item.id) })
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = Spacing.screen)) {
            PrimaryButton(onClick = onOpportunities,
                text = stringResource(R.string.saved_find_more))
        }
    }
}

@Composable
private fun SavedEmpty(onOpportunities: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth()) { BrandHeader() }
        Spacer(Modifier.height(Spacing.screen))
        SavedIllustration()
        Spacer(Modifier.height(Spacing.screen))
        Text("Здесь будут ваши", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text("возможности", color = colors.primary, style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = Spacing.step).drawBehind {
                val wave = Path().apply {
                    moveTo(0f, size.height)
                    cubicTo(size.width * .25f, size.height - 5.dp.toPx(), size.width * .65f,
                        size.height + 5.dp.toPx(), size.width, size.height - 2.dp.toPx())
                }
                drawPath(wave, colors.secondary, style = Stroke(2.dp.toPx()))
            })
        Spacer(Modifier.height(Spacing.screen))
        Text(buildAnnotatedString {
            append("Нажмите ")
            appendInlineContent("heart", "«Сохранить»")
            append(" на карточке стажировки, курса или олимпиады — и она сохранится здесь.")
        }, inlineContent = mapOf("heart" to InlineTextContent(
            Placeholder(18.sp, 18.sp, PlaceholderVerticalAlign.TextCenter)) {
            Icon(painterResource(R.drawable.heart), null, tint = colors.primary)
        }), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.screen * 2))
        PrimaryButton(stringResource(R.string.saved_find), onClick = onOpportunities)
    }
}

@Composable
private fun SavedIllustration() {
    val colors = MaterialTheme.colorScheme
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var motion by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) motion = ValueAnimator.areAnimatorsEnabled()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val floatOffset = if (motion) {
        val transition = rememberInfiniteTransition(label = "saved float")
        val offset by transition.animateFloat(-3f, 3f,
            infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "cards")
        offset
    } else 0f
    Box(Modifier.widthIn(max = 320.dp).fillMaxWidth().height(250.dp).clearAndSetSemantics { }) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(colors.primaryContainer, 104.dp.toPx(), center)
            drawOval(colors.primary.copy(alpha = .3f), Offset(8.dp.toPx(), 34.dp.toPx()),
                Size(size.width - 16.dp.toPx(), size.height - 68.dp.toPx()),
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))))
            listOf(Offset(.13f, .17f), Offset(.88f, .26f), Offset(.77f, .88f)).forEach { point ->
                val x = size.width * point.x
                val y = size.height * point.y
                val radius = 6.dp.toPx()
                val star = Path().apply {
                    moveTo(x, y - radius); quadraticTo(x, y, x + radius, y)
                    quadraticTo(x, y, x, y + radius); quadraticTo(x, y, x - radius, y)
                    quadraticTo(x, y, x, y - radius); close()
                }
                drawPath(star, colors.secondary)
            }
            drawCircle(colors.primary, 3.dp.toPx(), Offset(size.width * .2f, size.height * .82f))
            drawCircle(colors.secondary, 3.dp.toPx(), Offset(size.width * .73f, size.height * .1f))
        }
        SavedPaper("Курс", Modifier.align(Alignment.CenterStart).offset(12.dp, 25.dp)
            .graphicsLayer { rotationZ = -13f; translationY = floatOffset.dp.toPx() })
        SavedPaper("Олимпиада", Modifier.align(Alignment.CenterEnd).offset((-12).dp, 22.dp)
            .graphicsLayer { rotationZ = 11f; translationY = -floatOffset.dp.toPx() })
        SavedPaper("Стажировка", Modifier.align(Alignment.Center).offset(y = (-42).dp)
            .graphicsLayer { translationY = floatOffset.dp.toPx() }, featured = true)
    }
}

@Composable
private fun SavedPaper(label: String, modifier: Modifier, featured: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.width(if (featured) 140.dp else 112.dp).height(if (featured) 166.dp else 136.dp),
        shape = MaterialTheme.shapes.medium, color = colors.surface, shadowElevation = 3.dp) {
        Box {
            Column(Modifier.fillMaxSize().padding(Spacing.step * 2), verticalArrangement = Arrangement.spacedBy(Spacing.step)) {
                Box(Modifier.size(28.dp).background(colors.primaryContainer, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.heart), null, Modifier.size(ComponentSpacing.savedIconSize), tint = colors.primary)
                }
                repeat(3) { index ->
                    Box(Modifier.fillMaxWidth(if (index == 2) .55f else 1f).height(4.dp)
                        .background(colors.outlineVariant, MaterialTheme.shapes.small))
                }
                Text(label, style = MaterialTheme.typography.labelLarge, color = colors.primary)
            }
            if (featured) Canvas(Modifier.align(Alignment.TopEnd).padding(end = Spacing.step * 2).size(16.dp, 30.dp)) {
                drawPath(Path().apply {
                    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height)
                    lineTo(size.width / 2, size.height * .76f); lineTo(0f, size.height); close()
                }, colors.secondary)
            }
        }
    }
}

@Composable
private fun SavedOpportunity(item: CatalogItem, onClick: () -> Unit) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val date = catalogCardDate(item)
    val months = stringArrayResource(R.array.catalog_months)
    Surface(onClick = onClick, modifier = Modifier.testTag("saved_${item.id}"), color = Color.Transparent) {
        OpportunityCard(stringResource(R.string.catalog_kicker, stringResource(typeLabel(item.type)), item.spheres.joinToString(" · ") { resources.getString(sphereResource(it)) }), item.title,
            listOf(formatLabel(item.format), item.audience_text).filter(String::isNotBlank).joinToString(" · "),
            date.day.orEmpty(), date.month?.let { months[it - 1] } ?: date.year ?: stringResource(if (item.is_rolling) R.string.catalog_rolling else R.string.catalog_unknown_date),
            status = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.step / 2)) {
                    Icon(painterResource(R.drawable.heart_filled), null, Modifier.size(ComponentSpacing.savedIconSize), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.saved_status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }, accent = ru.voploshchenie.app.ui.opportunities.typeAccent(item.type))
    }
}

@Composable
internal fun DeadlinesRoute(onItem: (String) -> Unit, onOpportunities: () -> Unit,
    viewModel: SavedViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        SavedUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        SavedUiState.Error -> ru.voploshchenie.app.ui.opportunities.CatalogError(viewModel::retry)
        is SavedUiState.Content -> DeadlinesScreen(current.deadlines, onItem, onOpportunities)
    }
}

@Composable
internal fun DeadlinesScreen(groups: List<ru.voploshchenie.app.domain.usecase.DeadlineGroup>, onItem: (String) -> Unit, onOpportunities: () -> Unit) {
    val nearestId = groups.firstOrNull()?.entries?.firstOrNull()?.item?.id
    Column(Modifier.fillMaxSize().then(if (groups.isEmpty()) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = Spacing.screen)) {
        Spacer(Modifier.height(Spacing.screen)); BrandHeader(); Spacer(Modifier.height(Spacing.screen))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.deadlines_title), style = MaterialTheme.typography.displayLarge)
            Text(".", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(Spacing.step))
        Text(stringResource(R.string.deadlines_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (groups.isEmpty()) {
            Spacer(Modifier.height(Spacing.screen))
            ru.voploshchenie.app.ui.components.PrintCard(Modifier.testTag("deadlines_empty")) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ru.voploshchenie.app.ui.components.CalendarSheetArt()
                    ru.voploshchenie.app.ui.components.Kicker(stringResource(R.string.deadlines_empty_kicker).uppercase())
                    Text(stringResource(R.string.deadlines_empty), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 23.sp), fontWeight = FontWeight.ExtraBold)
                    PrimaryButton(stringResource(R.string.deadlines_find), onClick = onOpportunities)
                }
            }
        } else LazyColumn(Modifier.weight(1f).testTag("deadlines_list"), contentPadding = PaddingValues(top = Spacing.screen, bottom = Spacing.step), verticalArrangement = Arrangement.spacedBy(Spacing.step)) {
            groups.forEach { group ->
                item(key = "header_${group.month ?: group.year ?: "undated"}") {
                    Text(group.month?.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("ru")))?.replaceFirstChar(Char::uppercase) ?: group.year?.toString() ?: stringResource(R.string.deadlines_no_date), style = MaterialTheme.typography.titleLarge)
                }
                items(group.entries, key = { it.item.id }) { deadline ->
                    DeadlineEntry(deadline, deadline.item.id == nearestId, onItem)
                }
            }
        }
    }
}

@Composable
private fun DeadlineEntry(deadline: ru.voploshchenie.app.domain.usecase.Deadline, highlighted: Boolean, onItem: (String) -> Unit) {
    val modifier = Modifier.fillMaxWidth().testTag("deadline_${deadline.item.id}").clickable { onItem(deadline.item.id) }
    if (highlighted) {
        Surface(modifier, shape = RoundedCornerShape(CardRadius), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(Spacing.screen)) {
                DeadlineText(deadline)
            }
        }
    } else {
        Column(modifier.padding(vertical = Spacing.step)) {
            DeadlineText(deadline)
            HorizontalDivider(Modifier.padding(top = Spacing.screen), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun DeadlineText(deadline: ru.voploshchenie.app.domain.usecase.Deadline) {
    Text(deadline.item.title, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(Spacing.step))
    Text(deadlineLabel(deadline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun deadlineLabel(deadline: ru.voploshchenie.app.domain.usecase.Deadline): String {
    val date = deadline.date ?: return deadline.item.date_text.ifBlank { stringResource(R.string.deadlines_no_date) }
    val kind = when (date.kind) {
        "apply_deadline" -> stringResource(R.string.deadlines_kind_apply)
        "start" -> stringResource(R.string.deadlines_kind_start)
        "end" -> stringResource(R.string.deadlines_kind_end)
        else -> ""
    }
    val formatted = formatCatalogDate(date, deadline.item.date_text)
    return listOf(kind, formatted).filter(String::isNotBlank).joinToString(" · ")
}
