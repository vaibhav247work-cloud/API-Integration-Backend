package com.example.integration.model.config;

import com.example.integration.model.enums.PathType;
import lombok.Data;

@Data
public class ResponseConfig {
    private String recordPath;
    private PathType recordPathType;
    private boolean filterByWindow;
    private String recordDatePath;
    private PathType recordDatePathType;
    private String recordDateFormat;
    private DuplicateHandlingConfig duplicateHandling;
}
