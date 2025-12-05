# Implementation Tasks: ETL Shell Application

**Feature**: 001-shell-app | **Branch**: `001-shell-app` | **Date**: 2025-12-05

## Overview

This document provides a complete, dependency-ordered task breakdown for implementing the ETL microservice. Tasks are organized into 6 phases following the user story structure, with clear labels for parallelization opportunities and story mapping.

**Total Tasks**: 48
**Estimated Duration**: 60 minutes
**MVP Scope**: Phase 1-3 (US1 only - minimal demo)

---

## Task Format Convention

Each task follows this exact format:
```
- [ ] [TaskID] [P?] [Story?] Description with file path
```

Where:
- **Checkbox**: `- [ ]` (required for all tasks)
- **TaskID**: T001, T002, etc. (sequential, required)
- **[P]**: Present ONLY if task is parallelizable (different files, no dependencies)
- **[Story]**: [US1], [US2], [US3] for user story phases (omitted for setup/foundational/polish)
- **Description**: Clear action + exact file path

---

## Phase 1: Project Setup (5 tasks, ~5 minutes)

**Goal**: Initialize Spring Boot project with all dependencies and basic configuration.

**Parallelization**: T002-T005 can run in parallel after T001 completes.

### Tasks

- [X] T001 Initialize Spring Boot project via start.spring.io with Maven, Java 25, Spring Boot 4.0.0, dependencies: web, jooq, h2, devtools
- [X] T002 [P] Update `/w/functionize/etl/pom.xml` to add Jackson 3 dependency (tools.jackson.core:jackson-databind:3.0.0) and configure spring-boot-maven-plugin
- [X] T003 [P] Create `/w/functionize/etl/src/main/resources/application.yml` with H2 datasource, jOOQ SQLDialect, schema initialization, and logging configuration
- [X] T004 [P] Create package structure: `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/{controller,service,repository,model/{entity,dto},config,exception}`
- [X] T005 [P] Create test package structure: `/w/functionize/etl/src/test/java/dev/velocirawesome/etl/{integration,unit}`

---

## Phase 2: Foundational Infrastructure (7 tasks, ~10 minutes)

**Goal**: Set up shared infrastructure (config, schema, exceptions) that all user stories depend on.

**Parallelization**: T007-T009 can run in parallel after T006 completes.

### Tasks

- [ ] T006 Create `/w/functionize/etl/src/main/resources/schema.sql` with DDL for etl_jobs table (job_id, source_url, status, timestamps, counts, error_message) and indexes
- [ ] T007 [P] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/config/JooqConfig.java` with DSLContext bean configured for H2 SQLDialect
- [ ] T008 [P] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/config/AsyncConfig.java` with @EnableAsync and ThreadPoolTaskExecutor bean (core pool size 2, max 5)
- [ ] T009 [P] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/exception/EtlException.java` as base exception class
- [ ] T010 [P] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/exception/ExtractionException.java` extending EtlException for HTTP fetch/parse errors
- [ ] T011 [P] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/exception/TransformationException.java` extending EtlException for validation/mapping errors
- [ ] T012 [P] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/exception/LoadingException.java` extending EtlException for database errors

---

## Phase 3: User Story 1 - Trigger ETL Job (20 tasks, ~25 minutes)

**Goal**: Implement POST /etl/run endpoint with async pipeline execution (Extract-Transform-Load).

**Acceptance Criteria**:
- POST /etl/run accepts sourceUrl, returns 202 + jobId immediately
- EtlJob record created with RUNNING status
- Async pipeline executes E-T-L phases
- Job status updates after each phase (extracted/transformed/loaded counts)
- Final status SUCCESS or FAILED

**Parallelization**:
- T013-T016 (entities/DTOs) can run in parallel
- T021-T023 (service implementations) can run in parallel after T017-T020 complete

### Tasks

#### Entities and DTOs
- [ ] T013 [P] [US1] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/model/entity/JobStatus.java` enum with RUNNING, SUCCESS, FAILED
- [ ] T014 [P] [US1] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/model/entity/EtlJob.java` with fields: jobId, sourceUrl, status, startTime, endTime, record counts, errorMessage (with getters/setters)
- [ ] T015 [P] [US1] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/model/entity/Country.java` with fields: code (PK), data (JsonNode from tools.jackson.databind)
- [ ] T016 [P] [US1] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/model/dto/EtlRunRequest.java` with sourceUrl field (matching OpenAPI spec)

