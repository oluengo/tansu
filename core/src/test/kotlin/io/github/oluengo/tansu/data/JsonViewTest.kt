package io.github.oluengo.tansu.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonViewTest {
    private fun j(s: String) = Json.parseToJsonElement(s)

    @Test fun nombres() {
        assertEquals("Rest seconds", JsonView.human("restSeconds"))
        assertEquals("Zap amount", JsonView.human("zap_amount"))
        assertEquals("Ui", JsonView.human("ui"))
    }

    @Test fun fechas() {
        assertEquals(1762540200000, JsonView.dateMillis(JsonPrimitive(1762540200), "lastSync"))
        assertEquals(1762540200123, JsonView.dateMillis(JsonPrimitive(1762540200123), "updatedAt"))
        assertNull(JsonView.dateMillis(JsonPrimitive(1762540200), "zapAmount"))
        assertEquals(1794083400000, JsonView.dateMillis(JsonPrimitive("2026-11-07T20:30:00Z"), "exported"))
        assertEquals(1794009600000, JsonView.dateMillis(JsonPrimitive("2026-11-07"), null))
        assertNull(JsonView.dateMillis(JsonPrimitive("hola"), "date"))
    }

    @Test fun formas() {
        val fav = j("""[{"name":"Trabajo","children":[{"name":"Lo-fi","items":[]}]},{"name":"Japón","items":[{"name":"J-Pop"}]}]""") as JsonArray
        assertTrue(JsonView.isTree(fav))
        val ses = j("""[{"date":"2026-01-01","sets":9,"done":true},{"date":"2026-01-02","sets":8,"minutes":30}]""") as JsonArray
        assertTrue(JsonView.isTable(ses))
        assertFalse(JsonView.isTree(ses))
        assertEquals(listOf("date", "sets", "done", "minutes"), JsonView.columns(ses))
        assertTrue(JsonView.isChips(j("""["Paseo","Movilidad"]""") as JsonArray))
        assertFalse(JsonView.isTable(j("""[{"a":{"b":1}}]""") as JsonArray))
        assertEquals("Trabajo", JsonView.titleOf(fav[0] as JsonObject))
        assertEquals(true, JsonView.bool(JsonPrimitive(true)))
        assertNull(JsonView.bool(JsonPrimitive("true")))
    }
}
