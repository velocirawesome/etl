# Feature Specification: Initial ETL Shell Application (Job-per-Run Model)

**Feature Branch**: `001-shell-app`
**Created**: 2025-12-05
**Status**: Draft
**Input**: User description: "Trigger background job to fetch JSON, transform/filter data into Country entities, insert into job-specific table. Parent table tracks job lifecycle (running/success/failed)"

**Data Access Strategy**: jOOQ for dynamic SQL operations + fixed Country entity (see `/orm_layer.md` for rationale)

**Architecture Evolution**: Pivoted from dynamic arbitrary tables to job-per-run model with fixed Country entity. This isolates job results, prevents naming conflicts, and simplifies data model.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Trigger ETL Job with Source URL (Priority: P1)

An interview candidate needs to initiate a Country data extraction and loading process asynchronously. They submit a URL to a JSON source, and the service immediately acknowledges the request, queuing it for background execution.

**Why this priority**: This is the entry point for the entire ETL system. It demonstrates async job handling, a critical technical requirement. Without this, no ETL jobs can run.

**Independent Test**: Can be fully tested by sending `POST /etl/run` with a sourceUrl, verifying immediate response (202/200) and confirming a new EtlJob record is created in the parent table with status RUNNING.

**Acceptance Scenarios**:

1. **Given** the service is running, **When** a candidate sends `POST /etl/run` with `{"sourceUrl": "https://api.example.com/countries"}`, **Then** the service responds immediately (within 1 second) with status 202 or 200, returning a job ID
2. **Given** a job is currently running, **When** another `POST /etl/run` request is submitted, **Then** the service either queues it or rejects it with appropriate status, without crashing

---

### User Story 2 - Monitor Job Status and Progress (Priority: P1)

An interview candidate needs to track the progress of a submitted ETL job without polling indefinitely. They query the status endpoint to see job state transitions (RUNNING → SUCCESS or RUNNING → FAILED) and final counts.

**Why this priority**: Job state visibility is critical for validating the async execution model. Without it, the candidate cannot demonstrate understanding of job orchestration and non-blocking request handling.

**Independent Test**: Can be fully tested by submitting a job and polling `GET /etl/status/{jobId}` multiple times, verifying state transitions and metadata accuracy, independent of data retrieval endpoints.

**Acceptance Scenarios**:

1. **Given** a job has been submitted, **When** the candidate polls `GET /etl/status/{jobId}`, **Then** the service returns job state (RUNNING, SUCCESS, or FAILED) with metadata (start time, records extracted/transformed/loaded)
2. **Given** a job has completed successfully, **When** polled again, **Then** the service returns SUCCESS state consistently with final record counts
3. **Given** a job has failed, **When** polled, **Then** the service returns FAILED state with error message and partial counts (records processed before failure)

---

### User Story 3 - Retrieve Country Data from Successful Job (Priority: P1)

An interview candidate needs to verify that Country data was successfully extracted, transformed, and loaded into the database. They query the job-specific Country table to confirm the ETL pipeline completed correctly.

**Why this priority**: Data retrieval is the primary output validation mechanism. Without successfully loading and querying Country entities, the candidate has not completed the core ETL requirement. This proves the entire pipeline (E-T-L) executed.

**Independent Test**: Can be fully tested by submitting a successful job and calling `GET /country/{jobId}` to retrieve all Country records from that job's table, independent of other jobs' data.

**Acceptance Scenarios**:

1. **Given** an ETL job has completed successfully, **When** the candidate calls `GET /country/{jobId}`, **Then** the service returns a JSON array of all Country records loaded by that job
2. **Given** an ETL job is still RUNNING, **When** the candidate calls `GET /country/{jobId}`, **Then** the service returns 202 Accepted or empty array (partial data not yet available)
3. **Given** an ETL job with 100 Country records has succeeded, **When** called, **Then** all 100 records are returned as JSON objects with schema (code, name, population, region, etc.)

---

### Edge Cases

