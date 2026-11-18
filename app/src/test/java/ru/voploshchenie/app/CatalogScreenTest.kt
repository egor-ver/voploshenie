package ru.voploshchenie.app

import ru.voploshchenie.app.data.local.asset.CatalogLoader
import ru.voploshchenie.app.domain.model.CatalogDate
import ru.voploshchenie.app.domain.model.CatalogSphereResult
import ru.voploshchenie.app.domain.model.CatalogResult
import ru.voploshchenie.app.domain.usecase.CatalogSelection
import ru.voploshchenie.app.ui.opportunities.OpportunitiesRoute
import ru.voploshchenie.app.ui.opportunities.OpportunitiesViewModel
import ru.voploshchenie.app.ui.opportunities.OpportunitiesUiState
import ru.voploshchenie.app.ui.opportunities.CardDate
import ru.voploshchenie.app.ui.opportunities.catalogCardDate
import ru.voploshchenie.app.ui.opportunities.CatalogSort
import ru.voploshchenie.app.ui.theme.AppTheme
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-mdpi")
class C03ScreenTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun catalog() = CatalogLoader.load(compose.activity)

    @Test fun chipFiltersAndResetRestoresCatalog() {
        val items = catalog()
        val vm = viewModel(items)
        compose.activity.setContent { AppTheme { OpportunitiesRoute({}, {}, viewModel = vm) } }
        compose.onNodeWithTag("catalog_type_1").performClick().assertIsSelected()
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_found, items.count { it.type == "olympiad" })).assertExists()
        compose.onNodeWithTag("catalog_search").performTextInput("∅∅∅")
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_empty)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_reset)).performClick()
        compose.onNodeWithTag("catalog_type_0").assertIsSelected()
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_found, items.size)).assertExists()
    }

    @Test fun searchMatchesOrganizerIgnoringCaseAndWhitespace() {
        val source = catalog()
        val item = source.first().copy(organizer = source.last().title)
        val items = listOf(item) + source.drop(1)
        val vm = viewModel(items)
        compose.activity.setContent { AppTheme { OpportunitiesRoute({}, {}, viewModel = vm) } }
        val query = " ${item.organizer!!.uppercase()} "
        compose.onNodeWithTag("catalog_search").performTextInput(query)
        val expected = CatalogSelection(items).filter(query = query)
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_found, expected.size)).assertExists()
        compose.onNodeWithTag("catalog_list").performScrollToNode(hasTestTag("opportunity_${item.id}"))
        compose.onNodeWithTag("opportunity_${item.id}").assertExists()
    }

    @Test fun sortChangesOrderAndKeepsQueryAndChip() {
        val catalog = catalog()
        val olympiads = catalog.filter { it.type == "olympiad" && it.title.contains("олимпиада", true) }
        val today = java.time.LocalDate.now()
        val sooner = olympiads.filter { it.spheres == listOf("business") }.minBy { it.title.length }
            .copy(dates = listOf(CatalogDate("apply_deadline", today.plusDays(1).toString(), "day")))
        val matching = olympiads.filter { it.spheres == listOf("finance") }.minBy { it.title.length }
            .copy(dates = listOf(CatalogDate("apply_deadline", today.plusDays(2).toString(), "day")))
        val items = listOf(matching, sooner, catalog.first { it.type == "course" })
        val result = CatalogResult(listOf(CatalogSphereResult("finance", 100), CatalogSphereResult("business", 10)), listOf("finance"))
        val vm = viewModel(items, result)
        compose.activity.setContent { AppTheme { OpportunitiesRoute({}, {}, viewModel = vm) } }
        compose.onNodeWithTag("catalog_type_1").performClick()
        compose.onNodeWithTag("catalog_search").performTextInput("ОЛИМПИАДА")
        assertAbove(matching.id, sooner.id)
        compose.onNodeWithTag("catalog_sort").performClick()
        compose.onNodeWithTag("catalog_sort_false").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_apply)).performClick()
        compose.onNodeWithTag("catalog_type_1").assertIsSelected()
        compose.onNodeWithTag("catalog_search").assertTextEquals("ОЛИМПИАДА")
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_found, 2)).assertExists()
        assertAbove(sooner.id, matching.id)
        compose.onNodeWithTag("catalog_sort").performClick()
        compose.onNodeWithTag("catalog_sort_true").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_apply)).performClick()
        assertAbove(matching.id, sooner.id)
        compose.onNodeWithTag("catalog_type_1").assertIsSelected()
        compose.onNodeWithTag("catalog_search").assertTextEquals("ОЛИМПИАДА")
    }

    private fun viewModel(items: List<ru.voploshchenie.app.domain.model.CatalogItem>, result: CatalogResult? = null): OpportunitiesViewModel {
        val results = FakeResults()
        if (result != null) {
            val config = ru.voploshchenie.app.data.local.asset.ConfigLoader.load(compose.activity)
            val raw = compose.activity.assets.open("demo_answers.json").bufferedReader().use {
                kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(it.readText())
            }
            val answers = ru.voploshchenie.app.domain.usecase.scoringAnswers(raw, config)
            val score = ru.voploshchenie.app.domain.scoring.ScoringEngine.compute(answers, config)
            results.row = ru.voploshchenie.app.domain.model.SavedTestResult("test", 0, config.version.toString(),
                kotlinx.serialization.json.Json.encodeToString(ru.voploshchenie.app.domain.scoring.ScoringResult.serializer(),
                    score.copy(spheres = score.spheres.map { sphere -> sphere.copy(percent = result.spheres.firstOrNull { it.id == sphere.id }?.percent ?: 0) }, topSpheres = result.topSpheres)), 0)
        }
        return OpportunitiesViewModel(FakeCatalog(items), results, FakeSaved(), ru.voploshchenie.app.data.local.asset.ConfigLoader.load(compose.activity), androidx.lifecycle.SavedStateHandle())
    }

    private fun assertAbove(first: String, second: String) {
        val firstBounds = compose.onNodeWithTag("opportunity_$first").fetchSemanticsNode().boundsInRoot
        val secondBounds = compose.onNodeWithTag("opportunity_$second").fetchSemanticsNode().boundsInRoot
        assertTrue("$first должен находиться выше $second", firstBounds.top < secondBounds.top)
    }

    @Test fun interestsRequireResult() {
        compose.activity.setContent { AppTheme { CatalogSort(false, false) {} } }
        compose.onNodeWithTag("catalog_sort_true").assertIsNotEnabled()
        compose.onNodeWithTag("catalog_sort_false").assertIsSelected()
    }

    @Test fun recommendationsShowOtherLabelAndVisibleCount() {
        val sample = catalog().first()
        val items = listOf(
            sample.copy(id = "primary", spheres = listOf("it")),
            sample.copy(id = "secondary", spheres = listOf("business")),
            sample.copy(id = "other", spheres = listOf("biology")),
        )
        val result = CatalogResult(emptyList(), listOf("it", "business", "engineering"))
        val selection = CatalogSelection(items)
        val state = OpportunitiesUiState.Content(
            items.filter { it.id == "other" },
            result, selection.recommendations(result), interests = false, recommendationsOnly = true,
        )
        compose.activity.setContent { AppTheme { ru.voploshchenie.app.ui.opportunities.OpportunitiesScreen(state) } }
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_found, 1)).assertExists()
        compose.onNodeWithTag("catalog_list").performScrollToNode(hasTestTag("recommendation_from_other"))
        compose.onNodeWithTag("recommendation_from_other")
            .assertTextEquals(compose.activity.getString(R.string.catalog_other_opportunities))
    }

    @Test fun datePrecisionAndInvalidDatesDoNotInventDays() {
        val item = catalog().first()
        assertEquals(CardDate("25", 10), catalogCardDate(item.copy(dates = listOf(CatalogDate("apply_deadline", "2026-10-25", "day")))))
        assertEquals(CardDate(month = 10), catalogCardDate(item.copy(dates = listOf(CatalogDate("start", "2026-10", "month")))))
        assertEquals(CardDate(year = "2026"), catalogCardDate(item.copy(dates = listOf(CatalogDate("start", "2026", "year")))))
        assertEquals(CardDate(), catalogCardDate(item.copy(dates = listOf(CatalogDate("start", "invalid", "day")))))
        assertEquals(CardDate(), catalogCardDate(item.copy(dates = emptyList(), is_rolling = true)))
    }

    @Test fun locationNotesRequireExactCityOrRemoteFormatAndLocalPreference() {
        val sample = catalog().first()
        val items = listOf(
            sample.copy(id = "nearby", city = "казань", format = "hybrid"),
            sample.copy(id = "remote", city = "Москва", format = "remote"),
            sample.copy(id = "hybrid", city = "Казань область", format = "hybrid"),
        )
        val state = OpportunitiesUiState.Content(items, profileCity = " Казань ", locationPreference = "a", recommendationsOnly = true)
        val nearby = compose.activity.getString(R.string.catalog_nearby)
        val online = compose.activity.getString(R.string.catalog_online)
        compose.activity.setContent { AppTheme { ru.voploshchenie.app.ui.opportunities.OpportunitiesScreen(state) } }
        compose.onNodeWithText(nearby, substring = true).assertExists()
        compose.onNodeWithText(online, substring = true).assertExists()
        assertEquals(1, compose.onAllNodesWithText(nearby, substring = true).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText(online, substring = true).fetchSemanticsNodes().size)
        compose.activity.setContent {
            AppTheme { ru.voploshchenie.app.ui.opportunities.OpportunitiesScreen(state.copy(locationPreference = "b")) }
        }
        compose.onNodeWithText(nearby, substring = true).assertExists()
        compose.activity.setContent {
            AppTheme { ru.voploshchenie.app.ui.opportunities.OpportunitiesScreen(state.copy(locationPreference = "c")) }
        }
        compose.onNodeWithText(nearby, substring = true).assertDoesNotExist()
        compose.onNodeWithText(online, substring = true).assertDoesNotExist()
        compose.activity.setContent {
            AppTheme { ru.voploshchenie.app.ui.opportunities.OpportunitiesScreen(state.copy(locationPreference = "d")) }
        }
        compose.onNodeWithText(nearby, substring = true).assertDoesNotExist()
        compose.onNodeWithText(online, substring = true).assertDoesNotExist()
    }

    @Test fun selectedInternshipChipCanBeRemoved() {
        val internship = catalog().first { it.type == "internship" }
        var selectedType = 2
        val state = OpportunitiesUiState.Content(listOf(internship), type = selectedType)
        compose.activity.setContent {
            AppTheme { ru.voploshchenie.app.ui.opportunities.OpportunitiesScreen(state, onType = { selectedType = it }) }
        }
        compose.onNodeWithTag("catalog_remove_type").performClick()
        assertEquals(0, selectedType)
    }
}
