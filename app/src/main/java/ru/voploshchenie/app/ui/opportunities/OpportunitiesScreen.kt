package ru.voploshchenie.app.ui.opportunities

import ru.voploshchenie.app.ui.theme.brand
import ru.voploshchenie.app.R
import ru.voploshchenie.app.ui.components.BrandHeader
import ru.voploshchenie.app.domain.model.CatalogItem
import ru.voploshchenie.app.ui.components.Chip
import ru.voploshchenie.app.ui.components.SaveButton
import ru.voploshchenie.app.ui.components.OpportunityCard
import ru.voploshchenie.app.ui.components.PrimaryButton
import ru.voploshchenie.app.ui.result.sphereResource
import ru.voploshchenie.app.ui.theme.Spacing
import ru.voploshchenie.app.ui.theme.CardRadius
import ru.voploshchenie.app.ui.theme.CatalogSpacing
import ru.voploshchenie.app.ui.theme.QuizSpacing
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.Role
import ru.voploshchenie.app.domain.usecase.datePeriod
import ru.voploshchenie.app.domain.usecase.DateGroup
import ru.voploshchenie.app.domain.usecase.groupByDate
import ru.voploshchenie.app.ui.theme.deadline
import ru.voploshchenie.app.ui.theme.success
import ru.voploshchenie.app.ui.theme.secondaryText

internal val catalogTypes = listOf(null, "olympiad", "internship", "course", "event")
private val typeLabels = listOf(R.string.catalog_all, R.string.catalog_olympiads, R.string.catalog_internships, R.string.catalog_courses, R.string.catalog_events)
internal fun typeLabel(type: String) = when (type) {
    "olympiad" -> R.string.catalog_olympiad
    "internship" -> R.string.catalog_internship
    "course" -> R.string.catalog_course
    else -> R.string.catalog_event
}

