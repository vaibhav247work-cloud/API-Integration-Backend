# How To Use The Integration Engine

This guide explains how to start the app, inspect the seeded sample data, create a real integration, run it, and verify the CSV output.

## 1. What This App Does

The app lets you configure client API integrations without writing new Java code for each client.

For each client you can configure:

- authentication type
- request URL, headers, query params, and body
- JSON, XML, or SOAP response parsing
- pagination
- CSV column mapping
- constant and computed CSV columns
- schedule
- retry behavior
- run diagnostics and failure categorization
- rolling application logs
- output storage target

## 2. Prerequisites

- Java 17
- Maven 3.9+
- MySQL 8+

## 3. Configure Database

Create a database:

```sql
CREATE DATABASE integration_db;
```

By default the app uses:

```text
DB_URL=jdbc:mysql://localhost:3306/integration_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=root
```

Override them with environment variables if needed.

## 4. Start The App

Run:

```bash
mvn spring-boot:run
```

On startup:

- Flyway creates all tables
- Flyway inserts disabled dummy integrations from `V3__seed_dummy_integrations.sql`
- Flyway adds `brandCode` and multi-schedule support from `V4__add_schedule_type_and_brand_support.sql`
- Flyway adds run diagnostics fields from `V5__add_run_failure_diagnostics_and_logging_support.sql`
- Swagger UI becomes available at `http://localhost:8080/swagger-ui.html`

## 5. Understand The Seed Data

Seeded integrations are examples only. They are all stored with `enabled = false`.

They cover:

- all supported auth types
- JSON, XML, and SOAP examples
- `LOCAL`, `S3`, and `FTP` storage examples
- direct, constant, and computed CSV mappings
- single-date request fan-out examples
- local date filtering examples
- duplicate-handling examples

Important:

- the seeded URLs are dummy URLs
- do not enable them unless you replace the URLs and credentials with real values

## 6. Useful API Endpoints

Base URL:

```text
http://localhost:8080/api
```

Main endpoints:

- `GET /integrations`
- `GET /integrations/{id}`
- `POST /integrations`
- `PUT /integrations/{id}`
- `PATCH /integrations/{id}/enabled?value=true`
- `POST /integrations/{id}/run`
- `POST /integrations/{id}/run/custom`
- `GET /runs`
- `GET /runs/{id}`
- `GET /integrations/{id}/runs`
- `GET /failures`
- `POST /failures/{id}/retry`

Postman files:

- collection: [Integration-Engine.postman_collection.json](/d:/Workplace/API-Integration/postman/Integration-Engine.postman_collection.json)
- environment: [Integration-Engine.local.postman_environment.json](/d:/Workplace/API-Integration/postman/Integration-Engine.local.postman_environment.json)

## 7. First Check After Startup

Get all integrations:

```bash
curl http://localhost:8080/api/integrations
```

You should see the seed records.

If you want to inspect them directly in MySQL:

```sql
SELECT id, client_name, enabled, base_url
FROM integration_definition
ORDER BY id;
```

See field mappings:

```sql
SELECT integration_id, sort_order, mapping_type, target_header, source_path, expression
FROM integration_field_mapping
ORDER BY integration_id, sort_order;
```

## 8. Request Body For Creating An Integration

The `POST /api/integrations` and `PUT /api/integrations/{id}` body matches the `IntegrationDefinition` entity.

Main fields:

- `clientName`
- `brandCode`
- `baseUrl`
- `enabled`
- `scheduleConfig`
- `scheduleCron` (legacy fallback only)
- `csvFileName`
- `outputDirectory`
- `maxRetries`
- `authConfig`
- `responseConfig`
- `paginationConfig`
- `storageConfig`
- `stepConfig`
- `fieldMappings`

`fieldMappings` is an array of rows with:

- `sortOrder`
- `mappingType`
- `sourcePath`
- `pathType`
- `targetHeader`
- `expression`
- `defaultValue`
- `formatter`
- `requiredFlag`

Supported `formatter` values:

