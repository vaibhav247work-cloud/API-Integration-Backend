package com.example.integration.model.config;

import com.example.integration.model.enums.StorageType;
import lombok.Data;

import java.util.Map;

@Data
public class StorageConfig {
    private StorageType type = StorageType.LOCAL;
    private String tenantId;

    // LOCAL
    private String localDirectory;

    // S3
    private String bucket;
    private String region;
    private String keyPrefix;

    // FTP
    private String host;
    private Integer port = 21;
    private String username;
    private String password;
    private String remoteDirectory;
    private Boolean passiveMode = true;

    // HTTP_API
    private String uploadUrl;
    private String uploadMethod = "POST";
    private String uploadFileParam = "file";
    private Map<String, String> uploadHeaders;
    private Map<String, String> uploadFormFields;
    /** Optional. When set, the response body must contain this string for the upload to be considered successful. */
    private String uploadSuccessText;
}
