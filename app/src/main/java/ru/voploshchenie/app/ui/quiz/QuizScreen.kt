package ru.voploshchenie.app.ui.quiz

import ru.voploshchenie.app.ui.components.BrandHeader
import ru.voploshchenie.app.ui.components.PrimaryButton
import ru.voploshchenie.app.ui.components.SecondaryButton
import ru.voploshchenie.app.domain.model.ValueSelection
import ru.voploshchenie.app.domain.model.rankingAnswer
import ru.voploshchenie.app.domain.model.selectedIds
import ru.voploshchenie.app.domain.model.experienceAnswer
import ru.voploshchenie.app.domain.model.profileAnswer
import ru.voploshchenie.app.domain.model.textLimit
import ru.voploshchenie.app.domain.model.validQuizAnswer
import ru.voploshchenie.app.R
import ru.voploshchenie.app.domain.model.ConfigOption
import ru.voploshchenie.app.domain.model.TestScreen
import ru.voploshchenie.app.domain.model.TestSessionState
import ru.voploshchenie.app.domain.model.ConfigQuestion
import ru.voploshchenie.app.domain.scoring.TestAnswers
import ru.voploshchenie.app.ui.theme.PrintSpacing
import ru.voploshchenie.app.ui.theme.printNumber
import ru.voploshchenie.app.ui.theme.quizTitle
import ru.voploshchenie.app.ui.theme.printBody
import ru.voploshchenie.app.ui.theme.Spacing
import ru.voploshchenie.app.ui.theme.CardRadius
import ru.voploshchenie.app.ui.theme.QuizSpacing
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.withContext

@Composable
fun SingleChoice(options: List<ConfigOption>, selected: String?, onSelect: (String) -> Unit) {
    val letters = stringResource(R.string.quiz_letters)
    Column(Modifier.selectableGroup()) {
        options.forEachIndexed { index, option ->
            val checked = selected == option.id
            val colors = MaterialTheme.colorScheme
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = QuizSpacing.optionHeight)
                    .selectable(checked, role = Role.RadioButton, onClick = { onSelect(option.id) })
                    .testTag("choice_${option.id}"),
                shape = androidx.compose.ui.graphics.RectangleShape,
                color = if (checked) colors.primaryContainer else colors.background,
                border = if (checked) BorderStroke(QuizSpacing.stroke, colors.primary) else null,
            ) {
                Row(Modifier.padding(horizontal = Spacing.step * 2, vertical = Spacing.step * 2), verticalAlignment = Alignment.CenterVertically) {
                    Text(letters[index].toString(), Modifier.width(PrintSpacing.letterWidth), style = MaterialTheme.typography.titleLarge,
                        color = if (checked) colors.primary else colors.onSurfaceVariant)
                    Text(option.text, Modifier.weight(1f), style = MaterialTheme.typography.printBody, fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal)
                    Spacer(Modifier.width(Spacing.step))
                    Canvas(Modifier.size(QuizSpacing.radioSize)) {
                        drawCircle(if (checked) colors.primary else colors.onSurfaceVariant, style = Stroke(QuizSpacing.stroke.toPx()))
                        if (checked) drawCircle(colors.primary, radius = size.minDimension / 3)
                    }
                }
            }
            if (!checked) HorizontalDivider(color = colors.outlineVariant)
        }
    }
}

@Composable
fun PairChoice(question: ConfigQuestion, selected: String?, values: List<String> = listOf("A", "B"), onSelect: (String) -> Unit) {
    val labels = question.fields.filter { (key, value) ->
        key != "ID" && value != question.id && value != question.type_a && value != question.type_b && value != question.linked
    }.values.toList()
    val options = listOf(
        requireNotNull(question.type_a) to labels[0],
        requireNotNull(question.type_b) to labels[1],
    )
    val shuffledTypes = if (values == listOf("A", "B")) listOf(options[0].first to "A", options[1].first to "B") else values.map { type ->
        requireNotNull(options.firstOrNull { it.first == type }).let { it.first to type }
    }
    SingleChoice(shuffledTypes.map { (type, selection) ->
        ConfigOption(selection, options.first { it.first == type }.second)
    }, selected, onSelect)
}

