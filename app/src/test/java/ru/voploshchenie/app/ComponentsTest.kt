package ru.voploshchenie.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import ru.voploshchenie.app.ui.components.PrimaryButton
import ru.voploshchenie.app.ui.components.SecondaryButton
import ru.voploshchenie.app.ui.components.Kicker
import ru.voploshchenie.app.ui.components.Chip
import ru.voploshchenie.app.ui.components.SaveButton
import ru.voploshchenie.app.ui.components.OpportunityCard
import ru.voploshchenie.app.ui.theme.AppTheme
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.res.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class F02ComponentsTest {
    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun kitComponentsRenderAndRespondToSelection() {
        compose.activity.setContent {
            AppTheme {
                var saved by remember { mutableStateOf(false) }
                var selected by remember { mutableStateOf(false) }
                Column {
                    PrimaryButton(stringResource(R.string.take_test), {})
                    SecondaryButton(stringResource(R.string.tab_profile), {})
                    Kicker(stringResource(R.string.tab_opportunities))
                    Chip(stringResource(R.string.tab_opportunities), selected, onClick = { selected = !selected })
                    SaveButton(saved, { saved = !saved })
                    OpportunityCard(
                        kicker = stringResource(R.string.tab_opportunities),
                        title = stringResource(R.string.app_name),
                        meta = stringResource(R.string.opportunity_title),
                        day = "30",
                        month = "ОКТ",
                    )
                }
            }
        }

        compose.onNodeWithText("Пройти профтест").assertIsDisplayed()
        compose.onNodeWithText("Профиль").assertIsDisplayed()
        org.junit.Assert.assertEquals(2, compose.onAllNodesWithText("ВОЗМОЖНОСТИ").fetchSemanticsNodes().size)
        compose.onNodeWithText("Возможности").performClick().assertIsSelected()
        compose.onNodeWithText("Сохранить").performClick()
        compose.onNodeWithText("В избранном").assertIsDisplayed()
        compose.onNodeWithText("30").assertIsDisplayed()
        compose.onNodeWithText("ОКТ").assertIsDisplayed()
    }
}
