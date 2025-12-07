# ETL Microservice

An asynchronous Extract-Transform-Load (ETL) microservice for processing country data from REST APIs. Built with Spring Boot 4.0, Java 25, jOOQ, H2, and Jackson 3.

## Features

- **Async Pipeline Execution**: Non-blocking ETL operations with 202 Accepted responses and configurable delays for testing
- **Dynamic Job Tracking**: Each job gets a unique ID with status monitoring
- **Job-Specific Data Tables**: Countries stored in isolation per job (countries_job_<jobId>)
- **Type-Safe SQL**: jOOQ-generated code for compile-time SQL verification and type safety
- **Transactional Loading**: ACID-compliant data loading with field projection
- **Comprehensive Logging**: SLF4J logging at INFO/ERROR levels for job lifecycle
- **Centralized Exception Handling**: @RestControllerAdvice for consistent error responses
- **Jackson 3 Integration**: Modern JSON processing with JsonNode support

## Quick Start

### Prerequisites

- Java 25+
- Maven 3.8+

### Build

```bash
mvn clean install
```

### Run

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

### H2 Console (Optional)

Access the H2 console for database inspection:
- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:etldb`
- Username: `sa`
- Password: (leave blank)

## API Endpoints

### 1. Trigger ETL Job

**POST /etl/run**

Submit a URL for ETL processing. Returns immediately with job ID. Optional `delayMs` parameter simulates async work for testing.

```bash
curl -X POST http://localhost:8080/etl/run \
  -H "Content-Type: application/json" \
  -d '{"sourceUrl":"https://restcountries.com/v3.1/all?fields=name,cca2,cca3,capital,region,population,area,languages,currencies,flag"}'

# With delay for testing parallel jobs
curl -X POST http://localhost:8080/etl/run \
  -H "Content-Type: application/json" \
  -d '{"sourceUrl":"https://restcountries.com/v3.1/all?fields=name,cca2,cca3,capital,region,population,area,languages,currencies,flag","delayMs":5000}'
```

**Response (202 Accepted):**
```json
{
  "jobId": 1,
  "status": "RUNNING",
  "message": "ETL job started asynchronously"
}
```

### 2. Monitor Job Status

**GET /etl/status**

Check the status of the latest job. Includes extracted, transformed, and loaded record counts.

```bash
curl http://localhost:8080/etl/status
```

**Response (200 OK):**
```json
{
  "jobId": 1,
  "sourceUrl": "https://restcountries.com/v3.1/all",
  "status": "SUCCESS",
  "startTime": "2025-12-06T14:30:00",
  "endTime": "2025-12-06T14:30:15",
  "recordsExtracted": 250,
  "recordsTransformed": 250,
  "recordsLoaded": 250,
  "errorMessage": null
}
```

**Response (404 Not Found):**
```json
{
  "error": "NOT_FOUND",
  "message": "No ETL jobs found"
}
```

### 3. Retrieve Country Data

**GET /country**

Fetch all countries from the latest successful job.

```bash
curl http://localhost:8080/country | jq '.[0:3]'
```

**Response (200 OK):**
```json
[
  {
    "code": "AFG",
    "data": {
      "name": {
        "common": "Afghanistan",
        "official": "Islamic Emirate of Afghanistan"
      },
      "capital": ["Kabul"],
      "region": "Asia"
    }
  },
  {
    "code": "ALA",
    "data": {
      "name": {
        "common": "Åland Islands",
        "official": "Åland Islands"
      },
      "capital": ["Mariehamn"],
      "region": "Europe"
    }
  },
  {
    "code": "ALB",
    "data": {
      "name": {
        "common": "Albania",
        "official": "Republic of Albania"
      },
      "capital": ["Tirana"],
      "region": "Europe"
    }
  }
]
```

**Response (404 Not Found):**
```json
{
  "error": "NOT_FOUND",
  "message": "No successful ETL jobs found"
}
```

## Happy Path Workflow

Complete ETL workflow from start to finish:

**1. Start the application:**
```bash
mvn spring-boot:run
```

**2. Submit an ETL job:**
```bash
curl -X POST http://localhost:8080/etl/run \
  -H "Content-Type: application/json" \
  -d '{"sourceUrl":"https://restcountries.com/v3.1/all?fields=name,cca2,cca3,capital,region,population,area,languages,currencies,flag","delayMs":5000" }' | jq '.'