#### Repositories
- [ ] T017 [US1] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/repository/EtlJobRepository.java` with DSLContext injection
- [ ] T018 [US1] Implement EtlJobRepository.createJob(sourceUrl) with concurrent-safe job ID generation (MAX+1 within @Transactional synchronized method)
- [ ] T019 [US1] Implement EtlJobRepository update methods: updateRecordsExtracted, updateRecordsTransformed, updateRecordsLoaded, updateJobStatus (all @Transactional)
- [ ] T020 [US1] Implement EtlJobRepository.getLatestJob() querying etl_jobs table ordered by start_time DESC LIMIT 1 with Record-to-EtlJob mapping

#### Services (E-T-L)
- [ ] T021 [P] [US1] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/service/ExtractionService.java` with RestClient, fetchData(sourceUrl) method fetching JSON array, parsing with Jackson 3 ObjectMapper (tools.jackson package), logging extracted count, throwing ExtractionException on errors
- [ ] T022 [P] [US1] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/service/TransformationService.java` with transform(List<JsonNode>) method validating required fields (code, name), filtering invalid records, logging transformed count and skipped records
- [ ] T023 [P] [US1] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/service/LoadingService.java` with CountryRepository injection, load(jobId, countries) method with @Transactional, logging loaded count, throwing LoadingException on errors
- [ ] T024 [US1] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/repository/CountryRepository.java` with createJobTable(jobId) creating countries_job_<jobId> table (code VARCHAR(3) PK, data JSON)
- [ ] T025 [US1] Implement CountryRepository.insertCountries(jobId, countries) using jOOQ batch insert with dynamic table name countries_job_<jobId>

#### Orchestrator
- [ ] T026 [US1] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/service/EtlJobService.java` with injected dependencies: EtlJobRepository, ExtractionService, TransformationService, LoadingService
- [ ] T027 [US1] Implement EtlJobService.executePipeline(jobId, sourceUrl) with @Async("taskExecutor") annotation, orchestrating E-T-L phases with try-catch, updating job status/counts after each phase, logging job lifecycle

#### Controller and DTOs
- [ ] T028 [P] [US1] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/model/dto/EtlRunResponse.java` with jobId, status, message fields (matching OpenAPI spec)
- [ ] T029 [P] [US1] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/model/dto/ErrorResponse.java` with error, message fields (matching OpenAPI spec)
- [ ] T030 [US1] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/controller/EtlController.java` with @RestController, @RequestMapping("/etl")
- [ ] T031 [US1] Implement EtlController.runEtlJob(@RequestBody EtlRunRequest) with POST /etl/run mapping, calling jobRepository.createJob() and etlJobService.executePipeline(), returning ResponseEntity.accepted() with EtlRunResponse
- [ ] T032 [US1] Add @ControllerAdvice exception handler for validation errors (400 Bad Request) and internal errors (500 Internal Server Error) returning ErrorResponse

---

## Phase 4: User Story 2 - Monitor Job Status (5 tasks, ~10 minutes)

**Goal**: Implement GET /etl/status endpoint returning latest job state and metadata.

**Dependencies**: US1 must be complete (needs EtlJobRepository and EtlJob entity).

**Acceptance Criteria**:
- GET /etl/status returns latest job with status (RUNNING/SUCCESS/FAILED)
- Response includes timestamps, record counts (extracted/transformed/loaded)
- Returns 404 if no jobs exist

**Parallelization**: T033-T034 (DTO + query method) can run in parallel.

### Tasks

- [ ] T033 [P] [US2] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/model/dto/EtlStatusResponse.java` with all fields from OpenAPI spec (jobId, sourceUrl, status, timestamps, counts, errorMessage)
- [ ] T034 [P] [US2] Verify EtlJobRepository.getLatestJob() query (from T020) returns correct job ordered by start_time DESC
- [ ] T035 [US2] Implement EtlController.getStatus() with GET /etl/status mapping, calling jobRepository.getLatestJob(), mapping EtlJob to EtlStatusResponse
- [ ] T036 [US2] Add mapToStatusResponse(EtlJob) private helper method in EtlController converting entity to DTO with proper null handling for endTime and errorMessage
- [ ] T037 [US2] Handle 404 Not Found case when getLatestJob() returns null in EtlController.getStatus()

---

## Phase 5: User Story 3 - Retrieve Country Data (7 tasks, ~10 minutes)

**Goal**: Implement GET /country endpoint returning countries from latest successful job.

**Dependencies**: US1 must be complete (needs pipeline to load countries). US3 can run in parallel with US2.

**Acceptance Criteria**:
- GET /country returns all Country records from latest successful job
- Queries job-specific table countries_job_<jobId>
- Returns 404 if no successful jobs exist

**Parallelization**: T038-T039 can run in parallel.

