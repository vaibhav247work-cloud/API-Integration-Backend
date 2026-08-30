package com.example.integration.service.auth;

import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.config.AuthConfig;
import com.example.integration.model.enums.AuthType;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.PathType;
import com.example.integration.model.enums.PayloadFormat;
import com.example.integration.model.runtime.ExecutionContext;
import com.example.integration.service.PathExtractorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class OAuth2ClientCredentialsAuthStrategy implements AuthStrategy {

    private final WebClient.Builder webClientBuilder;
    private final PathExtractorService pathExtractorService;

    @Override
    public AuthType getType() {
        return AuthType.OAUTH2_CLIENT_CREDENTIALS;
    }

    @Override
    public void apply(AuthConfig authConfig, ExecutionContext context) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", authConfig.getClientId());
        form.add("client_secret", authConfig.getClientSecret());
        if (StringUtils.hasText(authConfig.getScope())) {
            form.add("scope", authConfig.getScope());
        }
        if (StringUtils.hasText(authConfig.getAudience())) {
            form.add("audience", authConfig.getAudience());
        }

        String responseBody = webClientBuilder.build()
                .post()
                .uri(authConfig.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        String tokenPath = StringUtils.hasText(authConfig.getTokenPath()) ? authConfig.getTokenPath() : "$.access_token";
        String token = pathExtractorService.extractString(
                responseBody,
                PayloadFormat.JSON,
                tokenPath,
                authConfig.getTokenPathType() == null ? PathType.JSON_PATH : authConfig.getTokenPathType());

        if (!StringUtils.hasText(token)) {
            throw new IntegrationFailureException(
                    FailureCategory.AUTHENTICATION_ERROR,
                    "OAuth2 client credentials flow did not return access_token",
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
