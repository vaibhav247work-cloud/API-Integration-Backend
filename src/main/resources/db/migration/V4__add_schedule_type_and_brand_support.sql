ALTER TABLE integration_definition
    ADD COLUMN brand_code VARCHAR(100) NULL AFTER client_name,
    ADD COLUMN schedule_config JSON NULL AFTER schedule_cron;

ALTER TABLE integration_run
    ADD COLUMN schedule_type VARCHAR(40) NULL AFTER attempt_number,
    ADD COLUMN window_start TIMESTAMP NULL AFTER schedule_type,
    ADD COLUMN window_end TIMESTAMP NULL AFTER window_start,
    ADD COLUMN file_token VARCHAR(100) NULL AFTER window_end;

UPDATE integration_definition
SET brand_code = 'NOAUTH',
    schedule_config = CAST('[{"type":"DAILY","enabled":true}]' AS JSON)
WHERE client_name = 'Seed Demo - No Auth JSON Local';

UPDATE integration_definition
SET brand_code = 'BASIC',
    schedule_config = CAST('[{"type":"DAILY","enabled":true}]' AS JSON)
WHERE client_name = 'Seed Demo - Basic JSON Local';

UPDATE integration_definition
SET brand_code = 'XMLFTP',
    schedule_config = CAST('[{"type":"MONTHLY","enabled":true}]' AS JSON)
WHERE client_name = 'Seed Demo - API Key Header XML FTP';

UPDATE integration_definition
SET brand_code = 'QRYKEY',
    schedule_config = CAST('[{"type":"HOURLY","enabled":true,"intervalHours":1}]' AS JSON)
WHERE client_name = 'Seed Demo - API Key Query JSON Local';

UPDATE integration_definition
SET brand_code = 'SOAPS3',
    schedule_config = CAST('[{"type":"MONTHLY","enabled":true}]' AS JSON)
WHERE client_name = 'Seed Demo - Bearer Static SOAP S3';

UPDATE integration_definition
SET brand_code = 'TOKEN',
    schedule_config = CAST('[{"type":"DAILY","enabled":true},{"type":"HOURLY","enabled":true,"intervalHours":1}]' AS JSON)
WHERE client_name = 'Seed Demo - Token API JSON Local';

UPDATE integration_definition
SET brand_code = 'OAUTH',
    schedule_config = CAST('[{"type":"DAILY","enabled":true},{"type":"MONTHLY","enabled":true}]' AS JSON)
WHERE client_name = 'Seed Demo - OAuth2 Client Credentials JSON Local';
