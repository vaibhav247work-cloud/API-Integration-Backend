package com.example.integration.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectMapperConfigTest {

    @Test
    void objectMapperSerializesLocalDateTimeAsIsoString() throws Exception {
        ObjectMapper objectMapper = new ObjectMapperConfig().objectMapper();
        LocalDateTime timestamp = LocalDateTime.of(2026, 3, 18, 15, 37, 9);

        String json = objectMapper.writeValueAsString(new Payload(timestamp));

        assertThat(json).isEqualTo("{\"createdAt\":\"2026-03-18T15:37:09\"}");
    }

    private record Payload(LocalDateTime createdAt) {
    }
}
