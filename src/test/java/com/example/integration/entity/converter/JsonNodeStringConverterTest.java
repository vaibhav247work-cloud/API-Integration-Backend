package com.example.integration.entity.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonNodeStringConverterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final JsonNodeStringConverter converter = new JsonNodeStringConverter();

    @Test
    void shouldSerializeJsonNodeToString() throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree("""
                {"type":"TOKEN_API","headers":{"Content-Type":"application/json"}}
                """);

        String value = converter.convertToDatabaseColumn(node);

        assertEquals("{\"type\":\"TOKEN_API\",\"headers\":{\"Content-Type\":\"application/json\"}}", value);
    }

    @Test
    void shouldDeserializeStringToJsonNode() throws Exception {
        JsonNode expected = OBJECT_MAPPER.readTree("""
                {"queryParams":{"page":"${page}","businessDate":"${requestDate}"}}
                """);

        JsonNode actual = converter.convertToEntityAttribute("""
                {"queryParams":{"page":"${page}","businessDate":"${requestDate}"}}
                """);

        assertEquals(expected, actual);
    }

    @Test
    void shouldReturnNullForBlankDatabaseValue() {
        assertNull(converter.convertToEntityAttribute("   "));
    }
}
