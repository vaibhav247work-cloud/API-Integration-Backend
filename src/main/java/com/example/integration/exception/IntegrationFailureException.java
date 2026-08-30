package com.example.integration.exception;

import com.example.integration.model.enums.FailureCategory;
import lombok.Getter;

@Getter
public class IntegrationFailureException extends RuntimeException {

    private final FailureCategory failureCategory;
    private final String stepName;
    private final String requestUrl;
    private final Integer httpStatusCode;
    private final String responsePreview;
    private final boolean retryable;

    public IntegrationFailureException(
            FailureCategory failureCategory,
            String message,
            String stepName,
            String requestUrl,
            Integer httpStatusCode,
            String responsePreview,
            boolean retryable,
            Throwable cause) {
        super(message, cause);
        this.failureCategory = failureCategory;
        this.stepName = stepName;
        this.requestUrl = requestUrl;
        this.httpStatusCode = httpStatusCode;
        this.responsePreview = responsePreview;
        this.retryable = retryable;
    }

    public IntegrationFailureException(
            FailureCategory failureCategory,
            String message,
            String stepName,
            String requestUrl,
            Integer httpStatusCode,
            String responsePreview,
            boolean retryable) {
        this(failureCategory, message, stepName, requestUrl, httpStatusCode, responsePreview, retryable, null);
    }
}