### Tasks

- [ ] T038 [P] [US3] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/model/dto/CountryRecord.java` with code, data (JsonNode) fields (matching OpenAPI spec)
- [ ] T039 [P] [US3] Implement EtlJobRepository.getLatestSuccessfulJob() querying etl_jobs WHERE status='SUCCESS' ORDER BY start_time DESC LIMIT 1
- [ ] T040 [US3] Implement CountryRepository.getCountriesByJobId(jobId) querying countries_job_<jobId> table, fetching all records, mapping Record to Country with Jackson ObjectMapper
- [ ] T041 [US3] Add mapToCountry(Record) private helper in CountryRepository extracting code and parsing JSON data column into JsonNode
- [ ] T042 [US3] Create `/w/functionize/etl/src/main/java/dev/velocirawesome/etl/controller/CountryController.java` with @RestController, @RequestMapping("/country")
- [ ] T043 [US3] Implement CountryController.getCountries() with GET /country mapping, calling jobRepository.getLatestSuccessfulJob() and countryRepository.getCountriesByJobId()
- [ ] T044 [US3] Add stream mapping Country to CountryRecord in CountryController.getCountries(), handle 404 when no successful jobs found

---

## Phase 6: Polish & Cross-Cutting (4 tasks, ~10 minutes)

**Goal**: Add integration tests, logging, error handling refinements, and end-to-end validation.

**Dependencies**: All user stories complete.

**Parallelization**: T045-T046 (tests) can run in parallel.

### Tasks

- [ ] T045 [P] Create `/w/functionize/etl/src/test/java/dev/velocirawesome/etl/integration/EtlIntegrationTest.java` with @SpringBootTest, testEtlPipeline_withRealUrl() hitting https://restcountries.com/v3.1/all, polling /etl/status until SUCCESS, verifying /country returns records
- [ ] T046 [P] Create `/w/functionize/etl/src/test/java/dev/velocirawesome/etl/unit/TransformationServiceTest.java` with testTransform_validRecords() and testTransform_filtersInvalidRecords() using sample JSON
- [ ] T047 Add SLF4J logging statements to all services (ExtractionService, TransformationService, LoadingService, EtlJobService) at INFO level for job lifecycle, phase transitions, record counts, and ERROR level for exceptions
- [ ] T048 Run full build with `mvn verify`, test all three endpoints with curl (POST /etl/run, poll GET /etl/status, GET /country), verify 10+ countries loaded successfully

---

## Dependency Graph

### High-Level Phase Dependencies

```
Phase 1 (Setup)
    ↓
Phase 2 (Foundational)
    ↓
Phase 3 (US1: Trigger ETL Job)
    ↓ (blocking for US2)
    ↓ (blocking for US3)
    ├─→ Phase 4 (US2: Monitor Job Status) [can run in parallel with Phase 5]
    └─→ Phase 5 (US3: Retrieve Country Data) [can run in parallel with Phase 4]
    ↓
