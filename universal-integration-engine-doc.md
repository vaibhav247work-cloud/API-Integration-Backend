# Universal API Integration Engine

A configurable Spring Boot integration engine for calling different client APIs with different authentication models, request/response formats, dynamic field mapping, CSV generation, scheduling, retries, and storage routing.

Step-by-step usage instructions are in [HOW-TO-USE.md](/d:/Workplace/API-Integration/HOW-TO-USE.md).
Variable, placeholder, and sample configuration reference is in [CONFIGURATION-VARIABLE-REFERENCE.md](/d:/Workplace/API-Integration/CONFIGURATION-VARIABLE-REFERENCE.md).

## Implemented Capabilities

- Dynamic client integration configuration from database
- JSON and XML/SOAP response handling
- Multiple authentication strategies
- Multi-step API flow with runtime variable extraction
- Pagination support
- Single-date request fan-out across daily/monthly windows
- Local response window filtering for over-returning APIs
- Duplicate row handling with per-column `KEEP_FIRST` or `SUM` rules
- CSV generation with dynamic headers
- Multiple schedule types per client: `DAILY`, `MONTHLY`, `HOURLY`
- Brand-code-prefixed schedule-aware file naming
- Parallel execution through async task pools
- Retry and failure queue processing
- Run diagnostics with failure category, failed step, URL, HTTP status, and response preview
- `NO_DATA` and `EMPTY_RESPONSE` handling for blank or non-matching client responses
- Manual custom-date and custom-date-time backfill runs
- Rolling application logs with 10 MB rotation and 15-day retention
- Local, S3, and FTP CSV storage
- REST configuration console for managing integrations
- Flyway database migration
- Swagger UI for backend API exploration

## Runtime Flow

```text
Dynamic Scheduler
  -> Load integration definition
  -> Build execution context
  -> Apply auth strategy
  -> Execute configured HTTP steps
  -> Handle pagination
  -> Extract records from JSON or XML
  -> Map fields to standard CSV headers
  -> Generate CSV
  -> Store CSV to LOCAL / S3 / FTP
  -> Persist run history
  -> Persist failure diagnostics when applicable
  -> Queue retry on failure when configured
```

## Project Structure

```text
src/main/java/com/example/integration
  |- config
  |- controller
  |- entity
  |- model/config
  |- model/enums
  |- model/runtime
  |- repository
  |- scheduler
  |- service
  |- service/auth
  `- service/storage
