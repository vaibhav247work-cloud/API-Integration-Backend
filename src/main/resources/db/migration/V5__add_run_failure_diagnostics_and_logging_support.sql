ALTER TABLE integration_run
    ADD COLUMN failure_category VARCHAR(50) NULL AFTER file_token,
    ADD COLUMN failed_step_name VARCHAR(255) NULL AFTER failure_category,
    ADD COLUMN failed_request_url VARCHAR(1000) NULL AFTER failed_step_name,
    ADD COLUMN http_status_code INT NULL AFTER failed_request_url,
    ADD COLUMN response_preview TEXT NULL AFTER http_status_code;

ALTER TABLE failed_job_queue
    ADD COLUMN failure_category VARCHAR(50) NULL AFTER next_retry_at,
    ADD COLUMN failed_step_name VARCHAR(255) NULL AFTER failure_category,
    ADD COLUMN failed_request_url VARCHAR(1000) NULL AFTER failed_step_name,
    ADD COLUMN http_status_code INT NULL AFTER failed_request_url;