Phase 6 (Polish & Cross-Cutting)
```

### Detailed Task Dependencies

**Setup Phase (T001-T005)**:
- T001 blocks T002-T005 (need project structure first)
- T002, T003, T004, T005 are parallelizable (independent files)

**Foundational Phase (T006-T012)**:
- T006 blocks T007-T012 (schema must exist before config/exceptions)
- T007, T008, T009 are parallelizable
- T010, T011, T012 depend on T009 (extend EtlException)

**US1 Phase (T013-T032)**:
- Entities/DTOs (T013-T016): parallelizable, no dependencies
- Repositories (T017-T020): T018-T020 depend on T017
- Services E-T-L (T021-T025): T021, T022 parallelizable; T023 depends on T024-T025
- Orchestrator (T026-T027): T027 depends on T017-T025 (needs all repos + services)
- Controller (T028-T032): T028, T029 parallelizable; T030-T032 sequential

**US2 Phase (T033-T037)**:
- T033, T034 parallelizable (DTO + repository method)
- T035-T037 sequential (controller implementation)
- US2 depends on T020 (getLatestJob query from US1)

**US3 Phase (T038-T044)**:
- T038, T039 parallelizable (DTO + repository method)
- T040-T041 sequential (CountryRepository query + mapping)
- T042-T044 sequential (controller implementation)
- US3 depends on T024-T025 (Country table creation from US1)

**Polish Phase (T045-T048)**:
- T045, T046 parallelizable (tests)
- T047 adds logging (touches all services)
- T048 is final validation (depends on all prior tasks)

---

## Parallel Execution Examples

### Phase 1 (After T001 completes)
```bash
# Developer A: pom.xml dependencies
# Developer B: application.yml config
# Developer C: package structure
# Developer D: test structure
```

### Phase 2 (After T006 completes)
```bash
# Developer A: JooqConfig.java
# Developer B: AsyncConfig.java
# Developer C: EtlException.java base class
# Developer D: ExtractionException, TransformationException, LoadingException (after C finishes T009)
```

### Phase 3 US1 - Entities/DTOs (T013-T016)
```bash
# Developer A: JobStatus.java enum
# Developer B: EtlJob.java entity
# Developer C: Country.java entity
# Developer D: EtlRunRequest.java DTO
```

### Phase 3 US1 - Services (After repositories complete)
```bash
# Developer A: ExtractionService.java
# Developer B: TransformationService.java
# Developer C: LoadingService.java + CountryRepository (sequential)
```

### Phase 4 US2 + Phase 5 US3 (Can run in parallel after US1)
```bash
# Team 1 (US2): T033-T037 (GET /etl/status endpoint)
# Team 2 (US3): T038-T044 (GET /country endpoint)
```

### Phase 6 Polish
```bash
# Developer A: EtlIntegrationTest.java
# Developer B: TransformationServiceTest.java
# Developer C: Logging statements (sequential, touches all services)
```

---

## MVP Scope (Minimal Demo - 30 minutes)

For the fastest demo that proves the core concept, implement only:

### MVP Phases (US1 Only)
- **Phase 1**: T001-T005 (Setup)
- **Phase 2**: T006-T012 (Foundational)
- **Phase 3**: T013-T032 (US1: Trigger ETL Job with full pipeline)

### MVP Validation
After Phase 3, manually test:
1. `curl -X POST http://localhost:8080/etl/run -H "Content-Type: application/json" -d '{"sourceUrl":"https://restcountries.com/v3.1/all"}'`
2. Check H2 console at http://localhost:8080/h2-console (verify etl_jobs table has RUNNING → SUCCESS)
3. Verify countries_job_1 table created with 10+ records
4. Check logs show "ETL pipeline completed successfully for job 1"

This MVP proves:
- Async job submission (POST /etl/run returns immediately)
- E-T-L pipeline execution (Extract → Transform → Load)
- Job status tracking (RUNNING → SUCCESS in etl_jobs table)
- Country data persistence (countries_job_<jobId> table created)

**Add-ons** (if time permits):
- **US2** (5 tasks): Adds GET /etl/status endpoint for job monitoring
- **US3** (7 tasks): Adds GET /country endpoint for data retrieval
- **Polish** (4 tasks): Integration tests + comprehensive logging

---

## Testing Strategy

### Unit Tests (Fast, No Network)
- **TransformationServiceTest** (T046): Test JSON → Country mapping, validation, filtering invalid records
- **EtlJobRepositoryTest** (optional): Test concurrent job ID generation (requires @SpringBootTest with in-memory H2)

### Integration Tests (Slow, Real HTTP)
- **EtlIntegrationTest** (T045): End-to-end test with real REST Countries API
  - POST /etl/run → verify 202 response + jobId returned
  - Poll GET /etl/status every 1s until status != RUNNING
  - Verify status == SUCCESS, recordsLoaded > 10
  - GET /country → verify array of Country records returned

### Manual Testing (During Development)
```bash
# Terminal 1: Start application
mvn spring-boot:run

# Terminal 2: Test endpoints
curl -X POST http://localhost:8080/etl/run -H "Content-Type: application/json" -d '{"sourceUrl":"https://restcountries.com/v3.1/all"}'
# Response: {"jobId":1,"status":"RUNNING","message":"ETL job started successfully"}

# Poll status (wait 5-10 seconds for job to complete)
curl http://localhost:8080/etl/status
# Response: {"jobId":1,"status":"SUCCESS","recordsExtracted":250,"recordsTransformed":250,"recordsLoaded":250,...}

# Retrieve countries
curl http://localhost:8080/country | jq '.[0:3]'  # First 3 countries
# Response: [{"code":"US","data":{"name":"United States",...}},...]
```

---

## Time Allocation Summary

| Phase | Tasks | Duration | Description |
|-------|-------|----------|-------------|
| **Phase 1: Setup** | T001-T005 | 5 min | Project initialization, dependencies, config, packages |
| **Phase 2: Foundational** | T006-T012 | 10 min | Schema, jOOQ config, Async config, exception hierarchy |
| **Phase 3: US1** | T013-T032 | 25 min | Entities, repos, E-T-L services, orchestrator, POST /etl/run |
| **Phase 4: US2** | T033-T037 | 10 min | GET /etl/status endpoint, EtlStatusResponse DTO |
| **Phase 5: US3** | T038-T044 | 10 min | GET /country endpoint, CountryRecord DTO, query logic |
| **Phase 6: Polish** | T045-T048 | 10 min | Integration tests, unit tests, logging, validation |
| **Total** | 48 tasks | **60 min** | Complete implementation with all three endpoints |

