package com.example.integration.model.config;

import com.example.integration.model.enums.PaginationMode;
import com.example.integration.model.enums.PathType;
import lombok.Data;

@Data
public class PaginationConfig {
    private boolean enabled;
    private PaginationMode mode = PaginationMode.PAGE_NUMBER;
    private Integer startPage = 1;
    private String pageParam = "page";
    private String sizeParam;
    private Integer pageSize;
    private String totalPagesPath;
    private PathType totalPagesPathType;
    private String nextPagePath;
    private PathType nextPagePathType;
}
