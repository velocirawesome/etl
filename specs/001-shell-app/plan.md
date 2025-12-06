# Implementation Plan: Initial ETL Shell Application (Job-per-Run Model)

**Branch**: `001-shell-app` | **Date**: 2025-12-05 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-shell-app/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Build a Spring Boot microservice that implements an asynchronous ETL pipeline for Country data. The service exposes three REST endpoints (POST /etl/run, GET /etl/status, GET /country) to trigger background jobs, monitor the latest job status, and retrieve loaded Country records from the most recent successful job. Each ETL job fetches JSON from a user-provided sourceUrl, transforms it into Country entities, and loads it into a job-specific database table. Uses jOOQ for dynamic SQL operations, Spring @Async for non-blocking execution, HttpServiceProxyFactory for HTTP client calls, and H2 for in-memory persistence. Architecture follows Controller → Service → Repository pattern with separate services for Extraction, Transformation, and Loading phases.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 7.latest, jOOQ, H2, Spring HttpServiceProxyFactory (declarative HTTP interface), Jackson 3 (JsonMapper + JsonPath + JSON Schema Validator)
**Storage**: H2 in-memory database (parent EtlJob table + job-specific Country tables, e.g., countries_job_<jobId>)
**Testing**: JUnit 5 + Spring Boot Test; integration tests hitting real URLs as primary validation; unit tests for isolated transformation/validation logic; @SpringBootTest for end-to-end endpoint verification
**Target Platform**: Linux server (JVM-based microservice)
**Project Type**: Single backend microservice (web API)
**Performance Goals**: Handle 10-1000 Country records per job; respond to /etl/run within 1 second; concurrent job submissions without crashes
**Constraints**: 60-minute implementation time; async execution required (non-blocking /etl/run); service must remain operational after extraction/transformation/loading failures; endpoints implicitly operate on latest job; avoid reactive types
**Scale/Scope**: Demo-grade single microservice; 3 REST endpoints; 4-5 service classes; 2 database tables (1 parent + job-specific); no UI

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Core Principle I: Interview Demo - Pragmatic Over Perfect
✅ **PASS**: All three endpoints (POST /etl/run, GET /etl/status, GET /country) are in scope and will be functional. ETL pipeline execution without crashes is a core requirement. Controller/Service/Repository separation is planned. Focus is on demonstrating competencies within 60-minute constraint.

### Core Principle II: ETL Pipeline First - Extract, Transform, Load in Isolation
✅ **PASS**: Feature spec explicitly defines three phases (FR-004, FR-005, FR-006, FR-007). Each phase has dedicated service (ExtractionService, TransformationService, LoadingService). Orchestrator (EtlJobService) handles phase coordination. Logging and error handling at each phase boundary.

### Core Principle III: Clean Layered Architecture - Controller → Service → Repository
✅ **PASS**: Architecture section commits to three tiers: EtlController and CountryController (HTTP layer), service layer with 4 services, jOOQ DSLContext for data access. No business logic in controllers.

### Core Principle IV: Async Execution - Non-Blocking Job Submission
✅ **PASS**: FR-001 requires immediate return from /etl/run with async execution. Spring @Async pattern planned. Status tracking via GET /etl/status. Thread-safe job status required (FR-010).

### Core Principle V: Observability via Structured Logging - Key Metrics at Each Stage
✅ **PASS**: FR-009 mandates logging of job lifecycle, phase transitions, record counts, and errors. SLF4J + Logback specified. Constitution defines INFO/WARN/ERROR/DEBUG levels with concrete metrics to track.

### Technology Stack Compliance
✅ **PASS**: Java 25, Spring Boot 7, Maven, H2, jOOQ, HttpServiceProxyFactory (avoiding reactive types), Jackson 3, Spring @Async, SLF4J + Logback all align with constitution and approach.md requirements.

**Overall Status**: ✅ All gates PASS. No violations. Ready to proceed to Phase 0.

---

## Post-Phase 1 Constitution Re-Check

*Re-evaluated after completing Phase 1 design (data-model.md, contracts, quickstart.md)*

### Core Principle I: Interview Demo - Pragmatic Over Perfect
✅ **PASS**: All three endpoints fully specified in [openapi.yaml](contracts/openapi.yaml). Implementation guide in [quickstart.md](quickstart.md) demonstrates clear separation of concerns with concrete code examples. 60-minute time allocation documented.

### Core Principle II: ETL Pipeline First - Extract, Transform, Load in Isolation
✅ **PASS**: Each phase has dedicated service class with clear responsibilities (ExtractionService, TransformationService, LoadingService). Error handling boundaries defined. Logging at each phase documented in quickstart.

### Core Principle III: Clean Layered Architecture - Controller → Service → Repository
✅ **PASS**: Project structure in plan.md shows clear package separation. Quickstart provides concrete implementation of each layer with jOOQ repositories, service orchestration, and REST controllers.

### Core Principle IV: Async Execution - Non-Blocking Job Submission
✅ **PASS**: AsyncConfig and @Async implementation documented in quickstart. Thread-safe job ID generation using MAX(job_id) + 1 within transaction (data-model.md). Status tracking via getLatestJob() query.

