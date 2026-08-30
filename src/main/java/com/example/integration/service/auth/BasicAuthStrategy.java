package com.example.integration.service.auth;

import com.example.integration.model.config.AuthConfig;
import com.example.integration.model.enums.AuthType;
import com.example.integration.model.runtime.ExecutionContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class BasicAuthStrategy implements AuthStrategy {

    @Override
    public AuthType getType() {
        return AuthType.BASIC;
    }

    @Override
    public void apply(AuthConfig authConfig, ExecutionContext context) {
        String credentials = authConfig.getUsername() + ":" + authConfig.getPassword();
        String token = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        context.putAuthHeader("Authorization", "Basic " + token);
    }
}
