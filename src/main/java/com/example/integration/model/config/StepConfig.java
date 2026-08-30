package com.example.integration.model.config;

import com.example.integration.model.enums.PathType;
import com.example.integration.model.enums.PayloadFormat;
import com.example.integration.model.enums.RequestWindowMode;
import lombok.Data;

import java.util.Map;

@Data
public class StepConfig {
    private Integer orderIndex = 1;
    private String name;
    private Boolean enabled = true;
    private String method = "GET";
    private String url;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
    private String bodyTemplate;
    private PayloadFormat requestFormat = PayloadFormat.JSON;
    private PayloadFormat responseFormat = PayloadFormat.JSON;
    private RequestWindowMode requestWindowMode = RequestWindowMode.NONE;
    private String requestDateVariable = "requestDate";
    private String requestDateFormat;
    private Boolean paginate = false;
    private Boolean dataStep = true;
    private String responseAlias;
    private Map<String, String> responseVariables;
    private PathType responseVariablePathType;
}
