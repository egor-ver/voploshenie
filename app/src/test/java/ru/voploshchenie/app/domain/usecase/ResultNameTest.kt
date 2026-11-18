package ru.voploshchenie.app.domain.usecase

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import ru.voploshchenie.app.domain.model.StoredTestAnswer
import ru.voploshchenie.app.domain.scoring.TestAnswers

class ResultNameTest {
    private fun profile(value: String) = listOf(StoredTestAnswer("attempt", "profile", value, 0))

    @Test fun currentNameTakesPriorityAndMissingNameRemainsOptional() {
        assertEquals("name", resultName(profile("{"), TestAnswers(profile = mapOf("PRF01" to JsonPrimitive("name")))))
        assertNull(resultName(emptyList(), TestAnswers()))
        assertNull(resultName(profile("{}"), TestAnswers()))
    }

    @Test fun legacyNamePreservesEscapes() {
        val name = "line\nwith \"quotes\" and \\"
        assertEquals(name, resultName(profile("{\"PRF01\":${JsonPrimitive(name)}}"), TestAnswers()))
    }

    @Test(expected = SerializationException::class)
    fun corruptLegacyProfilePropagatesToTheResultErrorState() {
        resultName(profile("{"), TestAnswers())
    }
}