@Composable
internal fun OpportunitiesRoute(onStartTest: () -> Unit, onItem: (String) -> Unit, onBack: () -> Unit = {},
    viewModel: OpportunitiesViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()) {
    LaunchedEffect(viewModel) { viewModel.retry() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    OpportunitiesScreen(state, onStartTest, onItem, onBack, viewModel::retry, viewModel::search,
        viewModel::filter, viewModel::reset, viewModel::showSort, viewModel::hideSort, viewModel::sort, viewModel::toggleSaved, viewModel::chooseSort)
}

@Composable
internal fun OpportunitiesScreen(state: OpportunitiesUiState, onStartTest: () -> Unit = {}, onItem: (String) -> Unit = {},
    onBack: () -> Unit = {}, onRetry: () -> Unit = {}, onQuery: (String) -> Unit = {}, onType: (Int) -> Unit = {},
    onReset: () -> Unit = {}, onShowSort: () -> Unit = {}, onHideSort: () -> Unit = {},
    onSort: (Boolean) -> Unit = {}, onSave: (String) -> Unit = {}, onSortChoice: (Boolean) -> Unit = {}) {
    when (state) {
        OpportunitiesUiState.Error -> CatalogError(onRetry)
        OpportunitiesUiState.Loading -> Column(Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
            Spacer(Modifier.height(Spacing.screen))
            BrandHeader()
            Spacer(Modifier.height(Spacing.screen))
            Text(stringResource(R.string.tab_opportunities), style = MaterialTheme.typography.displayLarge)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        is OpportunitiesUiState.Content -> if (state.similarTo != null) SimilarOpportunities(state.items, onItem, onBack)
            else OpportunitiesContent(state, onStartTest, onItem, onQuery, onType, onReset, onShowSort, onHideSort, onSort, onSave, onSortChoice)
    }
}


@Composable
internal fun OpportunitiesContent(state: OpportunitiesUiState.Content,
    onStartTest: () -> Unit = {}, onItem: (String) -> Unit = {}, onQuery: (String) -> Unit = {}, onType: (Int) -> Unit = {},
    onReset: () -> Unit = {}, onShowSort: () -> Unit = {}, onHideSort: () -> Unit = {},
    onSort: (Boolean) -> Unit = {}, onSave: (String) -> Unit = {}, onSortChoice: (Boolean) -> Unit = {}) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val query = state.query
    val type = state.type
    val interests = state.interests
    val result = state.result
    val sorted = state.items
    val recommendations = state.recommendations
    val recommendationsOnly = state.recommendationsOnly
    BackHandler(state.sorting, onHideSort)
    if (state.sorting) {
        CatalogSort(state.sortChoice, result != null, onApply = onSort, onSelect = onSortChoice)
        return
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("catalog_list"),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.screen, bottom = Spacing.screen * 2),
        verticalArrangement = Arrangement.spacedBy(Spacing.screen)) {
        item {
            CatalogHeader(query, type, interests, sorted.size, recommendationsOnly && result != null,
                onQuery, onType, onShowSort)
        }
        if (sorted.isEmpty()) item {
            Column {
                Text(stringResource(R.string.catalog_empty), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onReset) { Text(stringResource(R.string.catalog_reset)) }
            }
        }
        val today = java.time.LocalDate.now()
        // В режиме «по дедлайну» список разбит на группы с заголовками; «по интересам» остаётся плоским.
        val rows: List<Any> = if (interests) sorted else groupByDate(sorted, today).flatMap { (group, list) -> listOf<Any>(group) + list }
        items(rows, key = { if (it is CatalogItem) it.id else "group_$it" }) { row ->
            if (row is DateGroup) { GroupHeader(row); return@items }
            val item = row as CatalogItem
            val date = catalogCardDate(item, today)
            val monthName = date.month?.let { stringArrayResource(R.array.catalog_months)[it - 1] }
            val month = monthName?.let { if (date.until) stringResource(R.string.catalog_until, it) else it }
                ?: date.year
                ?: stringResource(if (item.is_rolling) R.string.catalog_rolling else R.string.catalog_unknown_date)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.step)) {
                Surface(onClick = { onItem(item.id) }, color = Color.Transparent, modifier = Modifier.testTag("opportunity_${item.id}")) {
                    OpportunityCard(stringResource(R.string.catalog_kicker, stringResource(typeLabel(item.type)),
                        item.spheres.joinToString(" · ") { resources.getString(sphereResource(it)) }), item.title,
                        listOf(formatLabel(item.format), item.audience_text,
                            locationNote(item, state.profileCity, state.locationPreference).takeIf { recommendationsOnly }.orEmpty())
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        date.day.orEmpty(), month, accent = typeAccent(item.type))
                }
                if (recommendationsOnly) recommendations.firstOrNull { it.item.id == item.id }?.let { recommendation ->
                    val label = recommendation.fromSphere?.let { sphere ->
                        stringResource(R.string.catalog_also_from_sphere, resources.getString(sphereResource(sphere)))
                    } ?: if (recommendation.isOther) stringResource(R.string.catalog_other_opportunities) else null
                    if (label != null) Text(label, Modifier.testTag("recommendation_from_${item.id}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SaveButton(item.id in state.savedIds, onClick = { onSave(item.id) }, compact = true)
            }
        }
        if (result == null) item {
            TextButton(onClick = onStartTest, contentPadding = PaddingValues(Spacing.none)) { Text(stringResource(R.string.take_test)) }
        }
    }
}

@Composable
internal fun typeAccent(type: String): Color = when (type) {
    "olympiad" -> MaterialTheme.colorScheme.deadline
    "course" -> MaterialTheme.colorScheme.secondaryText
    "event" -> MaterialTheme.colorScheme.success
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun GroupHeader(group: DateGroup) {
    val months = stringArrayResource(R.array.catalog_months_full)
    val label = when (group) {
        DateGroup.Ongoing -> stringResource(R.string.catalog_group_ongoing)
        DateGroup.NextMonth -> stringResource(R.string.catalog_group_next_month)
        is DateGroup.Month -> stringResource(R.string.catalog_group_month, months[group.month.monthValue - 1], group.month.year)
        is DateGroup.Year -> stringResource(R.string.catalog_group_year, group.year)
        DateGroup.Rolling -> stringResource(R.string.catalog_group_rolling)
        DateGroup.Unknown -> stringResource(R.string.catalog_group_unknown)
    }
    Column(Modifier.fillMaxWidth().padding(top = Spacing.step).testTag("catalog_group")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.secondary))
            Spacer(Modifier.width(Spacing.step))
            Text(label.uppercase(), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.4.sp), fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(Spacing.step))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onBackground))
    }
}

