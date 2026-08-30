package com.example.integration.service;

import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.config.AuthConfig;
import com.example.integration.model.enums.AuthType;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.runtime.ExecutionContext;
import com.example.integration.service.auth.AuthStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final java.util.List<AuthStrategy> authStrategies;

    public void apply(AuthConfig authConfig, ExecutionContext context) {
        AuthType authType = authConfig == null || authConfig.getType() == null ? AuthType.NONE : authConfig.getType();

        Map<AuthType, AuthStrategy> strategyMap = authStrategies.stream()
                .collect(Collectors.toMap(AuthStrategy::getType, Function.identity()));
        AuthStrategy strategy = strategyMap.get(authType);
        if (strategy == null) {
            throw new IntegrationFailureException(
                    FailureCategory.CONFIGURATION_ERROR,
                    "Unsupported auth strategy: " + authType,
                    "AUTH",
                    null,
                    null,
                    null,
                    false);
        }
        strategy.apply(authConfig == null ? new AuthConfig() : authConfig, context);
    }
}
