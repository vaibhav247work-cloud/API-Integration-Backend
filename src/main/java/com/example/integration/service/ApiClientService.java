package com.example.integration.service;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.config.StepConfig;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.PayloadFormat;
import com.example.integration.model.runtime.ExecutionContext;
import com.example.integration.model.runtime.StepResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiClientService {

    private final WebClient.Builder webClientBuilder;
    private final RequestTemplateService requestTemplateService;

    public StepResponse execute(IntegrationDefinition definition, StepConfig step, ExecutionContext context) {
        String rawUrl = StringUtils.hasText(step.getUrl()) ? step.getUrl() : definition.getBaseUrl();
        String url = requestTemplateService.resolve(resolveUrl(definition.getBaseUrl(), rawUrl), context);
        Map<String, String> headers = new LinkedHashMap<>(requestTemplateService.resolveMap(step.getHeaders(), context));
        headers.putAll(context.getAuthHeaders());

        Map<String, String> queryParams = new LinkedHashMap<>(requestTemplateService.resolveMap(step.getQueryParams(), context));
        queryParams.putAll(context.getAuthQueryParams());

        String body = requestTemplateService.resolve(step.getBodyTemplate(), context);
        HttpMethod method = HttpMethod.valueOf(step.getMethod().toUpperCase());
        PayloadFormat requestFormat = step.getRequestFormat() == null ? PayloadFormat.JSON : step.getRequestFormat();
        PayloadFormat responseFormat = step.getResponseFormat() == null ? PayloadFormat.JSON : step.getResponseFormat();

        URI finalUri = buildUri(url, queryParams);
        log.info("Calling client API client={} step={} method={} url={}",
                definition.getClientName(), step.getName(), method.name(), finalUri);

        WebClient.RequestBodySpec requestSpec = webClientBuilder.build()
                .method(method)
                .uri(finalUri)
                .headers(httpHeaders -> applyHeaders(httpHeaders, headers, requestFormat, responseFormat));

        WebClient.RequestHeadersSpec<?> headersSpec = supportsBody(method) && StringUtils.hasText(body)
                ? requestSpec.bodyValue(body)
                : requestSpec;

        try {
            StepResponse response = headersSpec.exchangeToMono(clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(responseBody -> {
                                        int statusCode = clientResponse.statusCode().value();
                                        if (clientResponse.statusCode().isError()) {
                                            return Mono.error(buildHttpFailure(step, finalUri, statusCode, responseBody));
                                        }
                                        return Mono.just(StepResponse.builder()
                                                .stepName(step.getName())
                                                .url(finalUri.toString())
                                                .body(responseBody)
                                                .format(responseFormat)
                                                .httpStatusCode(statusCode)
                                                .build());
                                    }))
                    .block();

            log.info("Client API completed client={} step={} status={} url={} responseChars={}",
                    definition.getClientName(),
                    step.getName(),
                    response.getHttpStatusCode(),
                    response.getUrl(),
                    response.getBody() == null ? 0 : response.getBody().length());
            if (!StringUtils.hasText(response.getBody())) {
                log.warn("Client API returned empty body client={} step={} status={} url={}",
                        definition.getClientName(), step.getName(), response.getHttpStatusCode(), response.getUrl());
            }
            return response;
        } catch (WebClientRequestException ex) {
            throw new IntegrationFailureException(
                    FailureCategory.NETWORK_ERROR,
                    "Network error while calling client API: " + ex.getMessage(),
                    step.getName(),
                    finalUri.toString(),
                    null,
                    null,
                    true,
                    ex);
        }
    }

    private URI buildUri(String url, Map<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        queryParams.forEach(builder::queryParam);
        return builder.build(true).toUri();
    }

    private void applyHeaders(
            HttpHeaders headers,
            Map<String, String> configuredHeaders,
            PayloadFormat requestFormat,
            PayloadFormat responseFormat) {

        headers.setContentType(contentTypeFor(requestFormat));
        headers.setAccept(java.util.List.of(contentTypeFor(responseFormat)));
        configuredHeaders.forEach(headers::set);
    }

    private MediaType contentTypeFor(PayloadFormat format) {
        if (format == PayloadFormat.XML) {
            return MediaType.APPLICATION_XML;
        }
        if (format == PayloadFormat.SOAP) {
            return MediaType.TEXT_XML;
        }
        return MediaType.APPLICATION_JSON;
    }

    private boolean supportsBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    private String resolveUrl(String baseUrl, String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return baseUrl;
        }
        if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            return candidate;
        }
        if (!StringUtils.hasText(baseUrl)) {
            return candidate;
        }
        if (baseUrl.endsWith("/") && candidate.startsWith("/")) {
            return baseUrl + candidate.substring(1);
        }
        if (!baseUrl.endsWith("/") && !candidate.startsWith("/")) {
            return baseUrl + "/" + candidate;
        }
        return baseUrl + candidate;
    }

    private IntegrationFailureException buildHttpFailure(
            StepConfig step,
            URI finalUri,
            int statusCode,
            String responseBody) {
        FailureCategory category = (statusCode == 401 || statusCode == 403)
                ? FailureCategory.AUTHENTICATION_ERROR
                : FailureCategory.HTTP_ERROR;
        boolean retryable = statusCode >= 500 || statusCode == 429;
        return new IntegrationFailureException(
                category,
                "Client API returned HTTP " + statusCode,
                step.getName(),
                finalUri.toString(),
                statusCode,
                truncate(responseBody),
                retryable);
    }

    private String truncate(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        return body.length() <= 1000 ? body : body.substring(0, 1000);
    }
}
