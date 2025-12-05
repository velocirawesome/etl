# Data Model: Initial ETL Shell Application

**Feature**: 001-shell-app | **Date**: 2025-12-05

## Overview

The ETL microservice uses a two-table architecture: a parent **EtlJob** table that tracks all job executions, and dynamically created job-specific **Country** tables that store the results of each ETL run. Job-specific tables use a minimal schema with country code as ID and a JSON column for flexible data storage.

---

## Entities

### 1. EtlJob (Parent Table)

**Purpose**: Tracks the lifecycle and metadata of all ETL job executions. Provides queryable history of job runs and current status.

**Table Name**: `etl_jobs`

**Columns**:

| Column Name         | Type          | Constraints           | Description                                                                 |
|---------------------|---------------|-----------------------|-----------------------------------------------------------------------------|
| `job_id`            | BIGINT        | PRIMARY KEY           | Unique identifier for the job; generated as (MAX(job_id) + 1) in concurrent-safe manner |
| `source_url`        | VARCHAR(2048) | NOT NULL              | URL of the JSON data source submitted by the user                           |
| `status`            | VARCHAR(20)   | NOT NULL              | Job execution status: RUNNING, SUCCESS, FAILED                              |
| `start_time`        | TIMESTAMP     | NOT NULL              | Job start timestamp (UTC)                                                   |
| `end_time`          | TIMESTAMP     | NULL                  | Job completion timestamp (UTC); NULL if still running                       |
| `records_extracted` | INTEGER       | DEFAULT 0             | Count of records fetched from sourceUrl during extraction phase             |
| `records_transformed` | INTEGER     | DEFAULT 0             | Count of records successfully transformed into Country entities             |
| `records_loaded`    | INTEGER       | DEFAULT 0             | Count of records persisted to the job-specific Country table                |
| `error_message`     | TEXT          | NULL                  | Error details if status is FAILED; NULL otherwise                           |

**Job ID Generation** (Concurrent-Safe):
```java
// Within a transaction or synchronized block:
Long maxJobId = dsl.select(DSL.max(ETL_JOBS.JOB_ID))
                   .from(ETL_JOBS)
                   .fetchOne(0, Long.class);
Long newJobId = (maxJobId != null ? maxJobId : 0) + 1;

// Insert new job with computed newJobId
dsl.insertInto(ETL_JOBS)
   .set(ETL_JOBS.JOB_ID, newJobId)
   .set(ETL_JOBS.SOURCE_URL, sourceUrl)
   .set(ETL_JOBS.STATUS, "RUNNING")
   .set(ETL_JOBS.START_TIME, Instant.now())
   .execute();
```

**Validation Rules**:
- `source_url` must be a valid HTTP/HTTPS URL
- `status` must be one of: RUNNING, SUCCESS, FAILED
- `records_extracted >= records_transformed >= records_loaded` (progressive filtering)
- `end_time` must be >= `start_time` when not NULL
- `error_message` should be present when status is FAILED

**State Transitions**:
```
NULL → RUNNING (job created and started)
RUNNING → SUCCESS (all phases completed successfully)
RUNNING → FAILED (any phase encountered unrecoverable error)
```

**Indexes**:
- PRIMARY KEY on `job_id`
- INDEX on `status` for querying running/failed jobs
- INDEX on `start_time DESC` for retrieving latest job

**Java Entity Mapping**:
```java
public class EtlJob {
    private Long jobId;
    private String sourceUrl;
    private JobStatus status;  // enum: RUNNING, SUCCESS, FAILED
    private Instant startTime;
    private Instant endTime;
    private Integer recordsExtracted;
    private Integer recordsTransformed;
    private Integer recordsLoaded;
    private String errorMessage;
}
```

---

### 2. Country (Job-Specific Tables)

**Purpose**: Stores Country records loaded by a specific ETL job. One table per job to ensure isolation. Uses minimal schema with JSON column for flexible data storage.

**Table Name Pattern**: `countries_job_<jobId>` (e.g., `countries_job_1`, `countries_job_42`)

**Columns**:

| Column Name   | Type          | Constraints      | Description                                                       |
|---------------|---------------|------------------|-------------------------------------------------------------------|
| `code`        | VARCHAR(3)    | PRIMARY KEY      | ISO 3166-1 alpha-2 or alpha-3 country code (e.g., "US", "USA")   |
| `data`        | JSON          | NOT NULL         | JSON object containing all country fields that survived transformation (name, population, region, capital, area, etc.) |

**Validation Rules**:
- `code` must be non-empty and 2-3 characters (ISO standard)
- `data` must be valid JSON object
- Transformation phase ensures `code` and `name` (within JSON) are present; filters records missing these fields

**Indexes**:
- PRIMARY KEY on `code` (prevents duplicate countries in same job, enables fast lookups)

**Table Lifecycle**:
- Created dynamically during the Loading phase of an ETL job
- Persists for the lifetime of the EtlJob record
- If a job is re-run with the same jobId (idempotent), the table is dropped and recreated

