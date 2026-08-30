package com.example.integration.model.runtime;

import com.example.integration.model.enums.PayloadFormat;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StepResponse {
    String stepName;
    String url;
    String body;
    PayloadFormat format;
    Integer httpStatusCode;
}
