package ru.voploshchenie.app.ui.result

import ru.voploshchenie.app.ui.theme.sectionTitle
import ru.voploshchenie.app.R
import ru.voploshchenie.app.ui.components.BrandHeader
import ru.voploshchenie.app.ui.components.PrimaryButton
import ru.voploshchenie.app.domain.model.TestConfig
import ru.voploshchenie.app.domain.scoring.TestAnswers
import ru.voploshchenie.app.domain.scoring.ScoringResult
import ru.voploshchenie.app.domain.scoring.SphereScore
import ru.voploshchenie.app.ui.theme.Spacing
import ru.voploshchenie.app.ui.theme.AppColors
import ru.voploshchenie.app.ui.theme.ResultSpacing
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ResultRoute(onOpportunities: (Boolean) -> Unit, viewModel: ResultViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResultScreen(state, onOpportunities, viewModel::retry)
}

@Composable
internal fun ResultScreen(state: ResultUiState, onOpportunities: (Boolean) -> Unit = {}, onRetry: () -> Unit = {}) {
    when (state) {
        ResultUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
        ResultUiState.Error -> ru.voploshchenie.app.ui.opportunities.CatalogError(onRetry)
        is ResultUiState.Content -> ResultSummary(state.result, state.answers, state.config,
            onOpportunities = onOpportunities, name = state.name, computedAt = state.computedAt)
    }
}

private fun TestConfig.textTable(key: String) = texts.getValue(key).jsonObject.getValue("table").jsonArray.map { it.jsonObject }
private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
private fun TestConfig.paragraphs(key: String) = texts.getValue(key).jsonObject.getValue("paragraphs").jsonArray.map { it.jsonPrimitive.content }