- `UPPERCASE`
- `LOWERCASE`
- `TRIM`
- `DATE:<outputPattern>`
- `DATE:<inputPattern>-><outputPattern>`
- `DATETIME:<outputPattern>`
- `DATETIME:<inputPattern>-><outputPattern>`

New request-window fields inside each `stepConfig` item:

- `requestWindowMode`: `NONE`, `SINGLE_DATE`, or `DATE_RANGE`
- `requestDateVariable`: placeholder variable name to fill during single-date fan-out
- `requestDateFormat`: optional Java date pattern like `yyyy-MM-dd` or `yyyyMMdd`

New response-window fields inside `responseConfig`:

- `filterByWindow`: `true` to filter records locally to the active schedule window
- `recordDatePath`: record-level JSONPath or XPath used for filtering
- `recordDatePathType`: `JSON_PATH` or `XPATH`
- `recordDateFormat`: optional Java date pattern for the record value
- `duplicateHandling`: post-mapping duplicate rules based on final CSV headers

`scheduleConfig` is an array like:

```json
[
  {
    "type": "DAILY",
    "enabled": true
  },
  {
    "type": "MONTHLY",
    "enabled": true
  },
  {
    "type": "HOURLY",
    "enabled": true,
    "intervalHours": 1
  }
]
```


## 9. Create A Simple JSON Integration

Example request:

```bash
curl -X POST http://localhost:8080/api/integrations \
  -H "Content-Type: application/json" \
  -d @- <<'JSON'
{
  "clientName": "Client A Orders",
  "brandCode": "CLIA",
  "baseUrl": "https://client-a.example.com/api",
  "enabled": false,
  "csvFileName": "client_a_orders.csv",
  "outputDirectory": "output/client-a",
  "maxRetries": 2,
  "scheduleConfig": [
    {
      "type": "DAILY",
      "enabled": true
    },
    {
      "type": "MONTHLY",
      "enabled": true
    },
    {
      "type": "HOURLY",
      "enabled": true,
      "intervalHours": 1
    }
  ],
  "authConfig": {
    "type": "API_KEY_HEADER",
    "headerName": "X-API-KEY",
    "headerValue": "replace-with-real-key"
  },
  "responseConfig": {
    "recordPath": "$.data.orders",
    "recordPathType": "JSON_PATH",
    "filterByWindow": true,
    "recordDatePath": "$.orderDate",
    "recordDatePathType": "JSON_PATH",
    "recordDateFormat": "yyyy-MM-dd",
    "duplicateHandling": {
      "enabled": true,
      "keyHeaders": ["ORDER_ID"],
      "defaultAction": "KEEP_FIRST",
      "fieldActions": {
        "GROSS_AMOUNT": "SUM"
      }
    }
  },
  "paginationConfig": {
    "enabled": true,
    "mode": "PAGE_NUMBER",
    "startPage": 1,
    "pageParam": "page",
    "totalPagesPath": "$.meta.totalPages",
    "totalPagesPathType": "JSON_PATH"
  },
  "storageConfig": {
    "type": "LOCAL",
    "localDirectory": "output/client-a"
  },
  "stepConfig": [
    {
      "orderIndex": 1,
      "name": "fetch-orders",
      "method": "GET",
      "url": "/orders",
      "queryParams": {
        "page": "${page}",
        "fromDate": "${windowStartDate}",
        "toDate": "${windowEndDateExclusive}"
      },
      "requestFormat": "JSON",
      "responseFormat": "JSON",
      "requestWindowMode": "DATE_RANGE",
      "paginate": true,
      "dataStep": true
    }
  ],
  "fieldMappings": [
    {
      "sortOrder": 1,
      "mappingType": "SOURCE_PATH",
      "sourcePath": "$.orderId",
      "pathType": "JSON_PATH",
      "targetHeader": "ORDER_ID",
      "requiredFlag": true
    },
    {
      "sortOrder": 2,
      "mappingType": "CONSTANT",
      "targetHeader": "STORE_ID",
      "expression": "1",
      "requiredFlag": false
    },
    {
      "sortOrder": 3,
      "mappingType": "EXPRESSION",
      "targetHeader": "GROSS_AMOUNT",
      "expression": "num('$.net') + num('$.gross')",
      "defaultValue": "0",
      "requiredFlag": false
    }
  ]
}
JSON
```

