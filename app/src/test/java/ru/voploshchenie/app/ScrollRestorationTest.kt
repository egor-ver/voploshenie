package ru.voploshchenie.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.voploshchenie.app.data.local.asset.CatalogLoader
import ru.voploshchenie.app.domain.repository.CatalogRepository
import ru.voploshchenie.app.ui.opportunities.*
import ru.voploshchenie.app.ui.theme.AppTheme

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h600dp-mdpi")
class A04ScrollRestorationTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val viewModels = ViewModelStore()
    @After fun clearViewModels() { viewModels.clear() }

    @Test fun catalogScrollSurvivesRestorationWhileRefreshIsPending() = retainsScroll(false, true)
    @Test fun catalogScrollSurvivesReturnWhileRefreshIsPending() = retainsScroll(false, false)
    // карточка помещается на экран 600 dp целиком — для проверки прокрутки берём низкий экран
    @Config(qualifiers = "w390dp-h400dp-mdpi")
    @Test fun detailScrollSurvivesRestorationWhileRefreshIsPending() = retainsScroll(true, true)
    @Config(qualifiers = "w390dp-h400dp-mdpi")
    @Test fun detailScrollSurvivesReturnWhileRefreshIsPending() = retainsScroll(true, false)

    private fun retainsScroll(detail: Boolean, restore: Boolean) {
        val items = CatalogLoader.load(compose.activity)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var pause = false
        val catalog = object : CatalogRepository {
            override suspend fun all(): List<ru.voploshchenie.app.domain.model.CatalogItem> {
                if (pause) { entered.complete(Unit); release.await() }
                return items
            }
        }
        val results = FakeResults()
        val saved = FakeSaved()
        val viewModel = if (detail) OpportunityDetailViewModel(catalog, results, saved,
            SavedStateHandle(mapOf("itemId" to items.filterNot { ru.voploshchenie.app.domain.usecase.opportunityClosed(it, java.time.LocalDate.now()) }.maxBy { it.title.length + it.direction.length + it.dates.size * 40 }.id)))
            else OpportunitiesViewModel(catalog, results, saved, ru.voploshchenie.app.data.local.asset.ConfigLoader.load(compose.activity), SavedStateHandle())
        viewModels.put("screen", viewModel)
        val tester = StateRestorationTester(compose)
        var visible by mutableStateOf(true)
        tester.setContent {
            val holder = rememberSaveableStateHolder()
            AppTheme {
                if (visible) holder.SaveableStateProvider("screen") {
                    if (detail) OpportunityDetailRoute({}, {}, viewModel as OpportunityDetailViewModel)
                    else OpportunitiesRoute({}, {}, viewModel = viewModel as OpportunitiesViewModel)
                }
            }
        }
        val tag = if (detail) "opportunity_detail" else "catalog_list"
        compose.waitUntil { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNodeWithTag(tag)
        if (detail) node.performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> scroll(0f, 240f) }
        else node.performScrollToIndex(10)
        val offset = scrollOffset(tag)
        assertTrue(offset > 0)
        try {
            compose.runOnIdle { pause = true }
            if (restore) tester.emulateSavedInstanceStateRestore() else {
                compose.runOnIdle { visible = false }
                compose.onNodeWithTag(tag).assertDoesNotExist()
                compose.runOnIdle { visible = true }
            }
            compose.onNodeWithTag(tag).assertExists()
            compose.waitUntil(10_000) { compose.waitForIdle(); entered.isCompleted }
            assertEquals(offset, scrollOffset(tag), .001f)
            release.complete(Unit)
            compose.waitForIdle()
            assertEquals(offset, scrollOffset(tag), .001f)
        } finally { release.complete(Unit) }
    }

    private fun scrollOffset(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()
}
