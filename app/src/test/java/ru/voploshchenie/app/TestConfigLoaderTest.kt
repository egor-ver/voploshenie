package ru.voploshchenie.app

import ru.voploshchenie.app.data.local.asset.ConfigLoader
import ru.voploshchenie.app.domain.model.TestConfig
import ru.voploshchenie.app.domain.model.ConfigRanking
import ru.voploshchenie.app.domain.model.ConfigQuestion
import ru.voploshchenie.app.domain.model.ConfigScale
import ru.voploshchenie.app.domain.scoring.ConfigValidator
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class F04ConfigTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val config by lazy { ConfigLoader.load(context) }

    private fun changedQuestion(id: String, update: (ConfigQuestion) -> ConfigQuestion): TestConfig = config.copy(
        parts = config.parts.map { part -> part.copy(questions = part.questions.map { if (it.id == id) update(it) else it }) },
    )

    @Test fun assetLoadsAndValidates() {
        assertEquals(emptyList<String>(), ConfigValidator.validate(config))
        assertEquals(15, config.parts.flatMap { it.questions }.count { it.id.startsWith("INT") })
    }

    @Test fun loaderPreservesOptionsAndInputParameters() {
        val questions = config.parts.flatMap { it.questions }
        assertEquals("a", questions.first { it.id == "NAD01" }.options.first().id)
        assertEquals("до 40 тыс. ₽", questions.first { it.id == "NAD01" }.options.first().text)
        assertEquals(listOf("а", "б", "в", "г"), questions.first { it.id == "TSK01" }.options.map { it.id })
        assertEquals("1 курс бакалавриата/специалитета", questions.first { it.id == "PRF02" }.options.first().id)
        assertEquals(ConfigRanking(3, 2), questions.first { it.id == "VAL01" }.ranking)
        assertEquals(ConfigScale(1, 5), questions.first { it.id == "EXP01" }.energy)
        val pick3 = config.parts.first { it.id == "needs" }.steps!!.last()
        assertEquals("pick3", pick3.type)
        assertEquals(3, pick3.min)
        assertEquals(3, pick3.max)
        assertEquals(8, pick3.options.size)
        assertEquals("NEED01", pick3.options.first().id)
    }

    @Test fun duplicateQuestionIdIsReported() {
        val profile = config.parts.first { it.id == "profile" }
        val broken = config.copy(parts = config.parts.map { if (it.id == profile.id) it.copy(questions = it.questions + profile.questions.first()) else it })
        assertTrue(ConfigValidator.validate(broken).any { it.contains("ID") })
    }

    @Test fun missingInterestPairIsReported() {
        assertTrue(ConfigValidator.validate(changedQuestion("INT01") { it.copy(type_b = it.type_a) }).any { it.contains("RIASEC") })
    }

    @Test fun eachSphereMustHaveExactlyTwoQuestions() {
        val broken = changedQuestion("SPH01") { it.copy(sphere_id = "finance") }
        assertTrue(ConfigValidator.validate(broken).any { it.contains("business") && it.contains("SPH") })
    }

    @Test fun nonSphLikertQuestionCannotReplaceSphereQuestion() {
        val broken = config.copy(parts = config.parts.map { part -> part.copy(questions = part.questions.map { if (it.id == "SPH08") it.copy(id = "X08") else it }) })
        val errors = ConfigValidator.validate(broken)
        assertTrue(errors.any { it.contains("business") && it.contains("SPH") })
    }

    @Test fun duplicateSphereIdIsReported() {
        val broken = config.copy(spheres = config.spheres + config.spheres.first { it.id == "business" })
        assertTrue(ConfigValidator.validate(broken).any { it.contains("business") && it.contains("ID") })
    }

    @Test fun sphereQuestionIdsMustMatchSphereMembership() {
        val broken = config.copy(spheres = config.spheres.map { if (it.id == "business") it.copy(questions = listOf("SPH02", "SPH08")) else it })
        assertTrue(ConfigValidator.validate(broken).any { it.contains("SPH") })
    }

    @Test fun profileReferencesRequireExistingValuesNeedsTypesAndDomains() {
        val first = config.spheres.first()
        val broken = config.copy(spheres = config.spheres.map { if (it.id == first.id) it.copy(values = listOf("VAL99"), needs = listOf("NEED99"), riasec = listOf("X"), domains = listOf("unknown")) else it })
        val errors = ConfigValidator.validate(broken)
        assertTrue(errors.any { it.contains("VAL") })
        assertTrue(errors.any { it.contains("NEED") })
        assertTrue(errors.any { it.contains("RIASEC") })
        assertTrue(errors.any { it.contains("домен") })
    }

    @Test fun profileValueReferenceMustUseRankValues() {
        val broken = changedQuestion("VAL01") { it.copy(type = "text") }
        assertTrue(ConfigValidator.validate(broken).any { it.contains("VAL") })
    }

    @Test fun profileNeedReferenceMustUseLikert5() {
        val broken = changedQuestion("NEED01") { it.copy(type = "text") }
        assertTrue(ConfigValidator.validate(broken).any { it.contains("NEED") })
    }

    @Test fun taskKeyMustBeAnOptionId() {
        val broken = changedQuestion("TSK01") { it.copy(correct = "ё") }
        assertTrue(ConfigValidator.validate(broken).any { it.contains("TSK01") })
    }

    @Test fun checkTypesMustMatchLinkedInterestPair() {
        val broken = changedQuestion("CHK01") { it.copy(type_b = "A") }
        assertTrue(ConfigValidator.validate(broken).any { it.contains("CHK01") && it.contains("INT") })
    }
}
