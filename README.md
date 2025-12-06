# ETL Microservice

An asynchronous Extract-Transform-Load (ETL) microservice for processing country data from REST APIs. Built with Spring Boot 4.0, Java 25, jOOQ, H2, and Jackson 3.

## Features

- **Async Pipeline Execution**: Non-blocking ETL operations with 202 Accepted responses
- **Dynamic Job Tracking**: Each job gets a unique ID with status monitoring
- **Job-Specific Data Tables**: Countries stored in isolation per job (countries_job_<jobId>)
- **Type-Safe SQL**: jOOQ for compile-time SQL verification
- **Comprehensive Logging**: SLF4J logging at INFO/ERROR levels for job lifecycle
- **Error Resilience**: Global exception handlers with proper HTTP status codes
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

Submit a URL for ETL processing. Returns immediately with job ID.

```bash
curl -X POST http://localhost:8080/etl/run \
  -H "Content-Type: application/json" \
  -d '{"sourceUrl":"https://restcountries.com/v3.1/all"}'
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

## Happy Path Scenario

This example demonstrates a complete ETL workflow:

### Step 1: Start the application

```bash
mvn spring-boot:run
```

Wait for the application to fully start (look for "Started EtlApplication in X seconds").

### Step 2: Submit ETL job

```bash
JOB_RESPONSE=$(curl -s -X POST http://localhost:8080/etl/run \
  -H "Content-Type: application/json" \
  -d '{"sourceUrl":"https://restcountries.com/v3.1/all"}')

