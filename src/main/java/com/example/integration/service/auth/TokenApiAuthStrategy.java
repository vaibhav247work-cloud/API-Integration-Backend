package com.example.integration.service.auth;

import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.config.AuthConfig;
import com.example.integration.model.enums.AuthType;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.PayloadFormat;
import com.example.integration.model.runtime.ExecutionContext;
import com.example.integration.service.PathExtractorService;
import com.example.integration.service.RequestTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TokenApiAuthStrategy implements AuthStrategy {

    private final WebClient.Builder webClientBuilder;
    private final RequestTemplateService requestTemplateService;
    private final PathExtractorService pathExtractorService;

    @Override
    public AuthType getType() {
        return AuthType.TOKEN_API;
    }

    @Override
    public void apply(AuthConfig authConfig, ExecutionContext context) {
        HttpMethod method = HttpMethod.valueOf(
                StringUtils.hasText(authConfig.getMethod()) ? authConfig.getMethod().toUpperCase() : "POST");

        Map<String, String> headers = new LinkedHashMap<>(requestTemplateService.resolveMap(authConfig.getHeaders(), context));
        String requestBody = requestTemplateService.resolve(authConfig.getBodyTemplate(), context);
        PayloadFormat requestFormat = authConfig.getRequestFormat() == null ? PayloadFormat.JSON : authConfig.getRequestFormat();

        String responseBody = webClientBuilder.build()
                .method(method)
                .uri(requestTemplateService.resolve(authConfig.getTokenUrl(), context))
                .headers(httpHeaders -> {
                    headers.forEach(httpHeaders::set);
                    if (requestFormat == PayloadFormat.JSON) {
                        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                    } else {
                        httpHeaders.setContentType(MediaType.APPLICATION_XML);
                    }
                })
                .bodyValue(requestBody == null ? "" : requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        String token = pathExtractorService.extractString(
                responseBody,
                authConfig.getResponseFormat(),
                authConfig.getTokenPath(),
                authConfig.getTokenPathType());

        if (!StringUtils.hasText(token)) {
            throw new IntegrationFailureException(
                    FailureCategory.AUTHENTICATION_ERROR,
                    "Token API response did not contain a token",
                    "AUTH",
                    authConfig.getTokenUrl(),
                    null,
                    truncate(responseBody),
                    false);
        }

        context.putVariable("token", token);
        context.putAuthHeader(authConfig.getTokenHeaderName(), authConfig.getTokenPrefix() + token);
    }

    private String truncate(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        return responseBody.length() <= 1000 ? responseBody : responseBody.substring(0, 1000);
    }
}
