package com.example.integration.service.auth;

import com.example.integration.model.config.AuthConfig;
import com.example.integration.model.enums.AuthType;
import com.example.integration.model.runtime.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyQueryAuthStrategy implements AuthStrategy {

    @Override
    public AuthType getType() {
        return AuthType.API_KEY_QUERY;
    }

    @Override
    public void apply(AuthConfig authConfig, ExecutionContext context) {
        context.putAuthQueryParam(authConfig.getQueryParamName(), authConfig.getQueryParamValue());
    }
}