echo "Job Response:"
echo $JOB_RESPONSE | jq '.'
```

**Expected Output:**
```json
{
  "jobId": 1,
  "status": "RUNNING",
  "message": "ETL job started asynchronously"
}
```

### Step 3: Poll job status until completion

```bash
# Poll every 2 seconds until job completes
echo "Polling job status..."
for i in {1..30}; do
  STATUS=$(curl -s http://localhost:8080/etl/status)
  JOB_STATUS=$(echo $STATUS | jq -r '.status')

  if [ "$JOB_STATUS" = "RUNNING" ]; then
    echo "[$i] Status: $JOB_STATUS - waiting..."
    sleep 2
  else
    echo "[$i] Status: $JOB_STATUS - job completed!"
    echo $STATUS | jq '.'
    break
  fi
done
```

**Expected Output (after ~5-10 seconds):**
```json
{
  "jobId": 1,
  "sourceUrl": "https://restcountries.com/v3.1/all",
  "status": "SUCCESS",
  "startTime": "2025-12-06T14:30:00",
  "endTime": "2025-12-06T14:30:08",
  "recordsExtracted": 250,
  "recordsTransformed": 250,
  "recordsLoaded": 250,
  "errorMessage": null
}
```

### Step 4: Retrieve loaded country data

```bash
echo "Retrieving countries..."
COUNTRIES=$(curl -s http://localhost:8080/country)

echo "Total countries:"
echo $COUNTRIES | jq 'length'

echo ""
echo "First 3 countries:"
echo $COUNTRIES | jq '.[0:3]'

echo ""
echo "Specific country (Afghanistan):"
echo $COUNTRIES | jq '.[] | select(.code == "AFG")'
```

**Expected Output:**
```
Total countries:
250

First 3 countries:
[
  {
    "code": "AFG",
    "data": {
      "name": { ... }
    }
  },
  ...
]

Specific country (Afghanistan):
{
  "code": "AFG",
  "data": {
    "name": {
      "common": "Afghanistan",
      "official": "Islamic Emirate of Afghanistan"
    },
    ...
  }
}
```

### Step 5: Check database directly (optional)

```bash
# Access H2 console at http://localhost:8080/h2-console
# Then run these queries:

-- Check all jobs
SELECT job_id, status, records_extracted, records_transformed, records_loaded FROM etl_jobs ORDER BY start_time DESC;

-- Check latest job
SELECT * FROM etl_jobs ORDER BY start_time DESC LIMIT 1;

-- Check countries for job 1
SELECT code, data FROM countries_job_1 LIMIT 10;
```

## Complete Automated Script

Here's a shell script to run the entire happy path scenario:

```bash
#!/bin/bash

set -e

echo "=== ETL Microservice Happy Path Demo ==="
echo ""

# Step 1: Submit ETL job
echo "Step 1: Submitting ETL job..."
JOB_RESPONSE=$(curl -s -X POST http://localhost:8080/etl/run \
  -H "Content-Type: application/json" \
  -d '{"sourceUrl":"https://restcountries.com/v3.1/all"}')

JOB_ID=$(echo $JOB_RESPONSE | jq -r '.jobId')
echo "✓ Job submitted with ID: $JOB_ID"
echo "  Status: $(echo $JOB_RESPONSE | jq -r '.status')"
echo ""

# Step 2: Poll status
echo "Step 2: Polling job status..."
for i in {1..30}; do
  STATUS=$(curl -s http://localhost:8080/etl/status)
  JOB_STATUS=$(echo $STATUS | jq -r '.status')

  if [ "$JOB_STATUS" = "RUNNING" ]; then
    printf "."
    sleep 2
  elif [ "$JOB_STATUS" = "SUCCESS" ]; then
    echo ""
    echo "✓ Job completed successfully!"
    echo "  Records Extracted: $(echo $STATUS | jq -r '.recordsExtracted')"
    echo "  Records Transformed: $(echo $STATUS | jq -r '.recordsTransformed')"
    echo "  Records Loaded: $(echo $STATUS | jq -r '.recordsLoaded')"
    break
  else
    echo ""
    echo "✗ Job failed with status: $JOB_STATUS"
    echo "  Error: $(echo $STATUS | jq -r '.errorMessage')"
    exit 1
  fi
done
echo ""

# Step 3: Retrieve countries
echo "Step 3: Retrieving country data..."
COUNTRIES=$(curl -s http://localhost:8080/country)
COUNTRY_COUNT=$(echo $COUNTRIES | jq 'length')
echo "✓ Retrieved $COUNTRY_COUNT countries"
echo ""

# Step 4: Show sample
echo "Step 4: Sample countries:"
echo $COUNTRIES | jq '.[0:3]'
echo ""

echo "=== Happy Path Demo Complete ==="
```

Save as `happy-path.sh`, make executable, and run:

```bash
chmod +x happy-path.sh
./happy-path.sh
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
- **jOOQ**: For type-safe SQL queries
- **H2**: In-memory database for development/testing
- **Jackson 3**: Modern JSON processing (tools.jackson.*)
- **SLF4J**: Logging framework
- **JUnit 5**: Testing framework

## Key Design Decisions

### 1. String Concatenation for Table Names
SQL cannot parameterize table names - they must be known at parse-time. The safe approach uses string concatenation with validated Long jobIds:
```java
String tableName = "countries_job_" + jobId; // jobId is validated Long, prefix is hardcoded
```

### 2. Async Execution
The `EtlJobService.executePipeline()` method runs asynchronously:
- Returns 202 Accepted immediately
- Pipeline executes in thread pool executor
- Job status updates after each phase
- Allows concurrent job processing

### 3. Job-Specific Tables
Each ETL job creates its own countries table:
- `countries_job_1` for job 1
- `countries_job_2` for job 2
- Prevents data collisions
- Allows easy cleanup by job

### 4. Transformation Validation
Countries must have both `cca3` (code) and `name` fields to be valid:
```java
if (record.has("cca3") && record.has("name")) {
    // Valid country - include in results
}
```

## Testing

Run all tests:

```bash
mvn test
```

Run specific test:

```bash
mvn test -Dtest=TransformationServiceTest
```

View test reports:

```bash
open target/surefire-reports/index.html
```

## Troubleshooting

### Job stuck in RUNNING status

The async executor may be busy. Check logs for errors:

```bash
# View ERROR logs
curl http://localhost:8080/etl/status | jq '.errorMessage'
```

### H2 Index Already Exists Error

The schema uses `CREATE INDEX IF NOT EXISTS` to avoid duplicate index errors on re-initialization.

### Network Timeout

If the REST Countries API is slow, increase the wait time in polling:

```bash
sleep 5  # Increase from 2 seconds
```

## License

This project is part of the Velocirawesome ETL initiative.