@Composable
fun Scale(range: IntRange, selected: Int?, minLabel: String, maxLabel: String, tagPrefix: String? = null, onSelect: (Int) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.step)) {
        // Ten points use two rows so every target remains at least 48 dp wide.
        range.toList().chunked(5).forEach { values ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.step)) {
                values.forEach { value ->
                    val checked = selected == value
                    Surface(
                        modifier = Modifier.weight(1f).height(QuizSpacing.scaleHeight)
                            .then(if (tagPrefix == null) Modifier else Modifier.testTag("${tagPrefix}_$value"))
                            .selectable(checked, role = Role.RadioButton, onClick = { onSelect(value) }),
                        shape = RoundedCornerShape(CardRadius),
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        border = if (checked) BorderStroke(QuizSpacing.stroke, MaterialTheme.colorScheme.primary) else null,
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(value.toString(), style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.step * 2)) {
            Text(minLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(maxLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

@Composable
internal fun NeedsRatings(questions: List<ConfigQuestion>, ratings: Map<String, Int>, modifier: Modifier = Modifier, onSelect: (String, Int) -> Unit) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.step * 3)) {
        questions.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.step)) {
                Text(item.fields.entries.first { it.key != "ID" }.value, style = MaterialTheme.typography.titleMedium)
                Scale(1..5, ratings[item.id], stringResource(R.string.test_needs_min), stringResource(R.string.test_needs_max), item.id) { value -> onSelect(item.id, value) }
            }
        }
    }
}

@Composable
internal fun TestQuestionScreen(
    question: ConfigQuestion,
    position: Int,
    total: Int,
    answer: String?,
    onAnswer: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    busy: Boolean = false,
    groupedQuestions: List<ConfigQuestion> = emptyList(),
    pairValues: List<String> = listOf("A", "B"),
    canBack: Boolean = position > 0,
    canAdvance: Boolean? = null,
    isLastScreen: Boolean = position == total - 1,
    part: String? = null,
    overallPercent: Int? = null,
) {
    val instruction = when {
        question.type == "rank_values" -> R.string.test_values_instruction
        question.type == "profile" -> R.string.test_profile_title
        question.type == "pick3" -> R.string.test_pick_instruction
        question.type == "text" || question.type == "text_energy5" -> R.string.test_text_instruction
        question.id.startsWith("INT") -> R.string.test_pairs_instruction
        question.id.startsWith("CHK") -> R.string.test_checks_instruction
        question.id.startsWith("TSK") -> R.string.test_task_instruction
        question.id.startsWith("SPH") -> R.string.test_interest_instruction
        question.id.startsWith("NEED") -> R.string.test_needs_instruction
        else -> R.string.test_choice_instruction
    }
    val title = when {
        question.type == "profile" -> stringResource(R.string.test_profile_title)
        question.type == "rank_values" -> stringResource(R.string.test_values_title)
        question.type == "pick3" -> stringResource(R.string.test_needs_instruction)
        question.id.startsWith("SPH") -> stringResource(R.string.test_interest_title)
        question.id.startsWith("NEED") -> stringResource(R.string.test_needs_title)
        else -> question.prompt ?: listOf("Утверждение", "Вопрос", "Поле", "Условие", "Текст")
            .firstNotNullOfOrNull { question.fields[it] } ?: stringResource(R.string.test_pair_title)
    }
    val scroll = key(question.id) { rememberScrollState() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = Spacing.screen)) {
        QuizHeader(onExit)
        Column(Modifier.weight(1f).verticalScroll(scroll).testTag("question_${question.id}")) {
            QuizProgress(part, position, total, overallPercent)
            Spacer(Modifier.height(Spacing.step * 4))
            Text(title, style = MaterialTheme.typography.quizTitle, color = MaterialTheme.colorScheme.onBackground)
            if (question.id.startsWith("SPH") || question.id.startsWith("NEED")) {
                Spacer(Modifier.height(Spacing.step * 2))
                Text(question.fields.values.first { it != question.id && it != question.sphere_id && it != question.linked },
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            }
            if (question.type != "profile") {
                Spacer(Modifier.height(Spacing.step * 3))
                Text(if (question.type in listOf("text", "text_energy5")) stringResource(instruction, textLimit(question)) else stringResource(instruction),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.step * 4))
            when (question.type) {
                "profile" -> ProfileFields(groupedQuestions, answer, onAnswer)
                "rank_values" -> RankValues(groupedQuestions, rankingAnswer(answer), onAnswer)
                "pick3" -> PickThree(question.options, selectedIds(answer)) { onAnswer(Json.encodeToString(it)) }
                "text" -> TextAnswer(question.id, answer.orEmpty(), textLimit(question), onAnswer)
                "text_energy5" -> ExperienceFields(question, experienceAnswer(answer), onAnswer)
                "pair" -> PairChoice(question, answer, pairValues, onAnswer)
                "single_choice" -> SingleChoice(question.options, answer, onAnswer)
                "likert5", "energy5", "scale", "scale10" -> {
                    val scale = requireNotNull(question.scale ?: question.energy)
                    val minimum = when {
                        question.type == "energy5" -> stringResource(R.string.test_energy_min)
                        question.id.startsWith("NEED") -> stringResource(R.string.test_needs_min)
                        question.type == "likert5" -> stringResource(R.string.test_interest_min)
                        else -> scale.min.toString()
                    }
                    val maximum = when {
                        question.type == "energy5" -> stringResource(R.string.test_energy_max)
                        question.id.startsWith("NEED") -> stringResource(R.string.test_needs_max)
                        question.type == "likert5" -> stringResource(R.string.test_interest_max)
                        else -> scale.max.toString()
                    }
                    Scale(scale.min..scale.max, answer?.toIntOrNull(), minimum, maximum, question.id) { onAnswer(it.toString()) }
                }
            }
            if (!question.id.startsWith("TSK")) {
                Spacer(Modifier.height(Spacing.step * 4))
                Text(stringResource(R.string.test_no_right_answers), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.step * 2))
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.step), horizontalArrangement = Arrangement.spacedBy(Spacing.step)) {
            OutlinedButton(onClick = onBack, enabled = canBack && !busy, shape = androidx.compose.ui.graphics.RectangleShape,
                modifier = Modifier.weight(1f).height(QuizSpacing.scaleHeight)) { Text(stringResource(R.string.test_back)) }
            Button(onClick = onNext, shape = RoundedCornerShape(topEnd = PrintSpacing.leafCorner),
                modifier = Modifier.weight(2f).height(QuizSpacing.scaleHeight),
                enabled = (canAdvance ?: validQuizAnswer(question, answer, groupedQuestions, pairValues)) && !busy) {
                Text(stringResource(if (isLastScreen) R.string.test_show_result else R.string.test_next), Modifier.weight(1f))
                Icon(painterResource(R.drawable.arrow_right), null, Modifier.size(QuizSpacing.arrowSize))
            }
        }
        Spacer(Modifier.height(Spacing.step * 2))
    }
}