## 10. Single-Date APIs And Full-Dump APIs

Not every client supports `startDate` and `endDate`.

### Client supports only one date

Use `requestWindowMode = SINGLE_DATE`.

For a monthly run, the engine will call the API once per day inside the monthly window and merge all responses into one run and one CSV.

Example:

```json
{
  "responseConfig": {
    "recordPath": "$.data.orders",
    "recordPathType": "JSON_PATH",
    "filterByWindow": true,
    "recordDatePath": "$.orderDate",
    "recordDatePathType": "JSON_PATH",
    "recordDateFormat": "yyyy-MM-dd"
  },
  "stepConfig": [
    {
      "orderIndex": 1,
      "name": "fetch-orders-by-date",
      "method": "GET",
      "url": "/orders",
      "queryParams": {
        "business_date": "${requestDate}"
      },
      "requestFormat": "JSON",
      "responseFormat": "JSON",
      "requestWindowMode": "SINGLE_DATE",
      "requestDateVariable": "requestDate",
      "requestDateFormat": "yyyy-MM-dd",
      "dataStep": true
    }
  ]
}
```

Behavior:

- daily schedule: 1 API call for T-1 day
- monthly schedule: 28-31 API calls inside one monthly run
- manual ad hoc run: 1 API call using today as `requestDate`

### Client returns too much data

If a client returns a full dump or more than one month, keep `requestWindowMode = NONE` or `DATE_RANGE` and enable response filtering:

```json
{
  "responseConfig": {
    "recordPath": "$.data.orders",
    "recordPathType": "JSON_PATH",
    "filterByWindow": true,
    "recordDatePath": "$.orderDate",
    "recordDatePathType": "JSON_PATH",
    "recordDateFormat": "yyyy-MM-dd"
  }
}
```

The engine will extract all records from the response, then keep only the records whose `orderDate` falls inside the active schedule window.

## 11. Duplicate Handling

Duplicate handling is applied after field mappings and expressions are evaluated.

That means you can:

- ignore duplicate rows by bill number, order id, invoice id, etc.
- sum mapped numeric columns
- sum computed columns created with `mappingType = EXPRESSION`

The duplicate key must use mapped CSV headers, not raw API paths.

Example: ignore duplicate rows and keep the first row for each bill number:

```json
{
  "responseConfig": {
    "duplicateHandling": {
      "enabled": true,
      "keyHeaders": ["BILL_NO"],
      "defaultAction": "KEEP_FIRST"
    }
  }
}
```

Example: merge duplicate bills and sum selected columns:

```json
{
  "responseConfig": {
    "duplicateHandling": {
      "enabled": true,
      "keyHeaders": ["BILL_NO"],
      "defaultAction": "KEEP_FIRST",
      "fieldActions": {
        "NET_AMOUNT": "SUM",
        "GROSS_AMOUNT": "SUM",
        "TOTAL_AMOUNT": "SUM"
      }
    }
  }
}
```

Behavior:

- rows with the same `BILL_NO` are merged into one output row
- unspecified columns follow `defaultAction`
- `SUM` requires numeric values
- if the duplicate key is blank, the row is treated as unique and is not merged

This is useful when:

- one client wants duplicate invoices ignored
- another client wants duplicate invoice amounts added together
- `TOTAL_AMOUNT` is a computed expression and still needs to be summed

## 12. Run An Integration Manually

If the integration ID is `8`:

```bash
curl -X POST http://localhost:8080/api/integrations/8/run
```

The run response now returns the full `IntegrationRun` row. Important fields:

- `status`
- `failureCategory`
- `failedStepName`
- `failedRequestUrl`
- `httpStatusCode`
- `responsePreview`
- `errorMessage`
- `recordsProcessed`
- `outputLocation`

## 13. Run With Custom Dates

Use this when you want to run a client manually for a specific business window instead of the default ad hoc window.

Date-only example:

```bash
curl -X POST http://localhost:8080/api/integrations/8/run/custom \
  -H "Content-Type: application/json" \
  -d '{
    "fromDate": "2026-02-01",
    "toDate": "2026-02-28"
  }'
```