**Example JSON Data**:
```json
{
  "name": "United States",
  "population": 331002651,
  "region": "Americas",
  "capital": "Washington, D.C.",
  "area": 9833520.5
}
```

**Java Entity Mapping**:
```java
public class Country {
    private String code;          // Primary key
    private JsonNode data;        // Jackson JsonNode for flexible schema

    // Convenience accessors for common fields (optional)
    public String getName() { return data.get("name").asText(); }
    public Long getPopulation() { return data.has("population") ? data.get("population").asLong() : null; }
    // ... etc
}
```

---

## Relationships

**EtlJob → Country (Job-Specific Tables)**:
- **Type**: One-to-Many (logical, not enforced via foreign key)
- **Cardinality**: One EtlJob can produce 0 to N Country records (stored in `countries_job_<jobId>`)
- **Referential Integrity**: Not enforced at the database level due to dynamic table names; managed by application logic
- **Query Pattern**: To retrieve Country records for a job, query `SELECT code, data FROM countries_job_<jobId>`

---

## Data Flow

### Extraction Phase
1. User submits `POST /etl/run` with `sourceUrl`
2. EtlJobRepository computes new jobId = MAX(job_id) + 1 in concurrent-safe manner (within transaction)
3. EtlJob record created with computed jobId, status RUNNING, `start_time` set
4. ExtractionService fetches JSON from `sourceUrl` via HttpServiceProxyFactory
5. `records_extracted` updated with count of JSON objects fetched

### Transformation Phase
1. TransformationService parses JSON array into Country objects
2. Validates required fields (`code`, `name` within JSON)
3. Filters invalid records (logged but not loaded)
4. Extracts `code` as primary key; stores remaining fields in `data` JSON column
5. `records_transformed` updated with count of valid Country objects

### Loading Phase
1. LoadingService creates `countries_job_<jobId>` table using jOOQ DSL
2. Batch inserts valid Country records (`code`, `data` JSON) with transaction
3. `records_loaded` updated with count of persisted records
4. On success: EtlJob status → SUCCESS, `end_time` set
5. On failure: EtlJob status → FAILED, `error_message` set, `end_time` set

---

## Query Patterns

### Get Latest Job Status
```java
// Query most recent EtlJob by start_time DESC
SELECT * FROM etl_jobs ORDER BY start_time DESC LIMIT 1;
```

### Get All Country Records for Latest Successful Job
```java
// 1. Find latest successful job
SELECT job_id FROM etl_jobs WHERE status = 'SUCCESS' ORDER BY start_time DESC LIMIT 1;

// 2. Query job-specific Country table
SELECT code, data FROM countries_job_<jobId>;
```

### Query Specific Country by Code
```java
// Fast lookup using PRIMARY KEY
SELECT code, data FROM countries_job_<jobId> WHERE code = 'US';
```

### Check Job Completion
```java
// Poll status until not RUNNING
SELECT status, records_extracted, records_transformed, records_loaded, error_message
FROM etl_jobs WHERE job_id = ?;
```

---

## Schema Initialization

### H2 DDL (Parent Table)
```sql
CREATE TABLE IF NOT EXISTS etl_jobs (
    job_id BIGINT PRIMARY KEY,
    source_url VARCHAR(2048) NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NULL,
    records_extracted INTEGER DEFAULT 0,
    records_transformed INTEGER DEFAULT 0,
    records_loaded INTEGER DEFAULT 0,
    error_message TEXT NULL
);

CREATE INDEX idx_etl_jobs_status ON etl_jobs(status);
CREATE INDEX idx_etl_jobs_start_time ON etl_jobs(start_time DESC);
```

### H2 DDL (Job-Specific Country Table - Example)
```sql
-- Created dynamically by LoadingService for each job
CREATE TABLE IF NOT EXISTS countries_job_1 (
    code VARCHAR(3) PRIMARY KEY,
    data JSON NOT NULL
);
```

---

## Notes

- **Concurrent-Safe Job IDs**: New job IDs are generated as MAX(job_id) + 1 within a transaction or synchronized block to prevent conflicts
- **Minimal Schema**: Job-specific tables use only two columns (`code`, `data`) for simplicity and flexibility
- **JSON Storage**: H2's JSON column type stores arbitrary country fields that survived transformation (name, population, region, etc.)
- **Job Isolation**: Each ETL job writes to its own Country table, eliminating conflicts between concurrent jobs
- **Fixed Country Code**: Country code is extracted as primary key; remaining fields stored in JSON for flexible querying
- **jOOQ Usage**: Parent EtlJob table uses standard jOOQ code generation; job-specific Country tables use jOOQ's dynamic DSL (`DSL.table()`, `DSL.field()`) with JSON type support
- **H2 In-Memory**: All data persists only for the application lifetime; suitable for demo purposes
- **No Cascade Deletes**: Dropping an EtlJob record does not automatically drop its Country table (manual cleanup or idempotent re-run required)
