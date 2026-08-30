package com.example.integration.model.config;

import com.example.integration.model.enums.DuplicateFieldAction;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class DuplicateHandlingConfig {
    private boolean enabled;
    private List<String> keyHeaders = List.of();
    private DuplicateFieldAction defaultAction = DuplicateFieldAction.KEEP_FIRST;
    private Map<String, DuplicateFieldAction> fieldActions = new LinkedHashMap<>();
}