@Composable
private fun CatalogHeader(query: String, type: Int, interests: Boolean, found: Int, matched: Boolean,
    onQuery: (String) -> Unit, onType: (Int) -> Unit, onShowSort: () -> Unit) {
    Column {
        BrandHeader()
        Spacer(Modifier.height(Spacing.screen))
        Text(stringResource(R.string.tab_opportunities), style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(Spacing.screen))
        CatalogSearch(query, onQuery)
        CatalogTypeFilters(type, onType)
        CatalogSummary(found, interests, onShowSort)
        if (matched) Text(stringResource(R.string.catalog_matched),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CatalogSearch(query: String, onQuery: (String) -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().height(QuizSpacing.touch)) {
        Row(Modifier.padding(horizontal = Spacing.step * 2), verticalAlignment = Alignment.CenterVertically) {
            val color = MaterialTheme.colorScheme.onSurface
            Canvas(Modifier.size(QuizSpacing.arrowSize)) {
                drawCircle(color, radius = size.width * .34f, center = Offset(size.width * .4f, size.height * .4f), style = Stroke(CatalogSpacing.searchStroke.toPx()))
                drawLine(color, Offset(size.width * .66f, size.height * .66f), Offset(size.width, size.height), CatalogSpacing.searchStroke.toPx())
            }
            Spacer(Modifier.width(Spacing.step))
            BasicTextField(query, onQuery, Modifier.weight(1f).testTag("catalog_search"), singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = color), decorationBox = { field ->
                    if (query.isEmpty()) Text(stringResource(R.string.catalog_search), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    field()
                })
        }
    }
}

@Composable
private fun CatalogTypeFilters(type: Int, onType: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = Spacing.step), horizontalArrangement = Arrangement.spacedBy(Spacing.step)) {
        typeLabels.forEachIndexed { index, label ->
            Box(Modifier.heightIn(min = QuizSpacing.touch), contentAlignment = Alignment.Center) {
                Chip(stringResource(label), index == type, { onType(index) }, Modifier.heightIn(min = QuizSpacing.touch).testTag("catalog_type_$index"))
            }
        }
    }
    if (type != 0) {
        Box(Modifier.heightIn(min = QuizSpacing.touch), contentAlignment = Alignment.CenterStart) {
            Surface(
                modifier = Modifier.width(140.dp).height(32.dp).clickable(onClick = { onType(0) }).testTag("catalog_remove_type"),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                border = androidx.compose.foundation.BorderStroke(QuizSpacing.stroke, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(start = Spacing.step * 2, end = Spacing.step * 2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(typeLabels[type]), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.catalog_remove_filter), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun CatalogSummary(found: Int, interests: Boolean, onShowSort: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.catalog_found, found), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onShowSort, modifier = Modifier.testTag("catalog_sort"), contentPadding = PaddingValues(Spacing.none)) {
            Text(stringResource(R.string.catalog_sort_action, stringResource(if (interests) R.string.catalog_interest_sort else R.string.catalog_date_sort)), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun formatLabel(format: String?) = when (format) {
    "remote" -> stringResource(R.string.catalog_remote)
    "offline" -> stringResource(R.string.catalog_onsite)
    "hybrid" -> stringResource(R.string.catalog_hybrid)
    else -> format.orEmpty()
}

@Composable
internal fun locationNote(item: CatalogItem, city: String?, preference: String?): String = when {
    preference !in setOf("a", "b") -> ""
    item.format == "remote" -> stringResource(R.string.catalog_online)
    !city.isNullOrBlank() && !item.city.isNullOrBlank() && normalizeCity(city) == normalizeCity(item.city) -> stringResource(R.string.catalog_nearby)
    else -> ""
}

private fun normalizeCity(value: String) = value.trim().replace(Regex("\\s+"), " ").lowercase()

internal data class CardDate(val day: String? = null, val month: Int? = null, val until: Boolean = false, val year: String? = null)
/** Дата на карточке. С [today] берётся ближайшая будущая (срок приёма или старт), а у идущих сейчас — дата окончания («до …»). */
internal fun catalogCardDate(item: CatalogItem, today: java.time.LocalDate? = null): CardDate {
    fun ahead(date: ru.voploshchenie.app.domain.model.CatalogDate) = today == null || date.precision !in setOf("day", "month") || date.datePeriod()?.second?.let { it >= today } ?: true
    val date = item.dates.firstOrNull { it.kind == "apply_deadline" && ahead(it) } ?: item.dates.firstOrNull { it.kind == "start" && ahead(it) }
        ?: (if (today != null) item.dates.firstOrNull { it.kind == "end" && ahead(it) } else null)
        ?: item.dates.firstOrNull { it.kind == "apply_deadline" } ?: item.dates.firstOrNull { it.kind == "start" } ?: return CardDate()
    // Известен только год: на штампе показываем его («2026»), как на вебе, пока позиция не «круглогодичная».
    if (date.precision == "year" && !item.is_rolling) return CardDate(year = date.date.take(4))
    if (date.precision !in setOf("day", "month")) return CardDate()
    val start = date.datePeriod()?.first ?: return CardDate()
    return CardDate(day = start.dayOfMonth.toString().takeIf { date.precision == "day" }, month = start.monthValue, until = date.kind == "end")
}

@Composable
private fun SimilarOpportunities(items: List<CatalogItem>, onItem: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
        Spacer(Modifier.height(Spacing.screen)); BrandHeader()
        TextButton(onClick = onBack) { Text(stringResource(R.string.back_to_label, stringResource(R.string.opportunity_title))) }
        Text(stringResource(R.string.catalog_similar), style = MaterialTheme.typography.headlineMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.step), contentPadding = PaddingValues(vertical = Spacing.screen)) {
            items(items, key = { it.id }) { candidate ->
                Surface(onClick = { onItem(candidate.id) }, color = Color.Transparent) {
                    OpportunityCard(stringResource(R.string.catalog_kicker, stringResource(typeLabel(candidate.type)), candidate.spheres.joinToString(" · ")), candidate.title,
                        candidate.organizer ?: listOf(formatLabel(candidate.format), candidate.audience_text).filter { it.isNotBlank() }.joinToString(" · "),
                        "", "", Modifier)
                }
            }
        }
    }
}

@Composable
internal fun CatalogSort(interests: Boolean, hasResult: Boolean, onSelect: (Boolean) -> Unit = {}, onApply: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = Spacing.screen)) {
        Spacer(Modifier.height(Spacing.screen))
        BrandHeader()
        Spacer(Modifier.height(Spacing.screen))
        Text(stringResource(R.string.catalog_sort), style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(Spacing.step))
        Text(stringResource(R.string.catalog_sort_prompt), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.screen * 2))
        listOf(false, true).forEach { interest ->
            val primary = MaterialTheme.colorScheme.primary
            Surface(shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().drawWithContent {
                    drawContent()
                    if (interests == interest) drawRoundRect(primary,
                        topLeft = Offset(QuizSpacing.selectedOffset.toPx(), QuizSpacing.selectedOffset.toPx()),
                        size = size, cornerRadius = CornerRadius(CardRadius.toPx()), style = Stroke(QuizSpacing.stroke.toPx()))
                }.selectable(interests == interest, enabled = !interest || hasResult, role = Role.RadioButton, onClick = { onSelect(interest) }).testTag("catalog_sort_$interest")) {
                Column(Modifier.padding(Spacing.step * 2)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(if (interest) R.string.catalog_interest_sort else R.string.catalog_date_sort), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        Canvas(Modifier.size(CatalogSpacing.radioSize)) {
                            drawCircle(primary, radius = (size.minDimension - QuizSpacing.stroke.toPx()) / 2, style = Stroke(QuizSpacing.stroke.toPx()))
                            if (interests == interest) drawCircle(primary, CatalogSpacing.radioDot.toPx(), center)
                        }
                    }
                    Text(stringResource(if (interest) R.string.catalog_interest_hint else R.string.catalog_date_hint), style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(Spacing.screen))
        }
        Text(stringResource(R.string.catalog_sort_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!hasResult) Text(stringResource(R.string.take_test), Modifier.padding(top = Spacing.step), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.catalog_apply), { onApply(interests) })
        Spacer(Modifier.height(Spacing.step * 2))
    }
}

@Composable
internal fun CatalogError(onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = Spacing.screen)) {
        Spacer(Modifier.height(Spacing.screen))
        BrandHeader()
        Spacer(Modifier.height(Spacing.screen * 3 + Spacing.step))
        CatalogGrid(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(Spacing.screen + Spacing.step))
        Text(stringResource(R.string.catalog_error), style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(Spacing.screen + Spacing.step))
        Text(stringResource(R.string.catalog_error_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.catalog_retry), onRetry)
        Spacer(Modifier.height(Spacing.step * 2))
    }
}

@Composable
internal fun CatalogGrid(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
    Canvas(modifier.width(CatalogSpacing.gridWidth).height(CatalogSpacing.gridHeight)) {
        val gap = CatalogSpacing.gridGap.toPx()
        var x = 0f
        while (x <= size.width) { drawLine(color, Offset(x, 0f), Offset(x, size.height), QuizSpacing.stroke.toPx()); x += gap }
        var y = 0f
        while (y <= size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), QuizSpacing.stroke.toPx()); y += gap }
    }
}
