# Configuration Variable Reference

This file explains:

- which runtime variables are available
- where `${...}` placeholders can be used
- where expression functions like `ctx()` and `num()` are used instead
- sample JSON payloads for basic and advanced client configurations

## 1. Where `${...}` Placeholders Work

`${...}` placeholders are resolved by the request templating layer.

You can use them in:

- `stepConfig[].url`
- `stepConfig[].headers`
- `stepConfig[].queryParams`
- `stepConfig[].bodyTemplate`
- `authConfig.tokenUrl`
- `authConfig.headers`
- `authConfig.bodyTemplate`

Important:

- `fieldMappings[].expression` does **not** use `${...}` placeholders
- `fieldMappings[].expression` uses expression functions like `value()`, `num()`, `column()`, and `ctx()`
- `csvFileName`, `outputDirectory`, `storageConfig`, and `scheduleConfig` are treated as normal config values, not runtime templates
- for `HTTP_API` storage, `storageConfig.tenantId` can point to tenant-specific URL and header values defined in application properties

## 2. Built-In Runtime Variables

These variables are created in the execution context for each run.

| Variable | Example | Use |
|---|---|---|
| `${clientName}` | `Client A Orders` | Client name from integration definition |
| `${brandCode}` | `CLIA` | Brand/client code |
| `${correlationId}` | UUID | Trace one run across logs |
| `${today}` | `2026-03-13` | Current system date when run starts |
| `${now}` | `2026-03-13T10:15:30` | Current system timestamp when run starts |
| `${scheduleType}` | `DAILY` | Current run type: `AD_HOC`, `CUSTOM`, `DAILY`, `MONTHLY`, `HOURLY`, `LEGACY_CRON` |
| `${fileToken}` | `daily_20260312` | Token used in file naming |
| `${triggerDateTime}` | `2026-03-13T01:00:00` | Scheduler/manual trigger time |
| `${windowStartDateTime}` | `2026-03-12T00:00:00` | Processing window start |
| `${windowEndDateTime}` | `2026-03-13T00:00:00` | Processing window end |
| `${windowStartDate}` | `2026-03-12` | Processing window start date |
| `${windowEndDateExclusive}` | `2026-03-13` | Processing window end date, exclusive |
| `${processDate}` | `2026-03-12` | Active process date; in `SINGLE_DATE` mode this changes per loop |
| `${processMonth}` | `202603` | Processing month token |
| `${processHour}` | `2026031209` | Processing hour token |

## 3. Schedule-Specific Variables

These are available only for certain schedule types.

| Variable | Available When | Example | Use |
|---|---|---|---|
| `${businessDate}` | `DAILY` | `2026-03-12` | T-1 business date |
| `${previousMonth}` | `MONTHLY` | `2026-02` | Previous month for monthly jobs |
| `${requestDate}` | `SINGLE_DATE` step mode | `2026-02-14` | Date sent for the current day in fan-out loop |
| `${requestDateIso}` | `SINGLE_DATE` step mode | `2026-02-14` | ISO date for the current day in fan-out loop |

## 4. Pagination Variables

These are added only while paginated API calls are running.

| Variable | Example | How It Is Created |
|---|---|---|
| `${page}` | `1` | Always set for `PAGE_NUMBER` pagination |
| `${<pageParam>}` | `${pageNo}` -> `1` | If `paginationConfig.pageParam = "pageNo"` |
| `${<sizeParam>}` | `${pageSize}` -> `100` | If `paginationConfig.sizeParam = "pageSize"` and `pageSize` is configured |

Example:

```json
{
  "paginationConfig": {
    "enabled": true,
    "mode": "PAGE_NUMBER",
    "startPage": 1,
    "pageParam": "pageNo",
    "sizeParam": "pageSize",
    "pageSize": 100,
    "totalPagesPath": "$.meta.totalPages",
    "totalPagesPathType": "JSON_PATH"
  },
  "stepConfig": [
    {
      "queryParams": {
        "page": "${page}",
        "pageNo": "${pageNo}",
        "size": "${pageSize}"
      }
    }
  ]
}
```

