package ru.voploshchenie.app.ui.opportunities

import ru.voploshchenie.app.ui.theme.secondaryText
import ru.voploshchenie.app.ui.components.formatCatalogDate
import ru.voploshchenie.app.R
import ru.voploshchenie.app.ui.components.BrandHeader
import ru.voploshchenie.app.domain.model.CatalogDate
import ru.voploshchenie.app.domain.model.CatalogItem
import ru.voploshchenie.app.ui.components.SaveButton
import ru.voploshchenie.app.ui.components.PrimaryButton
import ru.voploshchenie.app.ui.result.sphereResource
import ru.voploshchenie.app.ui.theme.Spacing
import android.content.Intent
import android.content.ActivityNotFoundException
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri

@Composable
internal fun OpportunityDetailRoute(onBack: () -> Unit, onSimilar: () -> Unit,
    viewModel: OpportunityDetailViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()) {
    LaunchedEffect(viewModel) { viewModel.retry() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var openingFailed by remember { mutableStateOf(false) }
    if (openingFailed) {
        CatalogError { openingFailed = false }
        return
    }
    OpportunityDetailScreen(state, onBack, onSimilar, viewModel::toggleSaved, viewModel::retry, onOpen = { url ->
        try {
            try {
                CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
            } catch (_: ActivityNotFoundException) {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            }
        } catch (_: Exception) {
            openingFailed = true
        }
    })
}

@Composable
internal fun OpportunityDetailScreen(state: OpportunityDetailUiState, onBack: () -> Unit, onSimilar: () -> Unit,
    onSave: () -> Unit = {}, onRetry: () -> Unit = {}, onOpen: (String) -> Unit = {}) {
    when (state) {
        OpportunityDetailUiState.Loading -> return
        OpportunityDetailUiState.Error -> { CatalogError(onRetry); return }
        is OpportunityDetailUiState.Content -> Unit
    }
    val resources = androidx.compose.ui.platform.LocalResources.current
    val current = state.item
    val saved = state.saved
    if (state.closed) {
        ClosedOpportunityContent(current, saved, onSimilar, onSave)
        return
    }
    Column(Modifier.fillMaxSize().testTag("opportunity_detail").verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
        Spacer(Modifier.height(Spacing.step * 2))
        BrandHeader()
        Spacer(Modifier.height(Spacing.screen))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(Spacing.none)) { Text(stringResource(R.string.back_to_label, stringResource(R.string.tab_opportunities))) }
            SaveButton(saved, onClick = onSave)
        }
        Spacer(Modifier.height(Spacing.step))
        Text(stringResource(R.string.catalog_kicker, stringResource(typeLabel(current.type)), current.spheres.joinToString(" · ") { resources.getString(sphereResource(it)) }).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondaryText)
        Text(current.title, style = MaterialTheme.typography.displayLarge)
        current.organizer?.let {
            Spacer(Modifier.height(Spacing.step))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.step * 2))
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.step)) {
                Text(listOf(formatLabel(current.format), current.audience_text).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.titleMedium)
                current.dates.forEach { date -> Text(stringResource(dateLabel(date.kind), humanDate(date)), style = MaterialTheme.typography.bodyLarge) }
            }
        }
        Spacer(Modifier.height(Spacing.screen))
        Text(stringResource(R.string.catalog_about), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.step))
        Text(current.direction, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.matchPercent?.let {
            Spacer(Modifier.height(Spacing.step))
            Text(stringResource(R.string.catalog_match, it), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(Spacing.screen * 2))
        PrimaryButton(stringResource(if (current.url_kind == "program") R.string.catalog_detail_apply else R.string.catalog_open_organizer), { onOpen(current.url) })
        Spacer(Modifier.height(Spacing.step * 2))
    }
}

@Composable
private fun ClosedOpportunityContent(item: CatalogItem, saved: Boolean, onSimilar: () -> Unit, onSave: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
        Spacer(Modifier.height(Spacing.screen))
        BrandHeader()
        Spacer(Modifier.height(Spacing.screen * 3 + Spacing.step))
        CatalogGrid(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(Spacing.screen + Spacing.step))
        Text(stringResource(R.string.catalog_closed), style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(Spacing.screen + Spacing.step))
        Text(stringResource(R.string.catalog_closed_hint, item.date_text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.catalog_similar), onSimilar)
        Spacer(Modifier.height(Spacing.step * 2))
        SaveButton(saved, onSave, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(Spacing.step * 2))
    }
}

private fun dateLabel(kind: String) = when (kind) { "apply_deadline" -> R.string.catalog_date_apply; "start" -> R.string.catalog_date_start; "end" -> R.string.catalog_date_end; else -> R.string.catalog_unknown_date }

internal fun humanDate(date: CatalogDate): String = formatCatalogDate(date)