@Composable
internal fun ResultSummary(
    result: ScoringResult?, answers: TestAnswers = TestAnswers(), config: TestConfig,
    listState: LazyListState = rememberLazyListState(), onOpportunities: (Boolean) -> Unit = {}, name: String? = null, computedAt: Long? = null,
) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val changeDirection = answers.profile["PRF06"]?.jsonPrimitive?.content == "хочу сменить направление"
    val internshipRequest = answers.profile["PRF06"]?.jsonPrimitive?.content == "ищу стажировку или практику"
    val specialty = answers.profile["PRF03"]?.jsonPrimitive?.content.orEmpty()
    val satisfaction = answers.profile["PRF05"]?.jsonPrimitive?.content?.toIntOrNull()
    val consistencyLine = specialty.isNotBlank() && satisfaction != null && satisfaction in 1..10 && result?.topSpheres?.isNotEmpty() == true
    val hasConsistency = consistencyLine || result?.consistencyWarning != null
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("result"),
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.step * 2),
        state = listState,
    ) {
        val consistencyBlock: @Composable () -> Unit = {
            ResultBlock(R.string.result_consistency) {
                result?.consistencyWarning?.let { Text(it) }
                if (consistencyLine) {
                    Text(stringResource(R.string.result_specialty_consistency, specialty, requireNotNull(satisfaction),
                        result!!.topSpheres.joinToString(", ") { resources.getString(sphereResource(it)) }))
                }
            }
        }
        item { BrandHeader() }
        item { Text(stringResource(R.string.result_direction), style = MaterialTheme.typography.sectionTitle, color = MaterialTheme.colorScheme.onBackground) }
        item {
            val date = computedAt?.let { SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(it)) }.orEmpty()
            val identity = if (name.isNullOrBlank()) date else stringResource(R.string.result_identity, name.trim(), date)
            Text(identity, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Text(stringResource(R.string.result_disclaimer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (changeDirection && hasConsistency) item { consistencyBlock() }
        if (internshipRequest) item { PrimaryButton(stringResource(R.string.result_open_opportunities), { onOpportunities(true) }, Modifier.testTag("result_open_opportunities")) }
        result?.spheres?.take(3)?.forEachIndexed { index, sphere -> item {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(Spacing.step * 2), verticalArrangement = Arrangement.spacedBy(Spacing.step)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(sphereResource(sphere.id)), Modifier.weight(1f).testTag("result_sphere_${sphere.id}"),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground)
                        if (index == 0) ResultFlower()
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.step)) {
                        SphereDots(sphere.percent)
                        Text(sphere.level, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (result.equivalentPairs.any { sphere.id in it }) Text(stringResource(R.string.result_equivalent), style = MaterialTheme.typography.labelMedium)
                        sphere.flags.forEach { Text(it.text, style = MaterialTheme.typography.bodySmall) }
                        // У профессий процент равен проценту сферы (Р10) — повторять его не нужно.
                        if (sphere.professions.isNotEmpty()) Text(stringResource(R.string.result_professions,
                            sphere.professions.take(3).joinToString(", ") { it.name }), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } }
        result?.let { data -> item {
            ResultBlock(R.string.result_all_spheres) {
                SphereChart(data.spheres)
            }
        } }
        result?.let { data -> item {
            ResultBlock(R.string.result_why) {
                Text(stringResource(R.string.result_why_code, data.riasecCode, data.label))
                val best = data.domains.values.maxOrNull()
                val strong = data.domains.filter { (_, score) -> score > 0 && score == best }.keys
                if (strong.isNotEmpty()) Text(stringResource(R.string.result_why_strengths,
                    strong.map { stringResource(domainName(it)).lowercase() }.joinToString(", ")))
            }
        } }
        if (!internshipRequest) item { PrimaryButton(stringResource(R.string.result_open_opportunities), { onOpportunities(false) }, Modifier.testTag("result_open_opportunities")) }
        if (result != null) {
            item {
                ResultBlock(R.string.result_interests) {
                    Text(result.riasecCode, style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.step)) {
                        result.riasec.forEach { (type, score) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth().height(ResultSpacing.chartHeight), contentAlignment = Alignment.BottomCenter) {
                                    if (score > 0) Box(Modifier.fillMaxWidth().fillMaxHeight(score / 5f).background(MaterialTheme.colorScheme.primary))
                                }
                                Text(type, style = MaterialTheme.typography.labelLarge)
                                Text(score.toString(), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    config.textTable("riasec").forEach { type ->
                        val name = type.text("Тип")
                        val id = name.take(1)
                        Text(stringResource(R.string.result_level, name, result.riasecLevels.getValue(id).lowercase()))
                        if (id in result.riasecCode) Text(type.text("Описание (1–2 предложения)"))
                    }
                }
            }
            item {
                ResultBlock(R.string.result_strengths) {
                    val names = listOf(R.string.result_logic, R.string.result_numbers, R.string.result_text, R.string.result_space, R.string.result_people)
                    listOf("logic", "numbers", "text", "space", "people").forEachIndexed { index, id ->
                        Text(stringResource(R.string.result_level, stringResource(names[index]), result.domainLevels.getValue(id).lowercase()))
                    }
                }
            }
            val energy = answers.experience.filter { it.key in config.questions.getValue("experience").map { q -> q.id } && it.value.energy in 4..5 && it.value.text.isNotBlank() }.toSortedMap()
            if (energy.isNotEmpty()) item {
                ResultBlock(R.string.result_energy) { energy.values.forEach { Text(it.text) } }
            }
            item {
                ResultBlock(R.string.test_values_title) {
                    val values = config.questions.getValue("values").associateBy { it.id }
                    answers.valuesTop3.forEach { Text(values.getValue(it).fields.getValue("Название")) }
                    result.anchor?.let {
                        Text(stringResource(R.string.result_anchor), style = MaterialTheme.typography.titleMedium)
                        Text(it)
                    }
                }
            }
            item {
                ResultBlock(R.string.result_needs) {
                    val needs = config.questions.getValue("needs").associateBy { it.id }
                    answers.needs.top3.forEach { Text(needs.getValue(it).fields.getValue("Условие")) }
                }
            }
            val words = answers.open.filter { it.key in config.questions.getValue("open").map { q -> q.id } + config.questions.getValue("happiness").map { q -> q.id } && it.value.isNotBlank() }.toSortedMap()
            if (words.isNotEmpty()) item {
                ResultBlock(R.string.result_words) { words.forEach { (id, answer) ->
                    Text(config.parts.flatMap { it.questions }.first { it.id == id }.fields.getValue("Текст"), style = MaterialTheme.typography.titleMedium)
                    Text(answer)
                } }
            }
            item {
                ResultBlock(R.string.result_reality) {
                    config.questions.getValue("constraints").forEach { question ->
                        answers.nad[question.id]?.takeIf { it.isNotBlank() }?.let { answer ->
                            Text(question.fields.getValue("Вопрос"), style = MaterialTheme.typography.titleMedium)
                            Text(question.options.firstOrNull { it.id == answer }?.text ?: answer)
                        }
                    }
                    // Общие подсказки по НАДО (ТЗ 8.6) — один раз здесь, а не в каждой сфере.
                    result.hints.filter { it.id !in sphereHints }.forEach { Text(it.text) }
                }
            }
            result.spheres.filter { it.id in result.topSpheres }.forEach { sphere ->
                item {
                    val description = config.textTable("spheres")[config.spheres.indexOfFirst { it.id == sphere.id }]
                    ResultBlock(R.string.result_entry) {
                        Text(description.text("Сфера"), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.result_entry_how), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(description.text("Вход и первые пробы"))
                        Text(stringResource(R.string.result_entry_attention), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(description.text("На что обратить внимание"))
                        sphere.flags.forEach { Text(it.text) }
                        result.hints.filter { sphere.id in sphereHints[it.id].orEmpty() }.forEach { Text(it.text) }
                    }
                }
            }
            if (!changeDirection && hasConsistency) item { consistencyBlock() }
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.step)) {
                    Text(stringResource(R.string.result_experiments), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text(config.paragraphs("experiments").single(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { ResultBlock(R.string.result_limits) {
                Text(config.paragraphs("disclaimer").last().substringAfter("«").substringBeforeLast("»"))
            } }
        }
    }
}

@Composable
private fun ResultBlock(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(Spacing.step * 2), verticalArrangement = Arrangement.spacedBy(Spacing.step)) {
            Text(stringResource(title), Modifier.testTag(if (title == R.string.result_consistency) "result_consistency_heading" else "result_block_$title"), style = MaterialTheme.typography.titleLarge)
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.bodyLarge) { content() }
        }
    }
}

@Composable
private fun ResultFlower() {
    Canvas(Modifier.size(ResultSpacing.diagramSize)) {
        scale(size.width / 72f, size.height / 72f, pivot = Offset.Zero) {
            val petals = listOf(
                AppColors.flowerSecondary to Path().apply {
                    moveTo(37f, 1f)
                    cubicTo(24f, 4f, 22f, 24f, 36f, 39f)
                    cubicTo(52f, 33f, 54f, 16f, 37f, 1f)
                    close()
                },
                AppColors.flowerSecondary to Path().apply {
                    moveTo(2f, 26f)
                    cubicTo(1f, 42f, 16f, 49f, 32f, 44f)
                    cubicTo(30f, 27f, 17f, 20f, 2f, 26f)
                    close()
                },
                AppColors.flowerPrimary to Path().apply {
                    moveTo(68f, 26f)
                    cubicTo(53f, 19f, 42f, 29f, 41f, 43f)
                    cubicTo(55f, 51f, 65f, 44f, 68f, 26f)
                    close()
                },
                AppColors.flowerPrimary to Path().apply {
                    moveTo(28f, 46f)
                    cubicTo(17f, 42f, 10f, 50f, 10f, 62f)
                    cubicTo(22f, 69f, 34f, 58f, 28f, 46f)
                    close()
                },
                AppColors.flowerPrimary to Path().apply {
                    moveTo(44f, 47f)
                    cubicTo(39f, 57f, 48f, 63f, 61f, 60f)
                    cubicTo(62f, 48f, 53f, 45f, 44f, 47f)
                    close()
                },
                AppColors.flowerPrimary to Path().apply {
                    moveTo(36f, 54f)
                    cubicTo(27f, 55f, 28f, 64f, 37f, 68f)
                    cubicTo(45f, 64f, 43f, 56f, 36f, 54f)
                    close()
                },
            )
            petals.forEach { (color, path) -> drawPath(path, color) }
        }
    }
}

@Composable
internal fun SphereDots(percent: Int, expanded: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val percentSize = MaterialTheme.typography.bodyLarge.fontSize
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.step)) {
        Canvas((if (expanded) Modifier.weight(1f) else Modifier.width(ResultSpacing.dotsWidth)).height(ResultSpacing.dotsHeight)
            .progressSemantics(percent.coerceIn(0, 100) / 100f)) {
            val value = percent.coerceIn(0, 100)
            repeat(20) { column ->
                val active = column * 100 < value * 20
                val fraction = column / 19f
                val color = if (expanded) lerp(colors.primary, colors.secondary, fraction)
                    else lerp(AppColors.resultGradientStart, AppColors.resultGradientEnd, fraction)
                repeat(3) { row ->
                    drawCircle(color.copy(alpha = if (active) 1f else .18f),
                        radius = if (active) (1f + fraction * 1.5f) * density else density,
                        center = Offset((column + .5f) * size.width / 20, (row + .5f) * ResultSpacing.dotGap.toPx()))
                }
            }
        }
        val label = stringResource(R.string.result_chart_percent, percent)
        Text(buildAnnotatedString {
            append(label)
            addStyle(SpanStyle(fontSize = percentSize, color = colors.onSurface),
                label.lastIndex, label.length)
        }, style = MaterialTheme.typography.displayLarge, color = colors.primary)
    }
}

/** Подсказки НАДО, привязанные к сферам (ТЗ 8.6); остальные показываются один раз в блоке «Реальность». */
private val sphereHints = mapOf("H01" to setOf("biology"), "H03" to setOf("business"), "H05" to setOf("public_service", "biology"))

@Composable
private fun SphereChart(spheres: List<SphereScore>) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.step * 2)) {
        spheres.forEach { sphere ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.step / 2)) {
                Row {
                    Text(stringResource(sphereResource(sphere.id)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.result_chart_percent, sphere.percent), style = MaterialTheme.typography.labelLarge)
                }
                Canvas(Modifier.fillMaxWidth().height(ResultSpacing.barHeight)) {
                    drawRoundRect(colors.surfaceVariant, cornerRadius = CornerRadius(size.height / 2))
                    drawRoundRect(colors.secondary, size = size.copy(width = size.width * sphere.percent.coerceIn(0, 100) / 100f), cornerRadius = CornerRadius(size.height / 2))
                }
            }
        }
    }
}

internal fun sphereResource(id: String) = when (id) {
    "business" -> R.string.sphere_business
    "finance" -> R.string.sphere_finance
    "it" -> R.string.sphere_it
    "engineering" -> R.string.sphere_engineering
    "biology" -> R.string.sphere_biology
    "telecom" -> R.string.sphere_telecom
    "public_service" -> R.string.sphere_public_service
    else -> R.string.sphere_unknown
}

private fun domainName(domain: String) = when (domain) {
    "logic" -> R.string.domain_logic
    "numbers" -> R.string.domain_numbers
    "text" -> R.string.domain_text
    "space" -> R.string.domain_space
    else -> R.string.domain_people
}
