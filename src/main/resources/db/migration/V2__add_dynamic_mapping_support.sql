ALTER TABLE integration_field_mapping
    MODIFY source_path VARCHAR(2000) NULL,
    MODIFY path_type VARCHAR(40) NULL,
    ADD COLUMN mapping_type VARCHAR(40) NOT NULL DEFAULT 'SOURCE_PATH' AFTER sort_order,
    ADD COLUMN expression VARCHAR(4000) NULL AFTER target_header;
