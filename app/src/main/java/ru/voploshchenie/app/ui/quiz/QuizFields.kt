package ru.voploshchenie.app.ui.quiz

import androidx.compose.runtime.setValue
import ru.voploshchenie.app.domain.model.ValueSelection
import ru.voploshchenie.app.domain.model.profileAnswer
import ru.voploshchenie.app.domain.model.textLimit
import ru.voploshchenie.app.domain.model.ConfigOption
import ru.voploshchenie.app.domain.model.ConfigQuestion
import ru.voploshchenie.app.domain.scoring.ExperienceAnswer
import ru.voploshchenie.app.R
import ru.voploshchenie.app.ui.theme.CardRadius
import ru.voploshchenie.app.ui.theme.QuizSpacing
import ru.voploshchenie.app.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
internal fun TextAnswer(id: String, value: String, limit: Int, onChange: (String) -> Unit) {
    var text by rememberSaveable(id) { mutableStateOf(value) }
    LaunchedEffect(Unit) { if (text != value) onChange(text) }
    Column {
        TextField(value = text, onValueChange = {
            text = it.take(limit)
            onChange(text)
        }, modifier = Modifier.fillMaxWidth().heightIn(min = QuizSpacing.scaleHeight).testTag("text_$id"),
            shape = RoundedCornerShape(CardRadius), textStyle = MaterialTheme.typography.bodyLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ))
        Text(stringResource(R.string.test_text_counter, text.length, limit), modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ProfileFields(questions: List<ConfigQuestion>, answer: String?, onAnswer: (String) -> Unit) {
    val values = profileAnswer(answer)
    fun update(id: String, value: String) = onAnswer(Json.encodeToString(values + (id to value)))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.step * 3)) {
        questions.forEach { question ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.step)) {
                Text(question.fields.getValue("Поле"), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                when (question.type) {
                    "text" -> TextAnswer(question.id, values[question.id].orEmpty(), textLimit(question)) { update(question.id, it) }
                    "single_choice" -> {
                        SingleChoice(question.options, values[question.id]) { update(question.id, it) }
                        if (question.id == "PRF06" && values[question.id] == question.options.last().id) {
                            Text(stringResource(R.string.test_other), style = MaterialTheme.typography.bodyLarge)
                            TextAnswer("PRF06_other", values["PRF06_other"].orEmpty(), 200) { update("PRF06_other", it) }
                        }
                    }
                    else -> requireNotNull(question.scale).let { scale ->
                        Scale(scale.min..scale.max, values[question.id]?.toIntOrNull(), scale.min.toString(), scale.max.toString(), question.id) { update(question.id, it.toString()) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExperienceFields(question: ConfigQuestion, answer: ExperienceAnswer, onAnswer: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.step * 3)) {
        TextAnswer(question.id, answer.text, textLimit(question)) { onAnswer(Json.encodeToString(answer.copy(text = it))) }
        requireNotNull(question.energy).let { scale ->
            Scale(scale.min..scale.max, answer.energy, stringResource(R.string.test_energy_min), stringResource(R.string.test_energy_max), "${question.id}_energy") {
                onAnswer(Json.encodeToString(answer.copy(energy = it)))
            }
        }
    }
}

@Composable
internal fun RankValues(questions: List<ConfigQuestion>, answer: ValueSelection, onAnswer: (String) -> Unit) {
    val limits = requireNotNull(questions.first().ranking)
    Column(verticalArrangement = Arrangement.spacedBy(QuizSpacing.optionGap)) {
        Text(stringResource(R.string.test_values_remaining, limits.top - answer.top.count { it.isNotEmpty() }, limits.least - answer.least.size),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        questions.forEach { question ->
            Surface(shape = RoundedCornerShape(CardRadius), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(QuizSpacing.optionPadding), verticalArrangement = Arrangement.spacedBy(Spacing.step)) {
                    Text(question.fields.getValue("Название"), style = MaterialTheme.typography.titleMedium)
                    Text(question.fields.getValue("Пояснение на карточке"), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.step)) {
                        (1..limits.top).forEach { rank ->
                            val selected = answer.top.getOrNull(rank - 1) == question.id
                            FilterChip(selected, onClick = {
                                val top = MutableList(limits.top) { answer.top.getOrNull(it).orEmpty() }
                                top[rank - 1] = if (selected) "" else question.id
                                onAnswer(Json.encodeToString(answer.copy(top = top)))
                            }, label = { Text(rank.toString()) },
                                enabled = selected || (question.id !in answer.least && question.id !in answer.top && answer.top.getOrNull(rank - 1).isNullOrEmpty()),
                                modifier = Modifier.heightIn(min = QuizSpacing.touch).testTag("${question.id}_$rank"))
                        }
                    }
                    val selected = question.id in answer.least
                    FilterChip(selected, onClick = {
                        val least = if (selected) answer.least - question.id else answer.least + question.id
                        onAnswer(Json.encodeToString(answer.copy(least = least)))
                    }, label = { Text(stringResource(R.string.test_least_important)) },
                        enabled = selected || (question.id !in answer.top && answer.least.size < limits.least),
                        modifier = Modifier.heightIn(min = QuizSpacing.touch).testTag("${question.id}_least"))
                }
            }
        }
    }
}

@Composable
internal fun PickThree(options: List<ConfigOption>, selected: List<String>, onSelect: (List<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(QuizSpacing.optionGap)) {
        Text(stringResource(R.string.test_pick_remaining, 3 - selected.size), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        options.forEach { option ->
            val checked = option.id in selected
            Surface(shape = RoundedCornerShape(CardRadius), color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().heightIn(min = QuizSpacing.optionHeight)
                    .toggleable(checked, enabled = checked || selected.size < 3, role = Role.Checkbox) {
                        onSelect(if (checked) selected - option.id else selected + option.id)
                    }.testTag("pick_${option.id}")) {
                Row(Modifier.padding(QuizSpacing.optionPadding)) {
                    Checkbox(checked, null)
                    Spacer(Modifier.width(QuizSpacing.radioGap))
                    Text(option.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
