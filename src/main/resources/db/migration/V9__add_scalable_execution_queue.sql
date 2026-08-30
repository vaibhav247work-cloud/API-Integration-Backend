ALTER TABLE integration_definition
    ADD COLUMN scheduler_mode VARCHAR(20) NOT NULL DEFAULT 'LEGACY' AFTER step_config;

ALTER TABLE integration_run
    ADD COLUMN execution_job_id BIGINT NULL AFTER file_token;

CREATE INDEX idx_integration_run_execution_job
    ON integration_run (execution_job_id, started_at DESC);

CREATE TABLE execution_job (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    integration_id BIGINT NOT NULL,
    dedupe_key VARCHAR(255) NOT NULL,
    schedule_type VARCHAR(40) NOT NULL,
    planned_fire_at TIMESTAMP NOT NULL,
    window_start TIMESTAMP NULL,
    window_end TIMESTAMP NULL,
    file_token VARCHAR(100) NULL,
    status VARCHAR(40) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    worker_id VARCHAR(120) NULL,
    lease_until TIMESTAMP NULL,
    claimed_at TIMESTAMP NULL,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    last_error TEXT NULL,
    last_failure_category VARCHAR(50) NULL,
    last_failed_step_name VARCHAR(255) NULL,
    last_http_status INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_execution_job_dedupe UNIQUE (dedupe_key),
    CONSTRAINT fk_execution_job_integration
        FOREIGN KEY (integration_id) REFERENCES integration_definition (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_execution_job_due
    ON execution_job (status, next_attempt_at, planned_fire_at);

CREATE INDEX idx_execution_job_lease
    ON execution_job (status, lease_until);

CREATE INDEX idx_execution_job_integration
    ON execution_job (integration_id, planned_fire_at DESC);