@Composable
private fun QuizHeader(onExit: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = Spacing.step), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.quiz_brand), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = onExit, contentPadding = PaddingValues(Spacing.none), modifier = Modifier.heightIn(min = QuizSpacing.touch)) {
            Text(stringResource(R.string.quiz_close), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun QuizProgress(part: String?, position: Int, total: Int, overallPercent: Int?) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("quiz_progress")) {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.step * 2), verticalAlignment = Alignment.Bottom) {
            Text("%02d".format(position + 1), Modifier.weight(1f), style = MaterialTheme.typography.printNumber, color = MaterialTheme.colorScheme.primary)
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.quiz_position, total, part?.let { stringResource(partTitle(it)) }.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (overallPercent != null) Text(stringResource(R.string.test_progress_percent, overallPercent),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Spacing.step * 2))
        val colors = MaterialTheme.colorScheme
        val progress = overallPercent?.div(100f) ?: ((position + 1).toFloat() / total)
        Canvas(Modifier.fillMaxWidth().height(PrintSpacing.rulerHeight)) {
            val gap = PrintSpacing.rulerGap.toPx()
            for (i in 0..(size.width / gap).toInt()) {
                val active = i * gap <= size.width * progress
                drawLine(if (active) colors.primary else colors.outlineVariant,
                    androidx.compose.ui.geometry.Offset(i * gap, if (active) 0f else size.height * .55f),
                    androidx.compose.ui.geometry.Offset(i * gap, size.height),
                    if (active) QuizSpacing.stroke.toPx() * 3 else QuizSpacing.stroke.toPx() * 1.5f)
            }
        }
    }
}

private fun partTitle(part: String) = when (part) {
    "profile" -> R.string.part_profile
    "interests" -> R.string.part_interests
    "values" -> R.string.part_values
    "open" -> R.string.part_open
    "sphere_interest" -> R.string.part_sphere_interest
    "tasks" -> R.string.part_tasks
    "experience" -> R.string.part_experience
    "constraints" -> R.string.part_constraints
    "needs" -> R.string.part_needs
    "happiness" -> R.string.part_happiness
    else -> R.string.part_consistency
}

@Composable
internal fun QuizRoute(onExit: () -> Unit, onResult: (String) -> Unit,
    viewModel: QuizViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()) {
    SessionTestFlow(viewModel, onExit, onResult)
}