## 5. Variables Created From Auth Or Previous Steps

These are not built in by default. They are created during the flow.

### Token Variables

If auth type is `TOKEN_API` or `OAUTH2_CLIENT_CREDENTIALS`, the engine stores:

| Variable | Example | Use |
|---|---|---|
| `${token}` | access token string | Reuse token in request templates if needed |

Note:

- the engine already injects the auth header automatically
- `${token}` is still useful if a client wants the token in query params or request body

### `responseVariables`

`responseVariables` lets you extract specific values from one step and reuse them later.

Example:

```json
{
  "responseVariables": {
    "$.sessionId": "sessionId",
    "$.storeCode": "storeCode"
  }
}
```

This creates:

- `${sessionId}`
- `${storeCode}`

### `responseAlias`

`responseAlias` stores the full response body of a step as a variable named:

```text
${<responseAlias>Response}
```

Example:

```json
{
  "responseAlias": "session"
}
```

This creates:

```text
${sessionResponse}
```

Important:

- if you set `responseAlias = "sessionResponse"`, the variable becomes `${sessionResponseResponse}`
- better practice is to use a short alias like `session`, `orders`, `tokenStep`

## 6. Expression Functions For `fieldMappings[].expression`

Use these only inside `fieldMappings[].expression`.

| Function | Use | Example |
|---|---|---|
| `value('path')` | Read string from current source record | `value('$.customer.name')` |
| `num('path')` | Read numeric value from current source record | `num('$.net') + num('$.tax')` |
| `column('HEADER')` | Read a previously mapped CSV column in the same row | `column('FIRST_NAME') + ' ' + column('LAST_NAME')` |
| `columnNum('HEADER')` | Read a previously mapped CSV column as number | `columnNum('NET_AMOUNT') + columnNum('TAX_AMOUNT')` |
| `ctx('key')` | Read a runtime variable from execution context | `ctx('storeCode')` |
| `ctxNum('key')` | Read a runtime variable as number | `ctxNum('discountRate')` |

Important:

- `column()` and `columnNum()` depend on `sortOrder`
- if `TOTAL_AMOUNT` depends on `NET_AMOUNT`, then `NET_AMOUNT` must come first
- `ctx()` works with built-in variables like `businessDate` and with custom variables like `sessionId`, `storeCode`, and `token`

Example:

```json
[
  {
    "sortOrder": 1,
    "mappingType": "SOURCE_PATH",
    "sourcePath": "$.net",
    "pathType": "JSON_PATH",
    "targetHeader": "NET_AMOUNT"
  },
  {
    "sortOrder": 2,
    "mappingType": "SOURCE_PATH",
    "sourcePath": "$.gross",
    "pathType": "JSON_PATH",
    "targetHeader": "GROSS_AMOUNT"
  },
  {
    "sortOrder": 3,
    "mappingType": "EXPRESSION",
    "targetHeader": "TOTAL_AMOUNT",
    "expression": "columnNum('NET_AMOUNT') + columnNum('GROSS_AMOUNT')"
  },
  {
    "sortOrder": 4,
    "mappingType": "EXPRESSION",
    "targetHeader": "STORE_CODE",
    "expression": "ctx('storeCode')"
  }
]
```

## 7. Which Config Field Is Used Where

### `authConfig`

Use this for:

- token generation
- basic auth
- static bearer token
- API key header or query param

Common fields:

- `type`
- `tokenUrl`
- `tokenPath`
- `tokenPathType`
- `headers`
- `bodyTemplate`
- `tokenHeaderName`
- `tokenPrefix`

### `stepConfig`

Use this for:

- actual client API calls
- dynamic URL, headers, query params, body
- multi-step chaining
- single-date or range request behavior
- pagination participation

Common fields:

- `name`
- `method`
- `url`
- `headers`
- `queryParams`
- `bodyTemplate`
- `requestFormat`
- `responseFormat`
- `requestWindowMode`
- `requestDateVariable`
- `requestDateFormat`
- `paginate`
- `dataStep`
- `responseVariables`
- `responseAlias`