```

## Database Tables

- `integration_definition`
- `integration_field_mapping`
- `integration_run`
- `failed_job_queue`

## Supported Authentication Types

- `NONE`
- `BASIC`
- `API_KEY_HEADER`
- `API_KEY_QUERY`
- `BEARER_STATIC`
- `TOKEN_API`
- `OAUTH2_CLIENT_CREDENTIALS`

## Example Config Model

`auth_config`

```json
{
  "type": "TOKEN_API",
  "method": "POST",
  "tokenUrl": "https://client.example.com/auth",
  "headers": {
    "Content-Type": "application/json"
  },
  "bodyTemplate": "{\"username\":\"${apiUser}\",\"password\":\"${apiPassword}\"}",
  "responseFormat": "JSON",
  "tokenPath": "$.access_token",
  "tokenPathType": "JSON_PATH",
  "tokenHeaderName": "Authorization",
  "tokenPrefix": "Bearer "
}
```

`step_config`

```json
[
  {
    "orderIndex": 1,
    "name": "session-init",
    "method": "POST",
    "url": "/session",
    "requestFormat": "JSON",
    "responseFormat": "JSON",
    "bodyTemplate": "{\"fromDate\":\"${today}\"}",
    "responseVariables": {
      "$.sessionId": "sessionId"
    },
    "responseVariablePathType": "JSON_PATH",
    "dataStep": false
  },
  {
    "orderIndex": 2,
    "name": "student-export",
    "method": "GET",
    "url": "/students",
    "queryParams": {
      "sessionId": "${sessionId}",
      "page": "${page}",
      "businessDate": "${requestDate}"
    },
    "requestFormat": "JSON",
    "responseFormat": "JSON",
    "requestWindowMode": "SINGLE_DATE",
    "requestDateVariable": "requestDate",
    "requestDateFormat": "yyyy-MM-dd",
    "paginate": true,
    "dataStep": true,
    "responseAlias": "student"
  }
]
```

`response_config`

```json
{
  "recordPath": "$.data.students",
  "recordPathType": "JSON_PATH",
  "filterByWindow": true,
  "recordDatePath": "$.attendanceDate",
  "recordDatePathType": "JSON_PATH",
  "recordDateFormat": "yyyy-MM-dd",
  "duplicateHandling": {
    "enabled": true,
    "keyHeaders": ["STUDENT_ID"],
    "defaultAction": "KEEP_FIRST",
    "fieldActions": {
      "TOTAL_AMOUNT": "SUM"
    }
  }
}
```

`pagination_config`

```json
{
  "enabled": true,
  "mode": "PAGE_NUMBER",
  "startPage": 1,
  "pageParam": "page",
  "totalPagesPath": "$.meta.totalPages",
  "totalPagesPathType": "JSON_PATH"
}
```

`storage_config`

```json
{
  "type": "S3",
  "bucket": "client-exports",
  "region": "ap-south-1",
  "keyPrefix": "daily/student-feed"
}
```

`schedule_config`

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

Field mappings are stored as rows in `integration_field_mapping`, for example:

```text
$.name        -> NAME
$.age         -> AGE
$.address.city -> CITY
```

Dynamic CSV columns are also supported through `mapping_type`:

```text
SOURCE_PATH  : direct API field mapping
CONSTANT     : fixed value for every row
EXPRESSION   : computed value using SpEL-style functions
```

Examples:

```text
target_header = STORE_ID
mapping_type  = CONSTANT
expression    = 1
```

```text
target_header = GROSS_AMOUNT
mapping_type  = EXPRESSION
expression    = num('$.net') + num('$.gross')
```

Supported expression helpers:

```text
value('$.field')        -> string value from current record
num('$.field')          -> numeric value from current record
column('HEADER_NAME')   -> already computed CSV column value
columnNum('HEADER_NAME')-> numeric value from already computed CSV column
ctx('variableName')     -> execution-context value from earlier steps
ctxNum('variableName')  -> numeric execution-context value
```

If you use `column(...)`, keep the dependent mapping after the source column by `sort_order`.

Duplicate handling runs after mapping and expression evaluation, so `SUM` can be applied to computed columns such as `TOTAL_AMOUNT`.

For XML/SOAP integrations use `XPATH` in `path_type` and `recordPathType`, for example:

```text
recordPath: //*[local-name()='Student']
sourcePath: ./*[local-name()='Name']/text()
```

## REST Configuration Console

Base path: `/api`

- `GET /api/integrations`
- `GET /api/integrations/{id}`
- `POST /api/integrations`
- `PUT /api/integrations/{id}`
- `PATCH /api/integrations/{id}/enabled?value=true`
- `POST /api/integrations/{id}/run`
- `POST /api/integrations/{id}/run/custom`
- `DELETE /api/integrations/{id}`
- `GET /api/runs`
- `GET /api/runs/{id}`
- `GET /api/integrations/{id}/runs`
- `GET /api/failures`
- `POST /api/failures/{id}/retry`

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

## How To Run

1. Create MySQL database.

```sql
CREATE DATABASE integration_db;
```

2. Set environment variables if needed.

```text
DB_URL
DB_USERNAME
DB_PASSWORD
INTEGRATION_EXECUTOR_POOL_SIZE
INTEGRATION_SCHEDULER_POOL_SIZE
INTEGRATION_RETRY_POLL_INTERVAL_MS
INTEGRATION_RETRY_DELAY_MINUTES
INTEGRATION_CSV_TEMP_DIR
```

3. Run the application.

```bash
mvn spring-boot:run
```

4. Create integration definitions through the REST API or Swagger UI.

Sample seed data is also included through Flyway in `V3__seed_dummy_integrations.sql`.
All seeded integrations are created with `enabled = false` so they are safe templates by default.

## Implemented Future Improvements

- Dynamic cron scheduler per client: implemented through `DynamicScheduleService`
- Multi-schedule execution with type strategies: implemented through `DynamicScheduleService` plus schedule strategy classes
- Parallel API processing: implemented through async executor-backed orchestration
- Retry and failure queue: implemented through `failed_job_queue` and `RetryQueueService`
- Operational diagnostics: implemented through `integration_run`, `failed_job_queue`, and `IntegrationFailureException`
- Rolling file logging: implemented through Spring Boot logback rolling policy in `application.yml`
- XML / SOAP support: implemented through XPath-based extraction
- S3 / FTP CSV storage: implemented through storage providers
- UI configuration console: implemented as REST management APIs with Swagger UI

## Current Extension Points

- Add new auth types by introducing another `AuthStrategy`
- Add new storage targets by introducing another `StorageProvider`
- Add formatter rules in `ResponseExtractionService`
- Add notification hooks after run completion
- Add frontend UI later on top of the existing REST console
