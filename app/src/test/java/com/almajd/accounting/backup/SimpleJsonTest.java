package com.almajd.accounting.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class SimpleJsonTest {

    @Test
    public void writeThenReadRoundTrip() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("app_id", "com.almajd.accounting");
        values.put("schema_version", 2);
        values.put("customers", 3L);
        values.put("enabled", Boolean.TRUE);
        values.put("missing", null);

        String json = SimpleJson.write(values);
        Map<String, Object> parsed = SimpleJson.read(json);

        assertEquals("com.almajd.accounting", parsed.get("app_id"));
        assertEquals(2.0, ((Number) parsed.get("schema_version")).doubleValue(), 0.0);
        assertEquals(3L, ((Number) parsed.get("customers")).longValue());
        assertEquals(Boolean.TRUE, parsed.get("enabled"));
        assertTrue(parsed.containsKey("missing"));
        assertNull(parsed.get("missing"));
    }

    @Test
    public void escapesSpecialCharactersAndArabic() throws Exception {
        String arabic = "\u0627\u0644\u0645\u062C\u062F"; // المجد
        String tricky = "line1\nline2 \"quoted\" \\ back";
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", arabic);
        values.put("tricky", tricky);

        Map<String, Object> parsed = SimpleJson.read(SimpleJson.write(values));
        assertEquals(arabic, parsed.get("name"));
        assertEquals(tricky, parsed.get("tricky"));
    }

    @Test
    public void parsesWhitespaceAndNumbers() throws Exception {
        Map<String, Object> parsed = SimpleJson.read(
                "  {\n  \"a\" : 12.5 ,\n  \"b\" : -3 ,\n  \"c\" : true\n }  ");
        assertEquals(12.5, ((Number) parsed.get("a")).doubleValue(), 0.0);
        assertEquals(-3L, ((Number) parsed.get("b")).longValue());
        assertEquals(Boolean.TRUE, parsed.get("c"));
    }

    @Test
    public void rejectsNestedObjects() {
        assertThrows(JsonException.class,
                () -> SimpleJson.read("{\"counts\":{\"a\":1}}"));
    }

    @Test
    public void rejectsArrays() {
        assertThrows(JsonException.class,
                () -> SimpleJson.read("{\"items\":[1,2,3]}"));
    }

    @Test
    public void rejectsTrailingContent() {
        assertThrows(JsonException.class,
                () -> SimpleJson.read("{\"a\":1} garbage"));
    }

    @Test
    public void rejectsMalformedDocuments() {
        assertThrows(JsonException.class, () -> SimpleJson.read("not json"));
        assertThrows(JsonException.class, () -> SimpleJson.read("{\"a\":}"));
        assertThrows(JsonException.class, () -> SimpleJson.read("{\"a\":1"));
        assertThrows(JsonException.class, () -> SimpleJson.read(null));
    }
}
