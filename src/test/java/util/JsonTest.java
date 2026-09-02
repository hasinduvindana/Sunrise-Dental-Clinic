package util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the hand-written JSON reader and writer. */
class JsonTest {

    @Test
    @DisplayName("an object round-trips through write() and parse()")
    @SuppressWarnings("unchecked")
    void roundTrip() {
        Map<String, Object> source = Json.obj();
        source.put("fullName", "Nimal Perera");
        source.put("queueNo", 7);
        source.put("vip", true);
        source.put("notes", null);

        Map<String, Object> parsed = Json.parseObject(Json.write(source));
        assertEquals("Nimal Perera", parsed.get("fullName"));
        assertEquals(7.0, ((Number) parsed.get("queueNo")).doubleValue());
        assertEquals(Boolean.TRUE, parsed.get("vip"));
        assertNull(parsed.get("notes"));
    }

    @Test
    @DisplayName("quotes and newlines in user input are escaped, not injected")
    void escaping() {
        Map<String, Object> source = Json.obj();
        source.put("notes", "Patient said \"it hurts\"\nsince Monday");
        String json = Json.write(source);
        assertTrue(json.contains("\\\""));
        assertTrue(json.contains("\\n"));
        assertEquals("Patient said \"it hurts\"\nsince Monday", Json.parseObject(json).get("notes"));
    }

    @Test
    @DisplayName("nested arrays of objects parse into lists of maps")
    @SuppressWarnings("unchecked")
    void nestedArrays() {
        String json = "{\"items\":[{\"drugName\":\"Amoxicillin\",\"durationDays\":5},"
                    + "{\"drugName\":\"Ibuprofen\",\"durationDays\":3}]}";
        Map<String, Object> parsed = Json.parseObject(json);
        List<Object> items = (List<Object>) parsed.get("items");
        assertEquals(2, items.size());
        assertEquals("Amoxicillin", ((Map<String, Object>) items.get(0)).get("drugName"));
    }

    @Test
    @DisplayName("malformed JSON is rejected rather than silently accepted")
    void malformedInput() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": }"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": 1"));
    }

    @Test
    @DisplayName("an empty body parses to an empty object instead of throwing")
    void emptyBody() {
        assertTrue(Json.parseObject("").isEmpty());
    }
}
