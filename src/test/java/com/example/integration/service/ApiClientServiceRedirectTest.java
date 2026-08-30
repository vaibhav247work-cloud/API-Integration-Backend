package com.example.integration.service;

import com.example.integration.config.RuntimeConfig;
import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.model.config.StepConfig;
import com.example.integration.model.enums.PayloadFormat;
import com.example.integration.model.runtime.ExecutionContext;
import com.example.integration.model.runtime.ScheduleWindow;
import com.example.integration.model.runtime.StepResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ApiClientServiceRedirectTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void executeFollowsRedirectAndReturnsFinalResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/final.xml");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final.xml", exchange -> respond(exchange, 200, "application/xml",
                "<?xml version=\"1.0\"?><Container><TransactionSegment><Transaction-Object>"
                        + "<RECEIPT_NO>ABC123</RECEIPT_NO>"
                        + "</Transaction-Object></TransactionSegment></Container>"));
        server.start();

        IntegrationDefinition definition = new IntegrationDefinition();
        definition.setClientName("Redirect Client");
        definition.setBaseUrl("http://localhost:" + server.getAddress().getPort());

        StepConfig step = new StepConfig();
        step.setName("fetch-redirected-xml");
        step.setMethod("GET");
        step.setUrl("/redirect");
        step.setRequestFormat(PayloadFormat.XML);
        step.setResponseFormat(PayloadFormat.XML);

        ApiClientService apiClientService = new ApiClientService(
                new RuntimeConfig().webClientBuilder(),
                new RequestTemplateService());

        ExecutionContext context = new ExecutionContext(
                definition.getClientName(),
                "TEST",
                ScheduleWindow.custom(
                        LocalDateTime.of(2026, 3, 18, 17, 56, 10),
                        LocalDateTime.of(2026, 3, 17, 0, 0),
                        LocalDateTime.of(2026, 3, 18, 0, 0),
                        "custom_20260317"));

        StepResponse response = apiClientService.execute(definition, step, context);

        assertThat(response.getHttpStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("<RECEIPT_NO>ABC123</RECEIPT_NO>");
    }

    private static void respond(HttpExchange exchange, int statusCode, String contentType, String body)
            throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }
}