This creates a `CUSTOM` run with:

- `windowStart = 2026-02-01T00:00:00`
- `windowEnd = 2026-03-01T00:00:00`

Single-day example:

```bash
curl -X POST http://localhost:8080/api/integrations/8/run/custom \
  -H "Content-Type: application/json" \
  -d '{
    "fromDate": "2026-02-14"
  }'
```

This runs only for `2026-02-14`.

Date-time example:

```bash
curl -X POST http://localhost:8080/api/integrations/8/run/custom \
  -H "Content-Type: application/json" \
  -d '{
    "fromDateTime": "2026-02-14T10:00:00",
    "toDateTime": "2026-02-14T18:00:00",
    "fileToken": "backfill_20260214_shift_a"
  }'
```

Rules:

- use either `fromDate` / `toDate` or `fromDateTime` / `toDateTime`
- do not send both date and date-time fields together
- if only `fromDate` is provided, the engine uses that one day
- for date ranges, `toDate` is inclusive in the API request body
- for date-time ranges, `toDateTime` must be after `fromDateTime`
- `fileToken` is optional and controls the suffix used in the CSV file name

Check run history:

```bash
curl http://localhost:8080/api/integrations/8/runs
```

Or all runs:

```bash
curl http://localhost:8080/api/runs
```

Or fetch one run directly:

```bash
curl http://localhost:8080/api/runs/15
```

## 14. Update A Wrong Entry

Yes, you can correct saved values like:

- `brandCode`
- `scheduleConfig`
- `scheduleCron` (legacy only)
- `csvFileName`
- `outputDirectory`
- `authConfig`
- `stepConfig`
- `paginationConfig`
- `responseConfig`
- `fieldMappings`

Preferred way:

- fetch the current integration using `GET /api/integrations/{id}`
- edit the values you want to fix
- send the full updated payload using `PUT /api/integrations/{id}`

Example:

```bash
curl http://localhost:8080/api/integrations/8
```

Then update it:

```bash
curl -X PUT http://localhost:8080/api/integrations/8 \
  -H "Content-Type: application/json" \
  -d @updated-integration.json
```

Important:

- `PUT` is a full update, not a partial update
- send the complete integration object
- if you send an empty or missing `fieldMappings`, the old mappings will be replaced
- if you change `scheduleConfig`, `scheduleCron`, or `enabled` using the API, the scheduler is refreshed automatically

Direct DB update is possible, but it is not the preferred method while the app is running.

Reason:

- if you update `scheduleConfig` or `scheduleCron` directly in MySQL, the row changes in DB
- but the running app may still keep the old in-memory schedule until restart or API save

So for live changes, use the API.

## 15. Enable Scheduled Execution

When your configuration is ready:

```bash
curl -X PATCH "http://localhost:8080/api/integrations/8/enabled?value=true"
```

The app will register all selected schedule types dynamically.

Supported schedule types:

```text
DAILY
MONTHLY
HOURLY
```

Behavior:

- `DAILY`: runs every day and uses T-1 day as the processing window
- `MONTHLY`: runs on the 1st day of the month and uses the previous month
- `HOURLY`: runs every 1 hour by default

Example:

```json
[
  {
    "type": "DAILY",
    "enabled": true
  },
  {
    "type": "MONTHLY",
    "enabled": true
  },
  {
    "type": "HOURLY",
    "enabled": true,
    "intervalHours": 1
  }
]
```

Available runtime variables in request templates:

- `${scheduleType}`
- `${windowStartDate}`
- `${windowEndDateExclusive}`
- `${windowStartDateTime}`
- `${windowEndDateTime}`
- `${requestDate}` for `SINGLE_DATE` steps
- `${requestDateIso}` for `SINGLE_DATE` steps
- `${businessDate}` for daily runs
- `${previousMonth}` for monthly runs
- `${processHour}` for hourly runs

To add a new schedule type later, add a new `ScheduleType` and one new schedule strategy class.

## 16. Multi-Step Flow Example

Use multiple `stepConfig` entries when one API call depends on another.

