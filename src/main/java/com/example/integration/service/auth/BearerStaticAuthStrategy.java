package com.example.integration.service.auth;

import com.example.integration.model.config.AuthConfig;
import com.example.integration.model.enums.AuthType;
import com.example.integration.model.runtime.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class BearerStaticAuthStrategy implements AuthStrategy {

    @Override
    public AuthType getType() {
        return AuthType.BEARER_STATIC;
    }

    @Override
    public void apply(AuthConfig authConfig, ExecutionContext context) {
        context.putAuthHeader("Authorization", authConfig.getTokenPrefix() + authConfig.getHeaderValue());
    }
}
