<!--
  SYNC IMPACT REPORT (v1.0.0 - Initial Constitution)
  ====================================================
  Version change: [TEMPLATE] → 1.0.0 (Initial adoption)

  Modified principles: N/A (first constitution)
  Added sections:
    - Core Principles (5 principles specific to DEM microservice)
    - Technology Stack Requirements
    - Development Workflow
    - Governance (amendment procedure, compliance review)

  Templates flagged for review:
    ✅ spec-template.md: No updates needed (generic template)
    ✅ plan-template.md: No updates needed (generic template)
    ✅ tasks-template.md: No updates needed (generic template)
    ✅ Existing docs: prep_guide.md and approach.md remain authoritative for feature details

  Deferred items: None - all placeholders filled

  Suggested commit message:
    docs: establish constitution v1.0.0 for ETL demo microservice
-->

# DEM Microservice Constitution

## Core Principles

### I. Interview Demo - Pragmatic Over Perfect

This is a one-off demo application for an interview exercise, not a production system. Decisions prioritize demonstrating core competencies (ETL pipeline implementation, clean code, error handling) over enterprise-grade robustness.

**Non-negotiable**:
- All three endpoints MUST be functional (POST /etl/run, GET /etl/status, GET /country)
- ETL pipeline MUST execute without crashing, even on errors
- Code structure MUST separate concerns (Controller/Service/Repository)
- Core transformation and persistence logic MUST work correctly

**Acceptable pragmatism**:
- Start with in-memory status tracking; if time permits, add persistence
- Skip comprehensive integration test suite; focus on key happy-path validation
- Use existing Spring Boot starters rather than hand-rolled solutions
- Minimal but clear logging rather than comprehensive observability framework

### II. ETL Pipeline First - Extract, Transform, Load in Isolation

Each phase of the ETL pipeline (Extraction, Transformation, Loading) must be independently testable and loggable. Failures in one phase MUST not crash the service; instead, job status reflects failure state clearly.

**Non-negotiable**:
- **Extraction**: Consume external sourceUrl robustly; fetch JSON array of Country objects; log count and any HTTP errors encountered
- **Transformation**: Parse JSON and map to Country entity structure (code, name, population, region, etc.); validate required fields (code, name); filter/skip invalid records; log processed/transformed/filtered counts
- **Loading**: Create job-specific Country table (e.g., countries_job_<jobId>); persist valid Country records with transactional integrity; log insertion counts and rollback events
- Each phase reports success/failure to the orchestrator (EtlJobService); parent EtlJob table updated with status and counts

**Implementation patterns**:
- Each service (ExtractionService, TransformationService, LoadingService) returns a result object or throws a domain exception
- EtlService orchestrator catches exceptions, logs them, and updates overall job status to FAILED
- No uncaught exceptions escape to the HTTP layer

### III. Clean Layered Architecture - Controller → Service → Repository

Code organization follows three tiers to ensure maintainability and testability even under time pressure.

**Non-negotiable**:
- **Controller Layer** (EtlController, CountryController): HTTP request/response mapping, status code selection, no business logic; routes to EtlJobService and job-specific Country queries
- **Service Layer** (EtlJobService, ExtractionService, TransformationService, LoadingService): All business logic, orchestration, error handling; Country entity mapping in TransformationService
- **Data Access Layer** (jOOQ DSLContext): Job-specific Country table creation (countries_job_<jobId>), parent EtlJob table CRUD, runtime SQL generation, parameterized queries

**Acceptable shortcuts**:
- DTOs can be simple (no elaborate mapping frameworks needed; manual mapping is fine for small datasets)
- Exception hierarchy can be minimal (one base EtlException + a few specific types is sufficient)
- Validators can be inline (Jackson annotations + basic checks) rather than a separate validation framework

### IV. Async Execution - Non-Blocking Job Submission

POST /etl/run MUST return immediately without waiting for the ETL pipeline to complete. The service uses Spring @Async to execute the pipeline in the background.

**Non-negotiable**:
- /etl/run returns 202 Accepted (or 200 OK) immediately after queuing the job
- GET /etl/status returns current job state (RUNNING, SUCCESS, or FAILED)
- Only one job may run at a time; concurrent POST /etl/run calls queue or reject (rejection acceptable for demo)
- Job status is thread-safe (use AtomicReference or synchronized wrapper)

**Implementation patterns**:
- EtlService.executePipeline() marked @Async
- Status tracked in AtomicReference<EtlJobStatus> in EtlService
- HTTP layer polls this reference; no blocking waits

### V. Observability via Structured Logging - Key Metrics at Each Stage

Logging is the primary observability tool for this demo. At minimum, the service MUST log job start/end, phase transitions, counts, and errors.

**Non-negotiable**:
- Log when ETL job starts and ends (with timestamp)
- Log counts: records extracted, transformed, filtered, loaded
- Log any errors with context (phase name, exception message, record count at failure point)
- Use SLF4J with Logback; log to console and optionally a file