Example:

1. Step 1 creates a session or gets a token
2. Step 1 stores values like `sessionId`
3. Step 2 uses `${sessionId}` in URL, query params, headers, or body

Example step fragment:

```json
[
  {
    "orderIndex": 1,
    "name": "start-session",
    "method": "POST",
    "url": "/session",
    "requestFormat": "JSON",
    "responseFormat": "JSON",
    "bodyTemplate": "{\"clientCode\":\"ABC\"}",
    "responseVariables": {
      "$.sessionId": "sessionId"
    },
    "responseVariablePathType": "JSON_PATH",
    "dataStep": false
  },
  {
    "orderIndex": 2,
    "name": "fetch-data",
    "method": "GET",
    "url": "/orders",
    "queryParams": {
      "sessionId": "${sessionId}"
    },
    "requestFormat": "JSON",
    "responseFormat": "JSON",
    "dataStep": true
  }
]
```

## 17. XML And SOAP Usage

For XML or SOAP:

- set `requestFormat` to `XML` or `SOAP`
- set `responseFormat` to `XML` or `SOAP`
- use `recordPathType = XPATH`
- use `pathType = XPATH` in field mappings

Example:

```json
{
  "responseConfig": {
    "recordPath": "//*[local-name()='Invoice']",
    "recordPathType": "XPATH"
  }
}
```

Mapping example:

```json
{
  "sortOrder": 1,
  "mappingType": "SOURCE_PATH",
  "sourcePath": "./InvoiceId/text()",
  "pathType": "XPATH",
  "targetHeader": "INVOICE_ID",
  "requiredFlag": true
}
```

## 18. CSV Mapping Types

### `SOURCE_PATH`

Reads a value directly from the current API record.

Example:

```json
{
  "mappingType": "SOURCE_PATH",
  "sourcePath": "$.customer.name",
  "pathType": "JSON_PATH",
  "targetHeader": "CUSTOMER_NAME"
}
```

### `CONSTANT`

Writes the same value to every row.

Example:

```json
{
  "mappingType": "CONSTANT",
  "targetHeader": "STORE_ID",
  "expression": "1"
}
```

### `EXPRESSION`

Builds a computed value.

Supported helpers:

- `value('$.field')`
- `num('$.field')`
- `column('HEADER_NAME')`
- `columnNum('HEADER_NAME')`
- `ctx('variableName')`
- `ctxNum('variableName')`

Examples:

```json
{
  "mappingType": "EXPRESSION",
  "targetHeader": "FULL_NAME",
  "expression": "value('$.firstName') + ' ' + value('$.lastName')"
}
```

```json
{
  "mappingType": "EXPRESSION",
  "targetHeader": "TOTAL_AMOUNT",
  "expression": "num('$.net') + num('$.tax')"
}
```

Important:

- if you use `column('...')`, keep that dependent mapping after the referenced column using `sortOrder`

## 19. Storage Options

### Local

```json
{
  "type": "LOCAL",
  "localDirectory": "output/client-a"
}
```

### S3

```json
{
  "type": "S3",
  "bucket": "my-export-bucket",
  "region": "ap-south-1",
  "keyPrefix": "daily/orders"
}
```

### FTP

```json
{
  "type": "FTP",
  "host": "ftp.example.com",
  "port": 21,
  "username": "ftp-user",
  "password": "ftp-pass",
  "remoteDirectory": "/exports/orders",
  "passiveMode": true
}
```

### HTTP_API

Posts the generated CSV file as `multipart/form-data` to any HTTP endpoint.

```json
{
  "type": "HTTP_API",
  "uploadUrl": "https://your-server.example.com/uploadFile?tenantId=YOUR_TENANT_ID",
  "uploadMethod": "POST",
  "uploadFileParam": "file",
  "uploadHeaders": {
    "Event": "ftp",
    "accessToken": "your-access-token",
    "userId": "your-user-id",
    "channel": "daily"
  },
  "uploadFormFields": {
    "partyC_Code": "your-party-code"
  }
}
```

Tenant-property example:

