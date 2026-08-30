DELETE FROM failed_job_queue
WHERE integration_id IN (
    SELECT seeded.id
    FROM (
        SELECT id
        FROM integration_definition
        WHERE client_name LIKE 'Seed Demo - %'
           OR base_url LIKE 'https://dummy-%'
    ) seeded
)
   OR run_id IN (
    SELECT seeded_runs.id
    FROM (
        SELECT r.id
        FROM integration_run r
        JOIN integration_definition d ON d.id = r.integration_id
        WHERE d.client_name LIKE 'Seed Demo - %'
           OR d.base_url LIKE 'https://dummy-%'
    ) seeded_runs
);

DELETE FROM execution_job
WHERE integration_id IN (
    SELECT seeded.id
    FROM (
        SELECT id
        FROM integration_definition
        WHERE client_name LIKE 'Seed Demo - %'
           OR base_url LIKE 'https://dummy-%'
    ) seeded
);

DELETE FROM integration_field_mapping
WHERE integration_id IN (
    SELECT seeded.id
    FROM (
        SELECT id
        FROM integration_definition
        WHERE client_name LIKE 'Seed Demo - %'
           OR base_url LIKE 'https://dummy-%'
    ) seeded
);

DELETE FROM integration_run
WHERE integration_id IN (
    SELECT seeded.id
    FROM (
        SELECT id
        FROM integration_definition
        WHERE client_name LIKE 'Seed Demo - %'
           OR base_url LIKE 'https://dummy-%'
    ) seeded
);

DELETE FROM integration_definition
WHERE client_name LIKE 'Seed Demo - %'
   OR base_url LIKE 'https://dummy-%';