**Logging levels**:
- **INFO**: Job lifecycle (start, phase complete, end), final metrics
- **WARN**: Recoverable errors or filtered records
- **ERROR**: Exceptions that cause job failure
- **DEBUG**: Field mappings, transformation details (optional, can be disabled to reduce noise)

**Metrics to track**:
- Extraction: HTTP response time, records fetched, HTTP errors
- Transformation: records parsed, records filtered, validation errors
- Loading: records inserted, batch failures, rollback count (if applicable)

## Technology Stack Requirements

**Fixed Stack** (per approach.md):
- **Language**: Java 25
- **Framework**: Spring Boot 4.latest
- **Build Tool**: Maven
- **Database**: H2 (in-memory)
- **Data Access**: jOOQ (dynamic SQL builder for runtime table operations)
- **HTTP Client**: Spring WebClient (WebFlux)
- **JSON Processing**: Jackson 3 (JsonMapper) + JsonPath + JSON Schema Validator
- **Async**: Spring @Async with ThreadPoolTaskExecutor (default or custom config)
- **Logging**: SLF4J + Logback

**Rationale**:
- Spring Boot provides rapid scaffolding and sensible defaults for a 60-minute demo
- H2 in-memory eliminates external database setup
- **jOOQ** (not JPA) because:
  - Dynamic table creation at runtime (user-supplied table names)
  - Type-safe SQL DSL with no boilerplate (faster than JPA entity generation or raw JDBC)
  - Excellent JSON column handling (id + JSON data schema)
  - Built-in SQL injection prevention (parameterized queries)
  - Modern fluent API demonstrates current Java practices
  - ~20 min implementation time vs. 30-40 min for JPA or raw JDBC
- WebClient (non-blocking) prepares for async extraction if needed
- Jackson is standard for JSON and integrates seamlessly
- See `orm_layer.md` for detailed comparative analysis and implementation guidance

## Development Workflow

**Before the interview session**:
1. **Project skeleton** initialized: Maven POM with all dependencies declared (Spring Web, jOOQ, H2, WebClient, Jackson 3, etc.)
2. **Database configuration** ready: application.yml with H2 datasource; JooqConfig bean set up to provide DSLContext with SQLDialect.H2
3. **Package structure** created (empty): controller/, service/, repository/, model/entity, model/dto, config/, exception/
4. **IDE verified**: IDE is functional, language SDK/JDK is configured, can run and debug immediately

**During the interview session**:
- **Live coding in IDE**: Use IDE's refactoring, code generation, and debugging tools freely
- **Think out loud**: Explain design decisions before implementation (why three services, why async, etc.)
- **Prioritize iteratively**: Complete P1 (core ETL pipeline) → validate → then P2 (error handling) → etc.
- **Leverage AI assistant**: Use Claude and other tools to accelerate routine tasks (boilerplate, dependency wiring)
- **Communicate blockers**: If stuck, explain what you're trying to achieve and what's not working; adjustments are expected

**Post-implementation**:
- Quick manual test: curl POST /etl/run → poll /etl/status → curl /country and verify records
- Code review for clarity: readable variable names, comments on non-obvious logic, no dead code

## Governance

**Constitution Compliance**:
- All code changes MUST adhere to Core Principles I-V above
- During code review (real or self-review), check:
  1. Are all three endpoints implemented and tested?
  2. Does the ETL pipeline follow three phases (E-T-L) with clear separation?
  3. Does error handling prevent crashes and log failures?
  4. Is the code organized in Controller → Service → Repository layers?
  5. Does async execution work and status tracking function correctly?
  6. Is logging present at job lifecycle milestones and for key counts?

**Amendment Procedure**:
- Amendments are unlikely during a single interview session
- If new requirements emerge during the session (e.g., "add pagination to /country"), discuss trade-offs aloud and update this document informally
- After the session, if lessons learned warrant updating this constitution, follow semantic versioning:
  - **MAJOR**: Remove a core principle or backward-incompatibly redefine one (e.g., switch from async to sync)
  - **MINOR**: Add a new principle or expand existing guidance (e.g., add specific performance targets)
  - **PATCH**: Clarify wording, fix typos, non-semantic refinements

**Version Semantics**:
- MAJOR.MINOR.PATCH format (e.g., 1.0.0)
- Bump MINOR after each session if features were adjusted or principles refined
- Bump PATCH for documentation clarifications
- MAJOR bumps reserved for fundamental architectural shifts

**Compliance Review**:
- After implementing each major component, verify it against principles (especially Principles II and III)
- Use this constitution as a checklist during code review
- No code should violate a "Non-negotiable" rule

---

**Version**: 1.0.0 | **Ratified**: 2025-12-05 | **Last Amended**: 2025-12-05