@Composable
private fun SessionTestFlow(viewModel: QuizViewModel,
    onExit: () -> Unit, onResult: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val screen = state.screen
    val session = state.session
    var started by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var exitDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = started) { exitDialog = true }
    if (!started) {
        SessionIntro(session, onStart = { started = true })
    } else if (screen != null && session != null) {
        SessionQuestion(state, screen, session, viewModel, busy,
            onShowExit = { exitDialog = true },
            onBack = { scope.launch { viewModel.awaitSaved(); viewModel.back() } },
            onNext = {
                if (state.index < state.screens.lastIndex) scope.launch { viewModel.awaitSaved(); viewModel.next() } else {
                    busy = true
                    scope.launch {
                        try {
                            val sessionId = viewModel.complete()
                            withContext(Dispatchers.Main.immediate) { onResult(sessionId) }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { error = true } finally { busy = false }
                    }
                }
            }, onExit = { scope.launch { viewModel.awaitSaved(); onExit() } })
    } else {
        SessionLoading()
    }
    SessionDialogs(error || state.error != null, exitDialog, onDismissError = { error = false },
        onDismissExit = { exitDialog = false }, onExit = {
            scope.launch { viewModel.awaitSaved(); exitDialog = false; onExit() }
        })
}

@Composable
internal fun SessionIntro(session: TestSessionState?, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
        .padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.step * 3)) {
        BrandHeader()
        Text(androidx.compose.ui.text.buildAnnotatedString {
            append(stringResource(R.string.test_title))
            withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.secondary)) { append(".") }
        }, style = MaterialTheme.typography.displayLarge.copy(fontSize = 29.sp, lineHeight = 32.sp, letterSpacing = (-1).sp), maxLines = 2)
        ru.voploshchenie.app.ui.components.PrintCard {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ru.voploshchenie.app.ui.components.Kicker(stringResource(R.string.test_intro_kicker).uppercase())
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.test_duration_value), color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp, lineHeight = 52.sp), fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(R.string.test_duration_unit), Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.secondary))
                    Text(stringResource(R.string.test_disclaimer), Modifier.padding(start = 14.dp), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp))
                }
            }
        }
        PrimaryButton(stringResource(if (session?.answers == TestAnswers()) R.string.test_start else R.string.continue_test),
            onClick = onStart, enabled = session != null, modifier = Modifier.testTag("test_start_or_continue"))
    }
}

private fun TestAnswers.withScreenAnswer(screen: TestScreen, value: String): TestAnswers =
    when {
        screen.part == "profile" -> copy(profile = profile + profileAnswer(value).mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) })
        screen.part == "values" -> rankingAnswer(value).let { copy(valuesTop3 = it.top, valuesLeast2 = it.least) }
        screen.step?.type == "pick3" -> copy(needs = needs.copy(top3 = selectedIds(value)))
        screen.part == "needs" -> copy(needs = needs.copy(ratings = needs.ratings + (screen.questions.single().id to value.toInt())))
        screen.part == "interests" -> copy(pairs = pairs + (screen.id to value))
        screen.part == "consistency" -> copy(checks = checks + (screen.id to value))
        screen.part == "sphere_interest" -> copy(interest = interest + (screen.id to value.toInt()))
        screen.part == "tasks" -> copy(tasks = tasks + (screen.id to value))
        screen.part == "experience" -> copy(experience = experience + (screen.id to experienceAnswer(value)))
        screen.part == "constraints" -> copy(nad = nad + (screen.id to value))
        screen.part == "open" || screen.part == "happiness" -> copy(open = open + (screen.id to value))
        else -> this
    }

private fun screenAnswer(screen: TestScreen, answers: TestAnswers): String? {
    return when {
        screen.part == "profile" -> Json.encodeToString(answers.profile.mapValues { it.value.jsonPrimitive.content })
        screen.part == "values" -> Json.encodeToString(ValueSelection(answers.valuesTop3, answers.valuesLeast2))
        screen.step?.type == "pick3" -> Json.encodeToString(answers.needs.top3)
        else -> when (screen.part) {
            "interests" -> answers.pairs[screen.id]
            "consistency" -> answers.checks[screen.id]
            "sphere_interest" -> answers.interest[screen.id]?.toString()
            "tasks" -> answers.tasks[screen.id]
            "experience" -> answers.experience[screen.id]?.let { Json.encodeToString(it) }
            "constraints" -> answers.nad[screen.id]
            "open", "happiness" -> answers.open[screen.id]
            "needs" -> screen.questions.singleOrNull()?.let { answers.needs.ratings[it.id]?.toString() }
            else -> null
        }
    }
}

