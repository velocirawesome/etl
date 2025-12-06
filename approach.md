# DEM Microservice Implementation Approach

## Project Overview
Building a Data Extraction Management (DEM) microservice that implements a complete ETL (Extract, Transform, Load) pipeline for master data using Spring Boot 4.latest and Java 25.

## Technology Stack
- **Framework:** Spring Boot 4.latest
- **Language:** Java 25
- **Build Tool:** Maven
- **Database:** H2 (in-memory)
- **ORM:** Spring Data JPA + Hibernate
- **HTTP Client:** Spring WebClient (WebFlux)
- **JSON Processing:** Jackson + JsonPath + JSON Schema Validator
- **Async:** Spring @Async with ThreadPoolTaskExecutor
- **Logging:** SLF4J + Logback (default)

## Core Architecture

### Project Structure
```
src/main/java/dev/velocirawesome/etl/
├── controller/
│   ├── EtlController.java          (POST /etl/run, GET /etl/status)
│   └── CountryController.java      (GET /country)
├── service/
│   ├── EtlService.java             (Orchestrates ETL pipeline)
│   ├── ExtractionService.java      (E - External API consumption)
│   ├── TransformationService.java  (T - Data transformation logic)
│   └── LoadingService.java         (L - Database persistence)
├── repository/
│   └── CountryRepository.java      (Spring Data JPA)
├── model/
│   ├── entity/
│   │   └── Country.java            (JPA entity)
│   ├── dto/
│   │   ├── EtlStatusDto.java
│   │   └── CountryDto.java
│   └── EtlJobStatus.java           (Status enum)
├── config/
│   ├── AsyncConfig.java            (Thread pool configuration)
│   └── WebClientConfig.java        (HTTP client setup)
├── exception/
│   ├── GlobalExceptionHandler.java
│   └── EtlException.java
└── DemApplication.java
```

## Implementation Plan

### Phase 1: Project Setup & Configuration
1. **Maven Dependencies**
   - Spring Boot Web starter
   - Spring Data JPA
   - H2 Database
   - Spring WebFlux (WebClient)
   - JsonPath & JSON Schema Validator
   - Lombok (optional)
   - Spring Validation starter

2. **Configuration Files**
   - application.yml with H2 datasource and JPA settings
   - AsyncConfig.java for thread pool configuration
   - WebClientConfig.java for HTTP client setup

### Phase 2: Data Model & Persistence Layer
1. **Entity Definition**
   - Create Country JPA entity with all required fields
   - Define proper column mappings and constraints

2. **Repository**
   - Extend Spring Data JPA Repository interface
   - Add custom query methods if needed

3. **Database Initialization**
   - Set up H2 with create-drop strategy for clean slate
   - Verify H2 console access for debugging

### Phase 3: Service Layer - ETL Pipeline

#### 3.1 ExtractionService
- Consume external API endpoint
- Handle HTTP errors (timeouts, connection failures, 4xx, 5xx responses)
- Implement retry logic with exponential backoff
- Log extraction metrics (count, duration, errors)
- Return raw extracted data structure

#### 3.2 TransformationService
- Parse raw JSON data using Jackson
- Validate against expected JSON schema
- Filter out unnecessary fields
- Map extracted fields to Country entity structure
- Handle transformation errors gracefully
- Log transformation metrics (processed count, filtered count, errors)
- Return collection of transformed entities

#### 3.3 LoadingService
- Accept collection of transformed entities
- Batch insert into database using JPA
- Handle transaction rollback on failure
- Implement batch size configuration (e.g., 1000 records)
- Log loading metrics (loaded count, failed count, duration)
- Return loading status

#### 3.4 EtlService (Orchestrator)
- Coordinate extraction, transformation, and loading phases
- Manage overall job status (RUNNING, SUCCESS, FAILED)
- Store current job status in thread-safe manner (AtomicReference or similar)
- Handle exceptions at each stage and mark job as FAILED
- Log key operational metrics throughout pipeline
- Implement @Async to allow non-blocking job execution

### Phase 4: REST API Controllers

#### 4.1 EtlController
- **POST /etl/run**
  - Trigger async ETL job via EtlService
  - Return job ID or confirmation immediately
  - Don't wait for completion (non-blocking)

- **GET /etl/status**
  - Return current job status (RUNNING, SUCCESS, FAILED)
  - Include metadata if available (start time, item count, error details)

#### 4.2 CountryController
- **GET /country**
  - Query all successfully loaded Country records
  - Return as JSON list
  - Consider pagination for large datasets

### Phase 5: Error Handling & Observability

#### 5.1 Exception Handling
- Create custom exception hierarchy (EtlException, ExtractionException, etc.)
- Implement @ControllerAdvice for centralized error responses
- Map exceptions to appropriate HTTP status codes

#### 5.2 Logging Strategy
- INFO level: Job start/end, phase completions, final metrics
- DEBUG level: Field mappings, transformation details
- ERROR level: Exceptions with full stack traces
- Include context (job ID, phase name, record count)

#### 5.3 Metrics
- Extraction: records retrieved, HTTP response time, errors
- Transformation: records processed, records filtered, validation errors
- Loading: records inserted, batch failures, rollbacks

### Phase 6: Testing & Validation
- Unit tests for transformation logic
- Integration tests for ETL pipeline
- Test error scenarios (API failures, malformed data, DB errors)
- Verify transaction rollback behavior
- Load test with various data volumes

## Implementation Order (Priority)

### Must Complete
1. Project setup & Maven dependencies
2. H2 database configuration
3. Country entity & repository
4. ExtractionService (stub with sample data initially)
5. TransformationService (core logic)
6. LoadingService with transaction handling
7. EtlService orchestrator
8. EtlController endpoints
9. CountryController
10. Error handling and logging

### Nice to Have (if time permits)
- Comprehensive exception handling
- Advanced retry logic
- Metrics collection
- Integration tests
- Batch size optimization

## Key Design Decisions

1. **Async Execution:** Use Spring @Async to handle long-running ETL jobs without blocking API response
2. **Job Status Storage:** Use AtomicReference<EtlJobStatus> for thread-safe status management
3. **Transaction Scope:** Keep transactions at LoadingService level for batch isolation
4. **Error Recovery:** Log errors and mark job FAILED, but don't crash service
5. **Code Separation:** Clear separation between Controller, Service, and Repository layers
6. **JSON Processing:** Use Jackson for parsing and JsonPath for dynamic field navigation

## Configuration Template (application.yml)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:etldb
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
    properties:
      hibernate.dialect: org.hibernate.dialect.H2Dialect
  h2:
    console:
      enabled: true
      path: /h2-console

logging:
  level:
    root: WARN
    com.velocirawesome.etl: DEBUG
    org.springframework.web: INFO
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

## Success Criteria

✓ All three endpoints functional and responding correctly
✓ ETL pipeline executes without crashing on error
✓ Data successfully persists to H2 database
✓ Async execution allows /etl/run to return immediately
✓ /etl/status accurately reflects job progress
✓ /country returns all loaded data
✓ Proper logging at key operational points
✓ Transaction rollback working for DB failures
✓ Clean code structure with separation of concerns
