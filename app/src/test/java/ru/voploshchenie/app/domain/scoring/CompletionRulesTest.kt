package ru.voploshchenie.app.domain.scoring

import ru.voploshchenie.app.domain.scoring.CompletionRules
import ru.voploshchenie.app.domain.scoring.isScreenComplete
import ru.voploshchenie.app.domain.scoring.missingForCompletion
import ru.voploshchenie.app.domain.scoring.TestAnswers
import ru.voploshchenie.app.domain.scoring.ExperienceAnswer
import ru.voploshchenie.app.domain.scoring.NeedsAnswers
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionRulesTest {
    @Test fun requiredProfileAndLimits() {
        val ready = TestAnswers(profile = mapOf(
            "PRF03" to JsonPrimitive("Информатика"), "PRF04" to JsonPrimitive("Казань"),
            "PRF02" to JsonPrimitive("3"), "PRF05" to JsonPrimitive("6"), "PRF06" to JsonPrimitive("сменить направление"),
        ))
        assertTrue(CompletionRules.isScreenComplete("profile", ready))
        assertFalse(CompletionRules.isScreenComplete("profile", ready.copy(profile = ready.profile + ("PRF03" to JsonPrimitive("x".repeat(121))))))
        assertFalse(CompletionRules.isScreenComplete("profile", ready.copy(profile = ready.profile - "PRF03")))
        assertFalse(CompletionRules.isScreenComplete("profile", ready.copy(profile = ready.profile + ("PRF04" to JsonPrimitive("а".repeat(121))))))
        assertTrue(CompletionRules.isScreenComplete("profile", ready.copy(profile = ready.profile + ("PRF01" to JsonPrimitive("И".repeat(60))))))
        assertFalse(CompletionRules.isScreenComplete("profile", ready.copy(profile = ready.profile + ("PRF01" to JsonPrimitive("И".repeat(61))))))
        assertFalse(CompletionRules.isScreenComplete("profile", ready.copy(profile = ready.profile + ("PRF06" to JsonPrimitive("другое")))))
        assertFalse(CompletionRules.isScreenComplete("profile", ready.copy(profile = ready.profile + mapOf("PRF06" to JsonPrimitive("другое"), "PRF06_other" to JsonPrimitive("д".repeat(201))))))
    }

    @Test fun valuesRequireExactlyThreeAndTwoDisjointEntries() {
        val three = listOf("VAL01", "VAL02", "VAL03")
        assertTrue(CompletionRules.isScreenComplete("values", TestAnswers(valuesTop3 = three, valuesLeast2 = listOf("VAL04", "VAL05"))))
        assertFalse(CompletionRules.isScreenComplete("values", TestAnswers(valuesTop3 = three + "VAL04", valuesLeast2 = listOf("VAL05", "VAL06"))))
        assertFalse(CompletionRules.isScreenComplete("values", TestAnswers(valuesTop3 = three, valuesLeast2 = listOf("VAL04"))))
        assertFalse(CompletionRules.isScreenComplete("values", TestAnswers(valuesTop3 = three, valuesLeast2 = listOf("VAL03", "VAL04"))))
    }

    @Test fun needsRequireEightRatingsAndThreeDistinctImportantConditions() {
        val ratings = (1..8).associate { "NEED%02d".format(it) to 3 }
        assertTrue(CompletionRules.isScreenComplete("needs", TestAnswers(needs = NeedsAnswers(ratings, listOf("NEED01", "NEED02", "NEED03")))))
        assertFalse(CompletionRules.isScreenComplete("needs", TestAnswers(needs = NeedsAnswers(ratings - "NEED08", listOf("NEED01", "NEED02", "NEED03")))))
        assertFalse(CompletionRules.isScreenComplete("needs", TestAnswers(needs = NeedsAnswers(ratings, listOf("NEED01", "NEED01", "NEED03")))))
        assertFalse(CompletionRules.isScreenComplete("needs", TestAnswers(needs = NeedsAnswers(ratings, listOf("NEED01", "NEED02", "NEED03", "NEED04")))))
    }

    @Test fun energyIsRequiredOnlyForNonblankExperienceTextAndOpenTextHasLimit() {
        assertTrue(CompletionRules.isScreenComplete("experience", TestAnswers(experience = mapOf("EXP01" to ExperienceAnswer("", 0)))))
        assertFalse(CompletionRules.isScreenComplete("experience", TestAnswers(experience = mapOf("EXP01" to ExperienceAnswer("Кейс", 0)))))
        assertTrue(CompletionRules.isScreenComplete("experience", TestAnswers(experience = mapOf("EXP01" to ExperienceAnswer("Кейс", 5)))))
        assertFalse(CompletionRules.isScreenComplete("open", TestAnswers(open = mapOf("OPEN01" to "я".repeat(1001)))))
        assertEquals(listOf("PRF03"), CompletionRules.missingForCompletion(TestAnswers()).take(1))
        assertFalse(CompletionRules.isScreenComplete("experience", TestAnswers(experience = mapOf("EXP01" to ExperienceAnswer("x".repeat(1001), 5)))))
    }

    @Test fun requiredAnswerScreensRejectMissingOrBlankAnswers() {
        val pairs = (1..15).associate { "INT%02d".format(it) to "A" }
        assertTrue(CompletionRules.isScreenComplete("interests", TestAnswers(pairs = pairs)))
        assertFalse(CompletionRules.isScreenComplete("interests", TestAnswers(pairs = pairs - "INT01")))
        assertFalse(CompletionRules.isScreenComplete("interests", TestAnswers(pairs = pairs + ("INT01" to ""))))
        assertFalse(CompletionRules.isScreenComplete("sphere_interest", TestAnswers(interest = mapOf("SPH01" to 3))))
        assertTrue(CompletionRules.isScreenComplete("sphere_interest", TestAnswers(interest = (1..14).associate { "SPH%02d".format(it) to 3 })))
        val tasks = (1..10).associate { "TSK%02d".format(it) to "a" }
        assertTrue(CompletionRules.isScreenComplete("tasks", TestAnswers(tasks = tasks)))
        assertFalse(CompletionRules.isScreenComplete("tasks", TestAnswers(tasks = tasks - "TSK01")))
        assertFalse(CompletionRules.isScreenComplete("tasks", TestAnswers(tasks = tasks + ("TSK01" to ""))))
        assertFalse(CompletionRules.isScreenComplete("nad", TestAnswers(nad = mapOf("NAD01" to "a"))))
        val checks = (1..5).associate { "CHK%02d".format(it) to "A" }
        assertTrue(CompletionRules.isScreenComplete("checks", TestAnswers(checks = checks)))
        assertFalse(CompletionRules.isScreenComplete("checks", TestAnswers(checks = checks - "CHK01")))
        assertFalse(CompletionRules.isScreenComplete("checks", TestAnswers(checks = checks + ("CHK01" to ""))))
    }

    @Test fun aggregateCompletionIncludesOpenAndNad07Limits() {
        val openExceeded = TestAnswers(open = mapOf("OPEN01" to "x".repeat(1001)))
        assertTrue(CompletionRules.missingForCompletion(openExceeded).contains("OPEN01"))
        val nadExceeded = TestAnswers(nad = (1..6).associate { "NAD%02d".format(it) to "a" } + ("NAD07" to "x".repeat(1001)))
        assertTrue(CompletionRules.missingForCompletion(nadExceeded).contains("NAD07"))
        val nadAtLimit = nadExceeded.copy(nad = nadExceeded.nad + ("NAD07" to "x".repeat(1000)))
        assertTrue(CompletionRules.isScreenComplete("nad", nadAtLimit))
    }
}

