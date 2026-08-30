package com.example.integration.service.auth;

import com.example.integration.model.config.AuthConfig;
import com.example.integration.model.enums.AuthType;
import com.example.integration.model.runtime.ExecutionContext;

public interface AuthStrategy {
    AuthType getType();

    void apply(AuthConfig authConfig, ExecutionContext context);
}