- What happens if POST /etl/run is called with an invalid or unreachable sourceUrl? (Job status should reflect FAILED with error message; service remains operational)
- What happens if the sourceUrl returns non-JSON data or malformed JSON? (Extraction fails; job status FAILED; no partial loads)
- What happens if JSON contains Country objects with missing required fields? (Transformation filters/skips invalid records; logs discrepancies; loads valid ones; job status SUCCESS with warning)
- What if database table creation for a job fails? (Job status FAILED; transaction isolated; no data corruption)
- What happens with concurrent ETL jobs? (Each job gets isolated result table; no conflicts; all run independently)
- How does GET /country/{jobId} behave for a nonexistent job ID? (Return 404 Not Found)

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST expose `POST /etl/run` endpoint that accepts JSON body with `sourceUrl` and returns immediately with a job ID (async execution)
- **FR-002**: System MUST expose `GET /etl/status/{jobId}` endpoint that returns current job state (RUNNING, SUCCESS, FAILED) with metadata (start time, extracted/transformed/loaded counts, error message if failed)
- **FR-003**: System MUST expose `GET /country/{jobId}` endpoint that returns all Country records loaded by a specific job as JSON array
- **FR-004**: System MUST implement three-phase ETL pipeline: Extraction (fetch JSON from sourceUrl), Transformation (parse and map JSON to Country entities), Loading (persist to job-specific table)
- **FR-005**: System MUST handle Extraction phase by fetching JSON data from sourceUrl via HTTP GET, parsing the response, and counting records fetched
- **FR-006**: System MUST handle Transformation phase by mapping JSON fields to Country entity structure (code, name, population, region, etc.), validating required fields, and filtering invalid records
- **FR-007**: System MUST handle Loading phase by creating a job-specific Country table and persisting valid Country records with transactional integrity
- **FR-008**: System MUST maintain a parent EtlJob table tracking all jobs with columns: jobId (PK), sourceUrl, status (RUNNING/SUCCESS/FAILED), startTime, endTime, recordsExtracted, recordsTransformed, recordsLoaded, errorMessage
- **FR-009**: System MUST log key operational metrics (job start/end, phase transitions, record counts, errors) for observability
- **FR-010**: System MUST maintain thread-safe job status that handles concurrent job submissions without race conditions
- **FR-011**: System MUST implement graceful error handling so extraction/transformation/loading failures do not crash the service
- **FR-012**: System MUST return appropriate HTTP status codes (202 Accepted for async jobs, 200 OK for retrieval, 404 for nonexistent jobs, 5xx for server errors)

### Key Entities

- **EtlJob** (parent table): Tracks all ETL job executions. Columns: jobId (PK, UUID or auto-increment), sourceUrl, status (RUNNING/SUCCESS/FAILED), startTime, endTime, recordsExtracted, recordsTransformed, recordsLoaded, errorMessage. Enables job lifecycle tracking and isolation.

- **Country** (job-specific tables): Represents a country entity loaded by a specific ETL job. Schema: code (ISO country code), name, population, region, and other demographic fields. One table per job (e.g., `countries_job_001`, `countries_job_002`) to isolate results and prevent naming conflicts.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All three endpoints (POST /etl/run, GET /etl/status/{jobId}, GET /country/{jobId}) are functional and return valid responses within the 60-minute session
- **SC-002**: Service remains operational after multiple concurrent ETL job submissions; no crashes due to extraction, transformation, or loading failures
- **SC-003**: EtlJob status accurately reflects pipeline progress and completion (state transitions visible through /etl/status/{jobId} polling)
- **SC-004**: At least 10 Country records are successfully loaded into a job-specific table and retrievable via GET /country/{jobId} for a successful job run
- **SC-005**: Code is organized into logical layers (Controller, Service, Repository) with clear separation of concerns; includes EtlJobService, ExtractionService, TransformationService, LoadingService
- **SC-006**: Logging captures job lifecycle (start, phase transitions, end), record counts at each phase, and errors with context
- **SC-007**: Multiple concurrent jobs can run without table name conflicts (job-specific tables isolate results)
- **SC-008**: Job-specific Country tables are created dynamically per job (e.g., `countries_job_<jobId>`) and persist until cleanup (or until next run with same job ID)

---

## Assumptions

- **Source JSON Format**: The sourceUrl returns a JSON array of country objects (e.g., `[{"code": "US", "name": "United States", ...}, ...]`). Each object is expected to have at least `code` and `name` fields.
- **Country Entity Schema**: Country records have standard fields: code (ISO 3166 2-letter code), name (official country name), population (integer), region (string), and optional demographic fields.
- **Job Isolation**: Each ETL run creates a unique job record and a job-specific Country table. Tables are named deterministically (e.g., `countries_job_<jobId>`). No shared data between jobs.
- **Concurrency Model**: Multiple ETL jobs can run concurrently. Each job is independent with its own status and result table. No job-to-job conflicts.
- **Table Lifecycle**: Job-specific Country tables persist for the lifetime of the EtlJob record. If a job is re-run with the same ID, the previous table is dropped and recreated (idempotent).
- **H2 Database**: In-memory H2 database with create-drop strategy. Parent EtlJob table persists across runs. Job-specific Country tables are created/dropped per job lifecycle.
- **Data Volume**: Demo operates on 10-1000 Country records per job; no optimization for 1M+ records.
- **Error Handling**: Extraction/Transformation/Loading failures are caught, logged, and reflected in EtlJob status. Service remains operational after any failure.
- **Testing Scope**: Manual testing via curl or Postman acceptable; comprehensive test suite not required for 60-min demo.
- **Time Constraint**: Implementation scoped to fit 60-minute session; features prioritized to deliver all three P1 stories.

---

## Notes

- Job-per-run model ensures isolation and eliminates table naming conflicts when running multiple ETL jobs
- Parent EtlJob table provides job lifecycle tracking and enables query of all historical/running jobs
- Country entity is fixed (not dynamic) to align with prep_guide.md and simplify data model
- jOOQ DSL builder enables dynamic job-specific table creation while maintaining type safety
