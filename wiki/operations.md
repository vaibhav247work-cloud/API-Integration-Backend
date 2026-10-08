# Configuration and operations

## Start locally

```bash
mvn spring-boot:run
```

The default HTTP port is `8080`. MySQL configuration, runtime roles, timeouts, logging, CORS, upload URL templates, and tenant database location are configured through environment variables documented in `CONFIGURATION-VARIABLE-REFERENCE.md` and `example.env`.

The application does not automatically load a `.env` file when started directly by Maven or an IDE. Set those environment variables in the run configuration, or use a shell/container configuration that loads the file.

## Operational endpoints

| Endpoint | Purpose |
| --- | --- |
| `GET /api/integrations` | List definitions. |
| `POST /api/integrations` | Create a definition. |
| `PUT /api/integrations/{id}` | Update a definition. |
| `PATCH /api/integrations/{id}/enabled?value=true` | Enable or disable scheduling. |
| `POST /api/integrations/{id}/run` | Run synchronously now. |
| `POST /api/integrations/{id}/enqueue` | Create a queue job. |
| `GET /api/jobs` | Inspect execution jobs. |
| `GET /api/runs` | Inspect run history. |
| `GET /api/failures` | Inspect retry queue. |
| `POST /api/failures/{id}/retry` | Retry a failed queue item. |
| `/actuator/health` | Health endpoint. |

Swagger and OpenAPI are disabled by default and controlled through `SPRINGDOC_*` environment variables.

## Failure diagnosis

The run history stores a failure category, failed step name, client URL, HTTP status when available, and error message. Start with the category:

| Category | Typical cause | First action |
| --- | --- | --- |
| `AUTHENTICATION_ERROR` | Client returned 401/403 or auth configuration is incorrect. | Verify headers, token extraction path, credentials, and request URL. |
| `CONFIGURATION_ERROR` | Invalid enum/configuration or missing tenant/storage fields. | Validate the saved definition and server-side environment configuration. |
| `RESPONSE_PARSING_ERROR` | Record path, payload format, or date format does not match the response. | Inspect a real response and correct JSONPath/XPath or date pattern. |
| Storage error | Remote FTP/FTPS/SFTP/API target rejected or could not receive the CSV. | Verify protocol, host/port, credentials, target directory, certificates, and network access. |

## Audit notes

The project currently has meaningful automated coverage for mapping/parsing, scheduling, queue execution, HTTP storage, and orchestrator failure handling. External systems (client APIs, S3, FTP/SFTP/FTPS servers, and upload APIs) must still be validated in the deployment network with non-production test data.

Avoid adding credentials to integration definitions, documentation, issue tickets, or Git history. Use environment variables, mounted secret files, and the ignored tenant database file instead.