private fun screenQuestion(screen: TestScreen): ConfigQuestion {
    return when {
        screen.part == "profile" -> ConfigQuestion("profile", emptyMap(), "profile")
        screen.part == "values" -> ConfigQuestion("values", emptyMap(), "rank_values")
        screen.step?.type == "pick3" -> ConfigQuestion("needs/${screen.step.id}", emptyMap(), "pick3", screen.step.options, screen.step.prompt, min = screen.step.min, max = screen.step.max)
        else -> screen.questions.first()
    }
}

@Composable
private fun SessionQuestion(state: TestState, screen: TestScreen, session: TestSessionState, viewModel: QuizViewModel,
    busy: Boolean, onShowExit: () -> Unit, onBack: () -> Unit, onNext: () -> Unit, onExit: () -> Unit) {
    val progress = state.progress
    val answer = screenAnswer(screen, session.answers)
    val question = screenQuestion(screen)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (screen.part == "needs" && screen.step?.type != "pick3") {
            SessionNeeds(state, screen, session, viewModel, busy, onShowExit, onBack, onNext)
        } else {
            TestQuestionScreen(question, progress?.let { it.number - 1 } ?: state.index,
                progress?.total ?: state.screens.size, answer,
                onAnswer = { value -> viewModel.updateAnswers { it.withScreenAnswer(screen, value) } }, onBack = viewModel::back,
                onNext = onNext, onExit = onExit, busy = busy,
                groupedQuestions = if (screen.part == "profile" || screen.part == "values") screen.questions else emptyList(),
                pairValues = screen.pairTypes, canBack = state.canBack,
                canAdvance = if (state.index == state.screens.lastIndex) state.canComplete else state.canNext,
                isLastScreen = state.index == state.screens.lastIndex,
                part = screen.part, overallPercent = progress?.percent)
        }
    }
}

@Composable
private fun ColumnScope.SessionNeeds(state: TestState, screen: TestScreen, session: TestSessionState,
    viewModel: QuizViewModel, busy: Boolean, onShowExit: () -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    val progress = state.progress
    Column(Modifier.padding(horizontal = Spacing.screen)) {
        QuizHeader(onExit = onShowExit)
        QuizProgress(screen.part, (progress?.number ?: 1) - 1, progress?.total ?: 1, progress?.percent)
        Spacer(Modifier.height(Spacing.step * 3))
        Text(stringResource(R.string.test_needs_title), style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(Spacing.step * 2))
    }
    NeedsRatings(screen.questions, session.answers.needs.ratings, Modifier.weight(1f)) { id, value ->
        viewModel.updateAnswers { a -> a.copy(needs = a.needs.copy(ratings = a.needs.ratings + (id to value))) }
    }
    Column(Modifier.padding(horizontal = Spacing.screen)) {
        TextButton(onClick = onBack, enabled = state.canBack,
            modifier = Modifier.heightIn(min = QuizSpacing.touch)) { Text(stringResource(R.string.test_back)) }
        PrimaryButton(stringResource(R.string.test_next), enabled = state.canNext && !busy,
            onClick = onNext)
        Spacer(Modifier.height(Spacing.step * 2))
    }
}

@Composable
private fun SessionLoading() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.step * 3)) {
        Text(stringResource(R.string.test_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.test_duration), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.test_disclaimer), style = MaterialTheme.typography.bodyLarge)
        CircularProgressIndicator()
    }
}

@Composable
private fun SessionDialogs(hasError: Boolean, showExit: Boolean,
    onDismissError: () -> Unit, onDismissExit: () -> Unit, onExit: () -> Unit) {
    if (hasError) AlertDialog(onDismissRequest = onDismissError,
        title = { Text(stringResource(R.string.test_save_error)) }, confirmButton = { TextButton(onClick = onDismissError) { Text(stringResource(R.string.test_continue)) } })
    if (showExit) AlertDialog(onDismissRequest = onDismissExit,
        title = { Text(stringResource(R.string.test_saved)) },
        text = { Text(stringResource(R.string.test_saved_body)) },
        confirmButton = { TextButton(onClick = onExit) { Text(stringResource(R.string.test_leave)) } },
        dismissButton = { TextButton(onClick = onDismissExit) { Text(stringResource(R.string.test_continue)) } })
}