```json
{
  "type": "HTTP_API",
  "tenantId": "tenant-a",
  "uploadMethod": "POST",
  "uploadFileParam": "file",
  "uploadHeaders": {
    "Event": "ftp",
    "channel": "daily"
  },
  "uploadFormFields": {
    "partyC_Code": "your-party-code"
  }
}
```

Application config example:

```yaml
integration:
  storage:
    http-api:
      upload-url-template:
        MONTHLY: https://default-api.example.com
        HOURLY: https://hourly-api.example.com
        DAILY: https://default-api.example.com
      access-token-header-name: accessToken
      user-id-header-name: userId
      tenants:
        tenant-a:
          access-token: your-access-token
          user-id: your-user-id
```

When `tenantId` is set, the engine resolves the final upload URL from:

1. `storageConfig.uploadUrl`
2. `integration.storage.http-api.tenants.<tenantId>.upload-url`
3. `integration.storage.http-api.upload-url-template.<SCHEDULE_TYPE>`

It also injects tenant-specific `accessToken` and `userId` headers from the matching property entry, so `uploadUrl` can be omitted from `storageConfig` when tenant properties are configured.

Fields:

| Field | Required | Default | Description |
|---|---|---|---|
| `tenantId` | no | â€” | Tenant key used to resolve URL, token, and user ID from application properties |
| `uploadUrl` | yes | — | Full URL including any query params (e.g. `?tenantId=...`) |
| `uploadMethod` | no | `POST` | HTTP method: `POST` or `PUT` |
| `uploadFileParam` | no | `file` | Multipart form-data key used for the file |
| `uploadHeaders` | no | — | Map of HTTP headers to send with the request |
| `uploadFormFields` | no | — | Map of extra multipart form fields to include |

Retry behavior:

- `5xx` server errors and `429` rate limit → retryable (uses `maxRetries`)
- `4xx` client errors (wrong URL, bad token) → not retried, recorded as `STORAGE_ERROR`

## 20. File Naming

CSV files now start with `brandCode`.

Format:

```text
{brandCode}_{csvFileNameWithoutExtension}_{scheduleToken}.csv
```

Examples:

```text
CLIA_client_a_orders_daily_20260312.csv
CLIA_client_a_orders_monthly_202602.csv
CLIA_client_a_orders_hourly_2026031309.csv
CLIA_client_a_orders_adhoc_20260313153045.csv
CLIA_client_a_orders_custom_20260201000000_20260301000000.csv
```

`brandCode` should be the client code you want at the start of the file name.

## 21. Run Statuses And Failure Queue

Common run statuses:

- `SUCCESS`: CSV created and stored successfully
- `NO_DATA`: API call succeeded but no usable records were produced
- `FAILED`: non-retryable failure
- `RETRY_QUEUED`: retryable failure was queued

Important diagnostic fields in `integration_run`:

- `failureCategory`
- `failedStepName`
- `failedRequestUrl`
- `httpStatusCode`
- `responsePreview`
- `errorMessage`

Important failure categories you will see:

- `AUTHENTICATION_ERROR`: 401 or 403, token fetch failure, wrong credentials
- `HTTP_ERROR`: non-auth 4xx/5xx response
- `NETWORK_ERROR`: DNS, connection, timeout, SSL, host unreachable
- `EMPTY_RESPONSE`: API returned HTTP success but blank body
- `NO_DATA`: body was present but no records matched the configured `recordPath`
- `RESPONSE_PARSING_ERROR`: invalid JSON/XML or wrong record extraction path
- `MAPPING_ERROR`: required mapping value missing
- `STORAGE_ERROR`: local write, S3 upload, or FTP upload failed
- `CONFIGURATION_ERROR`: bad integration setup in DB

Behavior to remember:

- `NO_DATA` and `EMPTY_RESPONSE` are stored in run history, but they are not queued for retry
- retry queue is only used for retryable failures such as network issues, HTTP `429`, and HTTP `5xx`
- `responsePreview` is truncated intentionally so logs and DB rows stay readable

If an integration fails and `maxRetries > 0`:

- the run is saved as `RETRY_QUEUED`
- an entry is created in `failed_job_queue`
- the retry scheduler picks it up later