### `responseConfig`

Use this for:

- record extraction
- local date filtering
- duplicate handling

Common fields:

- `recordPath`
- `recordPathType`
- `filterByWindow`
- `recordDatePath`
- `recordDatePathType`
- `recordDateFormat`
- `duplicateHandling`

### `fieldMappings`

Use this for:

- final CSV headers
- constants
- computed values
- required fields
- formatting

Supported `formatter` values:

- `UPPERCASE`
- `LOWERCASE`
- `TRIM`
- `DATE:<outputPattern>` for common ISO-style date input
- `DATE:<inputPattern>-><outputPattern>` for custom input date format
- `DATETIME:<outputPattern>` for common ISO-style date-time input
- `DATETIME:<inputPattern>-><outputPattern>` for custom input date-time format

Examples:

```json
{
  "targetHeader": "BILL_DATE",
  "formatter": "DATE:dd-MM-yyyy"
}
```

```json
{
  "targetHeader": "BILL_DATE_TIME",
  "formatter": "DATETIME:yyyyMMddHHmmss->dd/MM/yyyy HH:mm:ss"
}
```

## 8. Sample JSON 1: Simple Range API

Use this when the client supports `fromDate` and `toDate`.

```json
{
  "clientName": "Client Range API",
  "brandCode": "RNG1",
  "baseUrl": "https://client.example.com/api",
  "enabled": false,
  "csvFileName": "range_orders.csv",
  "outputDirectory": "output/range",
  "maxRetries": 2,
  "scheduleConfig": [
    {
      "type": "DAILY",
      "enabled": true
    }
  ],
  "authConfig": {
    "type": "API_KEY_HEADER",
    "headerName": "X-API-KEY",
    "headerValue": "replace-me"
  },
  "responseConfig": {
    "recordPath": "$.data.orders",
    "recordPathType": "JSON_PATH"
  },
  "paginationConfig": {
    "enabled": false
  },
  "storageConfig": {
    "type": "LOCAL",
    "localDirectory": "output/range"
  },
  "stepConfig": [
    {
      "orderIndex": 1,
      "name": "fetch-orders",
      "method": "GET",
      "url": "/orders",
      "queryParams": {
        "fromDate": "${windowStartDate}",
        "toDate": "${windowEndDateExclusive}"
      },
      "requestFormat": "JSON",
      "responseFormat": "JSON",
      "requestWindowMode": "DATE_RANGE",
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
      "mappingType": "SOURCE_PATH",
      "sourcePath": "$.gross",
      "pathType": "JSON_PATH",
      "targetHeader": "GROSS_AMOUNT"
    }
  ]
}
```

## 9. Sample JSON 2: Single-Date API For Monthly Jobs

Use this when the client accepts only one date and monthly should call the API once per day.

```json
{
  "clientName": "Client Single Date API",
  "brandCode": "SD01",
  "baseUrl": "https://client.example.com/api",
  "enabled": false,
  "csvFileName": "single_date_orders.csv",
  "outputDirectory": "output/single-date",
  "maxRetries": 2,
  "scheduleConfig": [
    {
      "type": "MONTHLY",
      "enabled": true
    }
  ],
  "authConfig": {
    "type": "NONE"
  },
  "responseConfig": {
    "recordPath": "$.data.orders",
    "recordPathType": "JSON_PATH",
    "filterByWindow": true,
    "recordDatePath": "$.billDate",
    "recordDatePathType": "JSON_PATH",
    "recordDateFormat": "yyyy-MM-dd"
  },
  "paginationConfig": {
    "enabled": false
  },
  "storageConfig": {
    "type": "LOCAL",
    "localDirectory": "output/single-date"
  },
  "stepConfig": [
    {
      "orderIndex": 1,
      "name": "fetch-by-date",
      "method": "GET",
      "url": "/orders",
      "queryParams": {
        "billDate": "${requestDate}"
      },
      "requestFormat": "JSON",
      "responseFormat": "JSON",
      "requestWindowMode": "SINGLE_DATE",
      "requestDateVariable": "requestDate",
      "requestDateFormat": "yyyy-MM-dd",
      "dataStep": true
    }
  ],
  "fieldMappings": [
    {
      "sortOrder": 1,
      "mappingType": "SOURCE_PATH",
      "sourcePath": "$.billNo",
      "pathType": "JSON_PATH",
      "targetHeader": "BILL_NO",
      "requiredFlag": true
    },
    {
      "sortOrder": 2,
      "mappingType": "SOURCE_PATH",
      "sourcePath": "$.net",
      "pathType": "JSON_PATH",
      "targetHeader": "NET_AMOUNT"
    }
  ]
}
```

