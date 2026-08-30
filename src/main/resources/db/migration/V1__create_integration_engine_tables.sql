CREATE TABLE integration_definition (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    client_name VARCHAR(255) NOT NULL,
    base_url VARCHAR(1000) NULL,
    enabled BIT NOT NULL DEFAULT b'1',
    schedule_cron VARCHAR(120) NULL,
    csv_file_name VARCHAR(255) NOT NULL,
    output_directory VARCHAR(500) NULL,
    max_retries INT NOT NULL DEFAULT 0,
    auth_config JSON NULL,
    request_config JSON NULL,
    response_config JSON NULL,
    pagination_config JSON NULL,
    storage_config JSON NULL,
    step_config JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE integration_field_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    integration_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    source_path VARCHAR(2000) NOT NULL,
    path_type VARCHAR(40) NOT NULL,
    target_header VARCHAR(255) NOT NULL,
    default_value VARCHAR(1000) NULL,
    formatter VARCHAR(100) NULL,
    required_flag BIT NOT NULL DEFAULT b'0',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_mapping_integration
        FOREIGN KEY (integration_id) REFERENCES integration_definition (id)
        ON DELETE CASCADE
);

CREATE TABLE integration_run (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    integration_id BIGINT NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL,
    attempt_number INT NOT NULL DEFAULT 0,
    output_location VARCHAR(1000) NULL,
    records_processed BIGINT NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_run_integration
        FOREIGN KEY (integration_id) REFERENCES integration_definition (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_run_integration_started
    ON integration_run (integration_id, started_at DESC);

CREATE TABLE failed_job_queue (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    integration_id BIGINT NOT NULL,
    run_id BIGINT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP NOT NULL,
    last_error TEXT NULL,
    active BIT NOT NULL DEFAULT b'1',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_failure_integration
        FOREIGN KEY (integration_id) REFERENCES integration_definition (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_failure_run
        FOREIGN KEY (run_id) REFERENCES integration_run (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_failure_retry
    ON failed_job_queue (active, next_retry_at);