Check failures:

```bash
curl http://localhost:8080/api/failures
```

Retry manually:

```bash
curl -X POST http://localhost:8080/api/failures/3/retry
```

## 22. Application Logs

The app writes rolling logs to:

- `logs/integration-engine.log`
- archived logs under `logs/archive/`

Current rolling policy:

- max active file size: `10MB`
- archive retention: `15` days
- old archives are cleaned automatically on startup

You can override the active file path with:

```text
INTEGRATION_LOG_FILE
```

The log pattern includes:

- correlation id
- client name
- schedule type

This makes it easier to trace one run across scheduler, HTTP call, extraction, CSV, and storage logs.

## 23. Common Problems

### App starts but no CSV is created

Check:

- the integration actually ran
- the run status in `/api/runs`
- the diagnostic fields in `/api/runs/{id}`
- the response `recordPath`
- the `fieldMappings`
- the configured storage target

### Integration fails immediately

Check:

- wrong auth config
- wrong request URL
- 401/403 from client auth endpoint
- 404/405 from wrong API path or method
- 429 rate limit from client API
- 5xx from client API
- DNS, proxy, firewall, or timeout issue
- wrong JSONPath or XPath
- wrong request or response format
- dummy seeded config was enabled without replacing the sample values

### Client API returns success but no file is created

Check the run status:

- if status is `NO_DATA` with `failureCategory = EMPTY_RESPONSE`, the API returned a blank body
- if status is `NO_DATA` with `failureCategory = NO_DATA`, the API responded but no rows matched `recordPath`
- if status is `FAILED` with `failureCategory = RESPONSE_PARSING_ERROR`, the body format or path config is wrong

### Monthly job should call one-date API many times

Use `requestWindowMode = SINGLE_DATE`.

Then:

- the scheduler still runs once for the month
- inside that one run, the engine loops day by day
- all daily responses are merged into one CSV

### Client returns two months or full dump

Use response-side filtering:

- `filterByWindow = true`
- `recordDatePath`
- `recordDatePathType`
- `recordDateFormat`

Then the engine keeps only the records inside the current run window.

### Computed column is blank or wrong

Check:

- `mappingType` is `EXPRESSION`
- the expression uses valid paths
- the paths match the current record format
- `sortOrder` is correct when using `column(...)`

### Duplicate rows are not merging as expected

Check:

- `responseConfig.duplicateHandling.enabled = true`
- `keyHeaders` uses mapped CSV headers like `BILL_NO`, not raw API paths
- duplicate key columns are not blank
- `SUM` is used only on numeric output columns
- computed columns like `TOTAL_AMOUNT` are included in `fieldActions` if they also need summing

### Scheduler is not doing what I expect

Check:

- the integration is `enabled = true`
- `scheduleConfig` contains at least one enabled schedule type
- you are not still depending on old `scheduleCron`
- you updated the integration through the API, not only directly in MySQL

## 24. Recommended Way To Onboard A New Client

1. Copy one of the seeded integrations that is closest to the client type.
2. Replace base URL, auth config, and step config with real values.
3. Test with `enabled = false`.
4. Run it manually with `POST /api/integrations/{id}/run`.
5. Verify CSV output and run history.
6. Enable cron scheduling only after manual validation.

## 25. Files To Read Next

- [CONFIGURATION-VARIABLE-REFERENCE.md](/d:/Workplace/API-Integration/CONFIGURATION-VARIABLE-REFERENCE.md)
- [universal-integration-engine-doc.md](/d:/Workplace/API-Integration/universal-integration-engine-doc.md)
- [application.yml](/d:/Workplace/API-Integration/src/main/resources/application.yml)
- [IntegrationDefinitionController.java](/d:/Workplace/API-Integration/src/main/java/com/example/integration/controller/IntegrationDefinitionController.java)
- [ResponseExtractionService.java](/d:/Workplace/API-Integration/src/main/java/com/example/integration/service/ResponseExtractionService.java)
- [IntegrationOrchestrator.java](/d:/Workplace/API-Integration/src/main/java/com/example/integration/service/IntegrationOrchestrator.java)
