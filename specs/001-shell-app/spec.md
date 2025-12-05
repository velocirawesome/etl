# Feature Specification: Initial ETL Shell Application

**Feature Branch**: `001-shell-app`
**Created**: 2025-12-05
**Status**: Draft
**Input**: User description: "implement the initial shell application"

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.
  
  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - Initiate ETL Job Execution (Priority: P1)

An interview candidate needs to trigger the ETL pipeline without waiting for completion. They invoke the ETL job start endpoint and receive immediate confirmation, allowing them to check job status independently.

**Why this priority**: This is the primary entry point for the entire system and demonstrates the candidate's ability to implement async patterns, a core technical requirement. Without this, the system cannot function.

**Independent Test**: Can be fully tested by sending a POST request to /etl/run and verifying immediate 202/200 response with job queued, independent of whether the actual ETL completes.

**Acceptance Scenarios**:

1. **Given** the service is running, **When** the candidate sends `POST /etl/run`, **Then** the service responds immediately (within 1 second) with status 202 or 200, indicating the job has been queued for async execution
2. **Given** a job is running, **When** the candidate sends another `POST /etl/run`, **Then** the service either queues the new request or rejects it with appropriate status, without crashing

---

### User Story 2 - Check Job Status (Priority: P1)

An interview candidate needs to monitor progress without blocking. They poll the status endpoint to check if the ETL pipeline is still running, succeeded, or failed.

**Why this priority**: Status tracking is equally critical to P1 because without it, the candidate cannot demonstrate understanding of job orchestration and state management. Users must be able to verify job progress.

**Independent Test**: Can be fully tested by calling `GET /etl/status` multiple times and verifying state transitions (RUNNING → SUCCESS or RUNNING → FAILED), independent of /country data loading.

**Acceptance Scenarios**:

1. **Given** a job has been submitted, **When** the candidate polls `GET /etl/status`, **Then** the service returns current job state (RUNNING, SUCCESS, or FAILED) with timestamp
2. **Given** a job has succeeded, **When** the candidate polls `GET /etl/status`, **Then** the service returns SUCCESS state consistently on subsequent calls
3. **Given** a job has failed, **When** the candidate polls `GET /etl/status`, **Then** the service returns FAILED state with error details

---

### User Story 3 - Retrieve Loaded Country Data (Priority: P1)

An interview candidate needs to verify that data was successfully transformed and loaded into the database. They retrieve all Country records to confirm the ETL pipeline's Loading phase worked correctly.

**Why this priority**: This is the primary output validation endpoint. Without successfully loading and retrieving data, the candidate has not completed the core requirement of a working ETL pipeline. This is the proof that Transformation and Loading phases executed.

**Independent Test**: Can be fully tested by calling `GET /country` after a successful ETL job and verifying the returned records match expected transformed data structure and count, independent of extraction details.

**Acceptance Scenarios**:

1. **Given** an ETL job has completed successfully, **When** the candidate calls `GET /country`, **Then** the service returns a JSON array of all loaded Country records
2. **Given** no ETL jobs have run, **When** the candidate calls `GET /country`, **Then** the service returns an empty array without error
3. **Given** an ETL job has loaded 100 Country records, **When** the candidate calls `GET /country`, **Then** all 100 records are returned as JSON objects with correct schema (code, name, population, etc.)

---

### Edge Cases

- What happens when POST /etl/run is called while a previous job is still RUNNING? (Rejection or queueing acceptable; document behavior)
- How does the system handle extraction failures (network timeout, malformed API response)? (Job status should reflect FAILED; service remains operational)
- What happens if database insertion fails for a batch of records? (Transaction should be isolated; job status reflects FAILED; no partial data corruption)
- How does GET /country behave if the database is corrupted or H2 connection fails? (Return 500 error with descriptive message; service can attempt restart)

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: System MUST expose `POST /etl/run` endpoint that accepts ETL job submission and returns immediately (async execution)
- **FR-002**: System MUST expose `GET /etl/status` endpoint that returns current job state (RUNNING, SUCCESS, or FAILED) with metadata
- **FR-003**: System MUST expose `GET /country` endpoint that returns all successfully loaded Country records as JSON array
- **FR-004**: System MUST implement three-phase ETL pipeline: Extraction (consume external API), Transformation (parse/validate/map JSON to Country), Loading (persist to database)
- **FR-005**: System MUST handle Extraction phase by consuming a configurable external API endpoint and parsing the JSON response
- **FR-006**: System MUST handle Transformation phase by validating JSON schema, filtering unnecessary fields, and mapping to Country entity structure
- **FR-007**: System MUST handle Loading phase by persisting transformed Country records to H2 in-memory database with transaction support
- **FR-008**: System MUST log key operational metrics at each ETL phase (records extracted, transformed, filtered, loaded) and any errors encountered
- **FR-009**: System MUST maintain thread-safe job status that survives concurrent API calls without race conditions
- **FR-010**: System MUST implement graceful error handling so extraction/transformation/loading failures do not crash the service

### Key Entities

- **Country**: Represents a country record with attributes like code (ISO code), name, population, and other demographic fields. Loaded from external API, transformed to canonical schema, and persisted in H2 database. Retrieved via GET /country endpoint.
- **EtlJobStatus**: Represents the current state of an ETL job (RUNNING, SUCCESS, FAILED) with metadata like start timestamp, records processed, and error message if applicable. Queried by GET /etl/status.

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: All three endpoints (POST /etl/run, GET /etl/status, GET /country) are functional and return valid responses within the 60-minute session
- **SC-002**: Service remains operational after multiple ETL job submissions and does not crash due to extraction, transformation, or loading failures
- **SC-003**: Job status accurately reflects pipeline progress and completion (transitions visible through /etl/status polling)
- **SC-004**: At least 10 Country records are successfully loaded and retrievable via GET /country after a successful ETL run
- **SC-005**: Code is organized into logical layers (Controller, Service, Repository) with clear separation of concerns
- **SC-006**: Logging captures at least job start/end timestamps, phase transition points, and record counts at each phase

## Assumptions

- **External API**: An external API endpoint is available for extraction (mocked or real). The candidate or interviewer will provide the endpoint URL and response schema.
- **H2 Database**: H2 in-memory database is configured with create-drop strategy; no manual schema setup required between runs.
- **Concurrency Model**: Only one ETL job may run at a time. Concurrent submissions during a running job are rejected or queued (rejection is acceptable for demo scope).
- **Data Volume**: Demo operates on manageable data volumes (10-1000 records), not large-scale datasets; no optimization for 1M+ records required.
- **Testing Scope**: Manual testing via curl or Postman is acceptable; comprehensive automated test suite is not required for this demo.
- **Time Constraint**: Implementation is scoped to fit within a 60-minute interview; features are prioritized to deliver P1 stories first.
