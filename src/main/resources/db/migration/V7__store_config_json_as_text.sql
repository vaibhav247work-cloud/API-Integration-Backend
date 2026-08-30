ALTER TABLE integration_definition
    MODIFY schedule_config LONGTEXT NULL,
    MODIFY auth_config LONGTEXT NULL,
    MODIFY request_config LONGTEXT NULL,
    MODIFY response_config LONGTEXT NULL,
    MODIFY pagination_config LONGTEXT NULL,
    MODIFY storage_config LONGTEXT NULL,
    MODIFY step_config LONGTEXT NULL;
