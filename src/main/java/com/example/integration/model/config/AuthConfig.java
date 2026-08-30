package com.example.integration.model.config;

import com.example.integration.model.enums.AuthType;
import com.example.integration.model.enums.PathType;
import com.example.integration.model.enums.PayloadFormat;
import lombok.Data;

import java.util.Map;

@Data
public class AuthConfig {
    private AuthType type = AuthType.NONE;
    private String method;
    private String tokenUrl;
    private String tokenPath;
    private PathType tokenPathType;
    private PayloadFormat requestFormat = PayloadFormat.JSON;
    private PayloadFormat responseFormat = PayloadFormat.JSON;
    private Map<String, String> headers;
    private String bodyTemplate;
    private String username;
    private String password;
    private String headerName;
    private String headerValue;
    private String queryParamName;
    private String queryParamValue;
    private String tokenHeaderName = "Authorization";
    private String tokenPrefix = "Bearer ";
    private String clientId;
    private String clientSecret;
    private String scope;
    private String audience;
}
