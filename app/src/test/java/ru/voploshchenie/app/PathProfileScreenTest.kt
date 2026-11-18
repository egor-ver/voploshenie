package ru.voploshchenie.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.voploshchenie.app.domain.model.PathProfileData
import ru.voploshchenie.app.ui.path.PathScreen
import ru.voploshchenie.app.ui.path.PathUiState
import ru.voploshchenie.app.ui.profile.ProfileScreen
import ru.voploshchenie.app.ui.theme.AppTheme

@RunWith(RobolectricTestRunner::class)
class V02ScreenTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun pathEmptyAndContentSwitchCtaAndSavedMarks() {
        val state = mutableStateOf<PathUiState>(PathUiState.Empty)
        var tests = 0
        var collections = 0
        compose.activity.setContent { AppTheme { PathScreen(state.value, { tests++ }, { collections++ }) } }
        compose.onNodeWithText(compose.activity.getString(R.string.path_step_work)).assertExists()
        compose.onNodeWithTag("path_step_1_current").assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.take_test)).assertExists().performClick()
        assertEquals(1, tests)
        state.value = PathUiState.Content(PathProfileData(null, 'I', listOf("it", "business", "biology"), "it", "Разработка"))
        compose.waitForIdle()
        compose.onNodeWithText(compose.activity.getString(R.string.path_cta_collection)).assertExists().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.path_step_work)).assertExists()
        compose.onNodeWithTag("path_step_1_complete").assertExists()
        compose.onNodeWithTag("path_step_3_pending").assertExists()
        compose.onNodeWithTag("path_step_4_pending").assertExists()
        assertEquals(1, collections)
    }

    @Test fun profileShowsFallbackNameTopThreeAndRepeatAction() {
        var repeated = 0
        compose.activity.setContent {
            AppTheme { ProfileScreen(PathUiState.Content(PathProfileData(null, 'A', listOf("it", "business", "biology"), "it", "data")), { repeated++ }, {}) }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.profile_unnamed)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.label_creator)).assertExists()
        compose.onNodeWithText(listOf(R.string.sphere_it, R.string.sphere_business, R.string.sphere_biology).joinToString(" · ") { compose.activity.getString(it) }).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.profile_repeat_test)).performScrollTo().performClick()
        assertEquals(1, repeated)
    }

    @Test fun profileWithoutResultOffersTestActionWithoutThemeControls() {
        compose.activity.setContent { AppTheme { ProfileScreen(PathUiState.Empty, {}, {}) } }
        compose.onNodeWithText(compose.activity.getString(R.string.profile_no_result)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.take_test)).assertExists()
        compose.onNodeWithTag("theme_radio_DARK").assertDoesNotExist()
    }

    @Test fun loadingAndErrorRemainDistinctAndCanRetry() {
        compose.activity.setContent { AppTheme { PathScreen(PathUiState.Loading, {}, {}) } }
        compose.onNodeWithTag("path_loading").assertExists()
        var retried = 0
        compose.activity.setContent { AppTheme { PathScreen(PathUiState.Error, {}, {}, { retried++ }) } }
        compose.onNodeWithText(compose.activity.getString(R.string.retry)).assertExists().performClick()
        assertEquals(1, retried)
    }
}