### Core Principle V: Observability via Structured Logging - Key Metrics at Each Stage
✅ **PASS**: Quickstart includes SLF4J logging at all critical points (job start, extraction count, transformation count, loading count, errors). Log statements follow INFO/WARN/ERROR convention from constitution.

### Technology Stack Compliance
✅ **PASS**: Quickstart confirms Java 25, Spring Boot 4.0.0, jOOQ, H2, HttpServiceProxyFactory (RestClient), Jackson 3 (tools.jackson package), Spring @Async. All align with constitution requirements.

**Post-Phase 1 Status**: ✅ All gates still PASS. Design artifacts complete and compliant. Ready for Phase 2 (tasks generation via /speckit.tasks).

## Project Structure

### Documentation (this feature)

```text
specs/001-shell-app/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── openapi.yaml     # REST API specification
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/main/java/dev/velocirawesome/etl/
├── controller/
│   ├── EtlController.java          # POST /etl/run, GET /etl/status endpoints
│   └── CountryController.java      # GET /country endpoint
├── service/
│   ├── EtlJobService.java          # Orchestrator: async job execution, status tracking
│   ├── ExtractionService.java      # Phase 1: Fetch JSON from sourceUrl
│   ├── TransformationService.java  # Phase 2: Parse JSON → Country entities, validation
│   └── LoadingService.java         # Phase 3: Create job table, persist records
├── repository/
│   ├── EtlJobRepository.java       # jOOQ queries for parent EtlJob table
│   └── CountryRepository.java      # jOOQ queries for job-specific Country tables
├── model/
│   ├── entity/
│   │   ├── EtlJob.java             # Parent job record (jobId, status, counts, etc.)
│   │   └── Country.java            # Country entity (code, name, population, region)
│   └── dto/
│       ├── EtlRunRequest.java      # POST /etl/run request body
│       ├── EtlStatusResponse.java  # GET /etl/status response
│       └── CountryResponse.java    # GET /country response (array of Country)
├── config/
│   ├── JooqConfig.java             # DSLContext bean, H2 SQLDialect
│   └── AsyncConfig.java            # @EnableAsync, ThreadPoolTaskExecutor
└── exception/
    ├── EtlException.java           # Base exception
    ├── ExtractionException.java    # HTTP fetch/parse errors
    ├── TransformationException.java# Validation/mapping errors
    └── LoadingException.java       # Database errors

src/main/resources/
├── application.yml                 # H2 datasource, jOOQ config, logging
└── logback.xml                     # SLF4J logging configuration

src/test/java/dev/velocirawesome/etl/
├── integration/
│   └── EtlIntegrationTest.java     # @SpringBootTest, end-to-end tests with real URLs
└── unit/
    ├── TransformationServiceTest.java  # Unit tests for JSON → Country mapping
    └── EtlJobServiceTest.java          # Unit tests for orchestration logic

pom.xml                             # Maven dependencies: Spring Boot, jOOQ, H2, Jackson 3, JUnit 5
```

**Structure Decision**: Single backend microservice (Option 1). Standard Spring Boot package-by-layer structure with controller/service/repository separation. All source code under `src/main/java/dev/velocirawesome/etl/`. Tests organized as integration (primary) and unit tests. No frontend or mobile components.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations detected. This section is empty.

---

## Development Iteration Strategy

To meet the 60-minute implementation target, use fast Maven iteration patterns:

### Build Commands (Ordered by Speed)

| Scenario | Command | Use When |
|----------|---------|----------|
| **Quick recompile** | `mvn compile` | Making code changes, want fast feedback |
| **Run tests only** | `mvn test` | Validating logic without building |
| **Single test class** | `mvn test -Dtest=TestClassName` | Debugging a specific component |
| **Single test method** | `mvn test -Dtest=TestClassName#methodName` | Testing one scenario |
| **Tests matching pattern** | `mvn test -Dtest=*IntegrationTest` | Running all integration tests |
| **Skip tests + compile** | `mvn compile -DskipTests` | Confident in code, want to build fast |
| **Full validation** | `mvn verify` | Before committing (runs all tests + plugins) |
| **Clean if stuck** | `mvn clean compile` | Build cache corrupted or import issues |

### Typical Development Cycle

**First time setup**:
```bash
mvn compile              # Initial compile, download dependencies
mvn spring-boot:run      # Start application
```

**Iterative development** (repeat while coding):
```bash
# Edit code in IDE...
mvn compile              # Recompile (no clean, no test, fast)
# Stop and restart the app (Ctrl+C, then):
mvn spring-boot:run      # Restart with new code
# Test with curl
```

**Before git commit**:
```bash
mvn verify               # Run all tests and validation
# If all pass, commit
```

**If you hit compilation errors**:
```bash
mvn clean compile        # Clear build cache and retry
# OR check imports in IDE
```

### Why This Approach

- **`mvn compile`**: ~2-5 seconds (only compiles Java files)
- **`mvn clean compile`**: ~5-10 seconds (clears cache first)
- **`mvn clean install`**: ~30-60 seconds (full build + tests + install)
- **`mvn verify`**: ~20-40 seconds (all tests + plugins)

For a 60-minute session, avoid `clean install`. Use `compile` and individual test runs to iterate fast.