## 10. Sample JSON 3: Full Dump + Local Date Filter + Duplicate Merge

Use this when the API returns too much data and duplicate bills must be merged.

```json
{
  "clientName": "Client Full Dump API",
  "brandCode": "FD01",
  "baseUrl": "https://client.example.com/api",
  "enabled": false,
  "csvFileName": "full_dump_orders.csv",
  "outputDirectory": "output/full-dump",
  "maxRetries": 2,
  "scheduleConfig": [
    {
      "type": "DAILY",
      "enabled": true
    }
  ],
  "authConfig": {
    "type": "NONE"
  },
  "responseConfig": {
    "recordPath": "$.data.orders",
    "recordPathType": "JSON_PATH",
    "filterByWindow": true,
    "recordDatePath": "$.billDate",
    "recordDatePathType": "JSON_PATH",
    "recordDateFormat": "yyyy-MM-dd",
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
  },
  "paginationConfig": {
    "enabled": false
  },
  "storageConfig": {
    "type": "LOCAL",
    "localDirectory": "output/full-dump"
  },
  "stepConfig": [
    {
      "orderIndex": 1,
      "name": "fetch-all-orders",
      "method": "GET",
      "url": "/orders/all",
      "requestFormat": "JSON",
      "responseFormat": "JSON",
      "requestWindowMode": "NONE",
      "dataStep": true
    }
  ],
  "fieldMappings": [
    {
      "sortOrder": 1,
      "mappingType": "SOURCE_PATH",
      "sourcePath": "$.billNo",
      "pathType": "JSON_PATH",
      "targetHeader": "BILL_NO",
      "requiredFlag": true
    },
    {
      "sortOrder": 2,
      "mappingType": "SOURCE_PATH",
      "sourcePath": "$.net",
      "pathType": "JSON_PATH",
      "targetHeader": "NET_AMOUNT"
    },
    {
      "sortOrder": 3,
      "mappingType": "SOURCE_PATH",
      "sourcePath": "$.gross",
      "pathType": "JSON_PATH",
      "targetHeader": "GROSS_AMOUNT"
    },
    {
      "sortOrder": 4,
      "mappingType": "EXPRESSION",
      "targetHeader": "TOTAL_AMOUNT",
      "expression": "columnNum('NET_AMOUNT') + columnNum('GROSS_AMOUNT')"
    }
  ]
}
```

## 11. Sample JSON 4: Multi-Step Session + Pagination + Custom Columns

Use this when one API call creates a session and another uses it.

