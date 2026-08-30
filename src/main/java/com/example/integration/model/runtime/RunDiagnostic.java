package com.example.integration.model.runtime;

import com.example.integration.model.enums.FailureCategory;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RunDiagnostic {
    FailureCategory failureCategory;
    String stepName;
    String requestUrl;
    Integer httpStatusCode;
    String responsePreview;
    String message;
    boolean retryable;
}