### Fast-Track MVP (US1 Only)
| Phase | Tasks | Duration |
|-------|-------|----------|
| Phase 1-3 | T001-T032 | 40 min |
| **MVP Total** | 32 tasks | **40 min** |

---

## Success Criteria Mapping

### SC-001: All three endpoints functional
- **T031**: POST /etl/run implemented
- **T035**: GET /etl/status implemented
- **T043**: GET /country implemented

### SC-002: Service remains operational after failures
- **T027**: EtlJobService.executePipeline() with try-catch, updates status to FAILED on exceptions
- **T032**: Global exception handler prevents crashes

### SC-003: EtlJob status accurately reflects pipeline progress
- **T018-T019**: Job status updates after each E-T-L phase
- **T027**: Orchestrator tracks state transitions (RUNNING → SUCCESS/FAILED)

### SC-004: At least 10 Country records loaded and retrievable
- **T025**: CountryRepository.insertCountries() batch insert
- **T040**: CountryRepository.getCountriesByJobId() query
- **T045**: Integration test verifies 10+ records loaded

### SC-005: Code organized into logical layers
- **T004**: Package structure (controller/service/repository)
- **T030**: EtlController (HTTP layer)
- **T026**: EtlJobService (business logic)
- **T017**: EtlJobRepository (data access)

### SC-006: Logging captures job lifecycle
- **T047**: SLF4J logging at INFO/ERROR levels for all phases

### SC-007: Multiple concurrent jobs without conflicts
- **T018**: Concurrent-safe job ID generation (MAX+1 within transaction)
- **T024**: Job-specific table names (countries_job_<jobId>)

### SC-008: Job-specific Country tables created dynamically
- **T024**: CountryRepository.createJobTable(jobId) with dynamic SQL

---

## Notes

### Jackson 3 Migration Reminders
- **Package**: Use `tools.jackson.*` instead of `com.fasterxml.jackson.*`
- **Maven**: `tools.jackson.core:jackson-databind:3.0.0`
- **Imports**: `import tools.jackson.databind.JsonNode;`, `import tools.jackson.databind.ObjectMapper;`

### jOOQ Dynamic SQL Patterns
```java
// Dynamic table name
String tableName = "countries_job_" + jobId;
dsl.selectFrom(DSL.table(tableName))
   .fetch();

// Dynamic field access
dsl.insertInto(DSL.table(tableName))
   .columns(DSL.field("code"), DSL.field("data"))
   .values(code, jsonData)
   .execute();
```

### Concurrent-Safe Job ID Generation
```java
@Transactional
public synchronized Long createJob(String sourceUrl) {
    Long maxJobId = dsl.select(DSL.max(DSL.field("job_id", Long.class)))
                       .from(DSL.table("etl_jobs"))
                       .fetchOne(0, Long.class);
    Long newJobId = (maxJobId != null ? maxJobId : 0) + 1;
    // Insert with newJobId...
}
```

### Async Best Practices
- Mark orchestrator method with `@Async("taskExecutor")`
- Return type should be `void` or `CompletableFuture<T>`
- Ensure `@EnableAsync` is present in config
- Use custom executor to avoid exhausting default thread pool

### Error Handling Strategy
- **Extraction**: Catch HTTP errors, wrap in ExtractionException
- **Transformation**: Validate and filter invalid records, log warnings
- **Loading**: Catch SQL errors, wrap in LoadingException
- **Orchestrator**: Catch all exceptions, update job status to FAILED with error message

---

## Quick Commands Reference

### Build & Run
```bash
mvn compile                    # Recompile (fast)
mvn spring-boot:run            # Start application
mvn test                       # Run all tests
mvn test -Dtest=TestClassName  # Run specific test
mvn verify                     # Full build with tests
```

### H2 Console
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:etldb`
- Username: `sa`
- Password: (blank)

### Useful Queries
```sql
-- Check all jobs
SELECT job_id, status, records_extracted, records_transformed, records_loaded FROM etl_jobs ORDER BY start_time DESC;

-- Check latest job
SELECT * FROM etl_jobs ORDER BY start_time DESC LIMIT 1;

-- Check countries for job 1
SELECT code, data FROM countries_job_1 LIMIT 10;
```

---

**End of Tasks Document**