```

**3. Check job status:**
```bash
curl http://localhost:8080/etl/status | jq '.'
```

**4. Retrieve loaded countries:**
```bash
# Get all countries
curl http://localhost:8080/country | jq '.[0:3]'

# Count total
curl http://localhost:8080/country | jq 'length'

# Find specific country
curl http://localhost:8080/country | jq '.[] | select(.code == "AFG")'
```

**5. Inspect database (optional):**
```sql
-- View jobs
SELECT * FROM etl_jobs ORDER BY start_time DESC;

-- View countries for job 1
SELECT * FROM countries_job_1 LIMIT 10;
```

## Project Structure

```
src/
├── main/
│   ├── java/dev/velocirawesome/etl/
│   │   ├── controller/
│   │   │   ├── EtlController.java       # POST /etl/run, GET /etl/status
│   │   │   └── CountryController.java   # GET /country
│   │   ├── service/
│   │   │   ├── ExtractionService.java   # HTTP fetch & parse
│   │   │   ├── TransformationService.java # Validate & filter
│   │   │   ├── LoadingService.java       # Database insert
│   │   │   └── EtlJobService.java        # Orchestrator
│   │   ├── repository/
│   │   │   ├── EtlJobRepository.java     # Job CRUD
│   │   │   └── CountryRepository.java    # Country CRUD
│   │   ├── model/
│   │   │   ├── entity/
│   │   │   │   ├── EtlJob.java
│   │   │   │   ├── Country.java
│   │   │   │   └── JobStatus.java
│   │   │   └── dto/
│   │   │       ├── EtlRunRequest.java
│   │   │       ├── EtlRunResponse.java
│   │   │       ├── EtlStatusResponse.java
│   │   │       ├── CountryRecord.java
│   │   │       └── ErrorResponse.java
│   │   ├── config/
│   │   │   ├── JooqConfig.java
│   │   │   └── AsyncConfig.java
│   │   ├── exception/
│   │   │   ├── EtlException.java
│   │   │   ├── ExtractionException.java
│   │   │   ├── TransformationException.java
│   │   │   └── LoadingException.java
│   │   └── EtlApplication.java
│   └── resources/
│       ├── application.yml
│       └── schema.sql
└── test/
    └── java/dev/velocirawesome/etl/
        ├── integration/
        │   └── EtlIntegrationTest.java
        └── unit/
            └── TransformationServiceTest.java
```

## Technology Stack

- **Java**: 25 (latest LTS with preview features)
- **Spring Boot**: 4.0.0 (latest major version)
- **jOOQ**: Code generation for type-safe SQL queries
- **H2**: In-memory database for development/testing
- **Jackson 3**: Modern JSON processing (tools.jackson.*)
- **SLF4J**: Logging framework
- **JUnit 5 + AssertJ**: Testing framework with fluent assertions

## Key Design Decisions

### 1. jOOQ Code Generation
Type-safe database access using generated classes:
- Run `mvn clean jooq-codegen:generate` to regenerate from schema
- Compile-time SQL verification prevents runtime errors
- Auto-completion and refactoring support in IDE

### 2. Centralized Exception Handling
`@RestControllerAdvice` provides consistent error responses:
- All exceptions mapped to proper HTTP status codes
- Structured error response format across all endpoints
- Automatic logging of error details

### 3. Transactional Data Loading
Loading phase uses `@Transactional` for ACID compliance:
- All-or-nothing writes to database
- Field projection extracts only needed JSON fields
- Automatic rollback on failures

### 4. Async Execution with Configurable Delays
The `EtlJobService.executePipeline()` runs asynchronously:
- Returns 202 Accepted immediately
- Optional `delayMs` parameter simulates long-running jobs
- Enables testing of parallel job execution
- Thread pool executor allows concurrent processing

### 5. Job-Specific Tables
Each ETL job creates its own countries table (e.g., `countries_job_1`):
- Prevents data collisions between concurrent jobs
- Simplifies cleanup and job isolation
- Table names use validated Long jobIds with hardcoded prefix

## Testing

Run all tests:
```bash
mvn test
```

Run specific test:
```bash
mvn test -Dtest=EtlControllerTest
```

The project includes comprehensive unit and integration tests using JUnit 5 and AssertJ for fluent assertions.

## License

This project is part of the Velocirawesome ETL initiative.
