package com.example.integration.service.auth;

import com.example.integration.model.config.AuthConfig;
import com.example.integration.model.enums.AuthType;
import com.example.integration.model.runtime.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyHeaderAuthStrategy implements AuthStrategy {

    @Override
    public AuthType getType() {
        return AuthType.API_KEY_HEADER;
    }

    @Override
    public void apply(AuthConfig authConfig, ExecutionContext context) {
        context.putAuthHeader(authConfig.getHeaderName(), authConfig.getHeaderValue());
    }
}
