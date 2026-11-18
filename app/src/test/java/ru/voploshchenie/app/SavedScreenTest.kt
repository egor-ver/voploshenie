package ru.voploshchenie.app

import ru.voploshchenie.app.data.local.asset.CatalogLoader
import ru.voploshchenie.app.domain.model.SavedItem
import ru.voploshchenie.app.ui.saved.SavedScreen
import ru.voploshchenie.app.ui.theme.AppTheme
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class V01SavedScreenTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun entryMissingFromProvidedCatalogShowsUnavailableAndActionOpensOpportunities() {
        val item = CatalogLoader.load(compose.activity).first()
        val opened = java.util.concurrent.atomic.AtomicBoolean(false)
        compose.activity.setContent { AppTheme { SavedScreen(ru.voploshchenie.app.ui.saved.SavedUiState.Content(listOf(SavedItem(item.id, 0)), emptyList()), {}, { opened.set(true) }) } }

        compose.onNodeWithText(compose.activity.getString(R.string.saved_unavailable)).assertExists()
        compose.onNodeWithText(compose.activity.resources.getQuantityString(R.plurals.saved_count, 1, 1)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.saved_find_more)).performClick()
        assertTrue(opened.get())
    }
}
