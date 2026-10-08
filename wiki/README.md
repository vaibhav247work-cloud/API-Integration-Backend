# Integration Engine Wiki

This wiki describes the implemented Spring Boot integration engine. It is kept beside the code so documentation changes are reviewed and released with the feature they describe.

## Contents

- [Architecture](architecture.md)
- [Configuration and operations](operations.md)
- [Storage targets and tenant-default upload](storage.md)

## Purpose

The service calls a client API, extracts records from its response, maps them to CSV, and delivers the file to a selected storage target. Integration definitions, schedules, execution jobs, run diagnostics, and retry state are persisted in MySQL.

For API examples, see `HOW-TO-USE.md`; for every supported environment variable, see `CONFIGURATION-VARIABLE-REFERENCE.md`.