```json
{
  "clientName": "Client Session API",
  "brandCode": "SES1",
  "baseUrl": "https://client.example.com/api",
  "enabled": false,
  "csvFileName": "session_orders.csv",
  "outputDirectory": "output/session-api",
  "maxRetries": 2,
  "scheduleConfig": [
    {
      "type": "DAILY",
      "enabled": true
    }
  ],
  "authConfig": {
    "type": "TOKEN_API",
    "method": "POST",
    "tokenUrl": "https://client.example.com/auth/token",
    "headers": {
      "Content-Type": "application/json"
    },
    "bodyTemplate": "{\"brand\":\"${brandCode}\",\"date\":\"${businessDate}\"}",
    "requestFormat": "JSON",
    "responseFormat": "JSON",
    "tokenPath": "$.access_token",
    "tokenPathType": "JSON_PATH",
    "tokenHeaderName": "Authorization",
    "tokenPrefix": "Bearer "
  },
  "responseConfig": {
    "recordPath": "$.data.orders",
    "recordPathType": "JSON_PATH"
  },
  "paginationConfig": {
    "enabled": true,
    "mode": "PAGE_NUMBER",
    "startPage": 1,
    "pageParam": "page",
    "sizeParam": "pageSize",
    "pageSize": 100,
    "totalPagesPath": "$.meta.totalPages",
    "totalPagesPathType": "JSON_PATH"
  },
  "storageConfig": {
    "type": "LOCAL",
    "localDirectory": "output/session-api"
  },
  "stepConfig": [
    {
      "orderIndex": 1,
      "name": "start-session",
      "method": "POST",
      "url": "/session/start",
      "headers": {
        "X-Client": "${brandCode}"
      },
      "bodyTemplate": "{\"date\":\"${businessDate}\"}",
      "requestFormat": "JSON",
      "responseFormat": "JSON",
      "responseVariables": {
        "$.sessionId": "sessionId",
        "$.storeCode": "storeCode"
      },
      "responseAlias": "session",
      "dataStep": false
    },
    {
      "orderIndex": 2,
      "name": "fetch-orders",
      "method": "GET",
      "url": "/orders",
      "queryParams": {
        "sessionId": "${sessionId}",
        "page": "${page}",
        "pageSize": "${pageSize}"
      },
      "requestFormat": "JSON",
      "responseFormat": "JSON",
      "paginate": true,
      "dataStep": true
    }
  ],
  "fieldMappings": [
    {
      "sortOrder": 1,
      "mappingType": "SOURCE_PATH",
      "sourcePath": "$.billNo",
      "pathType": "JSON_PATH",
      "targetHeader": "BILL_NO",
      "requiredFlag": true
    },
    {
      "sortOrder": 2,
      "mappingType": "CONSTANT",
      "targetHeader": "BRAND_CODE",
      "expression": "SES1"
    },
    {
      "sortOrder": 3,
      "mappingType": "EXPRESSION",
      "targetHeader": "STORE_CODE",
      "expression": "ctx('storeCode')"
    },
    {
      "sortOrder": 4,
      "mappingType": "EXPRESSION",
      "targetHeader": "TOTAL_AMOUNT",
      "expression": "num('$.net') + num('$.gross')"
    }
  ]
}
```

## 12. Sample JSON 5: Manual Custom Run Request

Use this for backfill or rerun.

Date range:

```json
{
  "fromDate": "2026-02-01",
  "toDate": "2026-02-28",
  "fileToken": "backfill_feb_2026"
}
```

Date-time range:

```json
{
  "fromDateTime": "2026-02-14T10:00:00",
  "toDateTime": "2026-02-14T18:00:00",
  "fileToken": "shift_a_backfill"
}
```

API:

```text
POST /api/integrations/{id}/run/custom
```

## 13. Common Mistakes

- Using `${...}` inside `fieldMappings[].expression`
- Using raw API paths inside `duplicateHandling.keyHeaders`
- Setting `responseAlias` to a name that already ends with `Response`
- Expecting `${...}` placeholders to work inside `csvFileName` or `storageConfig`
- Using `SUM` on non-numeric columns
- Forgetting `sortOrder` when one expression depends on another column

## 14. Recommended Reading

- [HOW-TO-USE.md](/d:/Workplace/API-Integration/HOW-TO-USE.md)
- [universal-integration-engine-doc.md](/d:/Workplace/API-Integration/universal-integration-engine-doc.md)
- [StepConfig.java](/d:/Workplace/API-Integration/src/main/java/com/example/integration/model/config/StepConfig.java)
- [ResponseConfig.java](/d:/Workplace/API-Integration/src/main/java/com/example/integration/model/config/ResponseConfig.java)
- [ExecutionContext.java](/d:/Workplace/API-Integration/src/main/java/com/example/integration/model/runtime/ExecutionContext.java)
