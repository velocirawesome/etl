# Quickstart Guide: ETL Shell Application

**Feature**: 001-shell-app | **Target Duration**: 60 minutes

## Overview

This guide walks through implementing the ETL microservice from scratch, following the architecture defined in [plan.md](plan.md), data model in [data-model.md](data-model.md), and API contracts in [contracts/openapi.yaml](contracts/openapi.yaml).

---

## Prerequisites

Before starting, ensure you have:
- **Java 25** installed (verify: `java --version`)
- **Maven 3.9+** installed (verify: `mvn --version`)
- **IDE** configured (IntelliJ IDEA, VS Code with Java extensions, or Eclipse)
- **Git** for version control
- **curl** or Postman for API testing

---

## Project Setup (5 minutes)

### 1. Initialize Spring Boot Project

Use [Spring Initializr](https://start.spring.io) to generate the project:

**Configuration**:
- **Project**: Maven
- **Language**: Java
- **Spring Boot**: 4.0.0 (latest stable)
- **Packaging**: Jar
- **Java**: 25
- **Group**: dev.velocirawesome.etl
- **Artifact**: etl-demo
- **Dependencies**: Spring Web, jOOQ Access Layer, H2 Database, Spring Boot DevTools

Or generate via curl:

```bash
curl https://start.spring.io/starter.zip \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=4.0.0 \
  -d groupId=dev.velocirawesome.etl \
  -d artifactId=etl-demo \
  -d packageName=dev.velocirawesome.etl \
  -d javaVersion=25 \
  -d dependencies=web,jooq,h2,devtools \
  -o etl-demo.zip

unzip etl-demo.zip && cd etl-demo
```

### 2. Configure `pom.xml`

Update dependencies to include Jackson 3 (tools.jackson package):

```xml
<project>
    <properties>
        <java.version>25</java.version>
        <spring-boot.version>4.0.0</spring-boot.version>
        <jooq.version>3.19.0</jooq.version>
        <jackson.version>3.0.0</jackson.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Starter Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot jOOQ -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jooq</artifactId>
        </dependency>

        <!-- H2 Database -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Jackson 3 for JSON processing (tools.jackson package) -->
        <dependency>
            <groupId>tools.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>

        <!-- Spring Boot Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- JUnit 5 -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3. Configure `application.yml`

Set up H2 datasource and jOOQ configuration:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:etldb;DB_CLOSE_DELAY=-1;MODE=MySQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
  jooq:
    sql-dialect: H2

logging:
  level:
    dev.velocirawesome.etl: INFO
    org.jooq: WARN
```

### 4. Create Package Structure

```bash
mkdir -p src/main/java/dev/velocirawesome/etl/{controller,service,repository,model/{entity,dto},config,exception}
mkdir -p src/main/resources
mkdir -p src/test/java/dev/velocirawesome/etl/{integration,unit}
```

---

## Implementation Phases

### Phase 1: Core Configuration (10 minutes)

#### 1.1 Create jOOQ Configuration Bean

`src/main/java/dev/velocirawesome/etl/config/JooqConfig.java`:

```java
package dev.velocirawesome.etl.config;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class JooqConfig {

    @Bean
    public DSLContext dslContext(DataSource dataSource) {
        return DSL.using(dataSource, SQLDialect.H2);
    }
}
```

#### 1.2 Create Async Configuration

`src/main/java/dev/velocirawesome/etl/config/AsyncConfig.java`:

```java
package dev.velocirawesome.etl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("etl-async-");
        executor.initialize();
        return executor;
    }
}
```

#### 1.3 Create Database Schema Initializer

`src/main/resources/schema.sql`:

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

CREATE INDEX IF NOT EXISTS idx_etl_jobs_status ON etl_jobs(status);
CREATE INDEX IF NOT EXISTS idx_etl_jobs_start_time ON etl_jobs(start_time DESC);
```

Update `application.yml` to enable schema initialization:

```yaml
spring:
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
```

---

### Phase 2: Data Layer (10 minutes)

#### 2.1 Define Entities

`src/main/java/dev/velocirawesome/etl/model/entity/EtlJob.java`:

```java
package dev.velocirawesome.etl.model.entity;

import java.time.Instant;

public class EtlJob {
    private Long jobId;
    private String sourceUrl;
    private JobStatus status;
    private Instant startTime;
    private Instant endTime;
    private Integer recordsExtracted;
    private Integer recordsTransformed;
    private Integer recordsLoaded;
    private String errorMessage;

    // Constructors, getters, setters
}

public enum JobStatus {
    RUNNING, SUCCESS, FAILED
}
```

`src/main/java/dev/velocirawesome/etl/model/entity/Country.java`:

```java
package dev.velocirawesome.etl.model.entity;

import tools.jackson.databind.JsonNode;  // Jackson 3 package

public class Country {
    private String code;           // Primary key
    private JsonNode data;         // Jackson JsonNode

    public Country(String code, JsonNode data) {
        this.code = code;
        this.data = data;
    }

    // Getters, setters
}
```

#### 2.2 Create Repositories

`src/main/java/dev/velocirawesome/etl/repository/EtlJobRepository.java`:

```java
package dev.velocirawesome.etl.repository;

import dev.velocirawesome.etl.model.entity.EtlJob;
import dev.velocirawesome.etl.model.entity.JobStatus;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
public class EtlJobRepository {

    private final DSLContext dsl;

    public EtlJobRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional
    public synchronized Long createJob(String sourceUrl) {
        // Concurrent-safe job ID generation
        Long maxJobId = dsl.select(DSL.max(DSL.field("job_id", Long.class)))
                           .from(DSL.table("etl_jobs"))
                           .fetchOne(0, Long.class);
        Long newJobId = (maxJobId != null ? maxJobId : 0) + 1;

        dsl.insertInto(DSL.table("etl_jobs"))
           .set(DSL.field("job_id"), newJobId)
           .set(DSL.field("source_url"), sourceUrl)
           .set(DSL.field("status"), JobStatus.RUNNING.name())
           .set(DSL.field("start_time"), Instant.now())
           .set(DSL.field("records_extracted"), 0)
           .set(DSL.field("records_transformed"), 0)
           .set(DSL.field("records_loaded"), 0)
           .execute();

        return newJobId;
    }

    public EtlJob getLatestJob() {
        Record record = dsl.selectFrom(DSL.table("etl_jobs"))
                           .orderBy(DSL.field("start_time").desc())
                           .limit(1)
                           .fetchOne();
        return record != null ? mapToEtlJob(record) : null;
    }

    public EtlJob getLatestSuccessfulJob() {
        Record record = dsl.selectFrom(DSL.table("etl_jobs"))
                           .where(DSL.field("status").eq(JobStatus.SUCCESS.name()))
                           .orderBy(DSL.field("start_time").desc())
                           .limit(1)
                           .fetchOne();
        return record != null ? mapToEtlJob(record) : null;
    }

    @Transactional
    public void updateRecordsExtracted(Long jobId, int count) {
        dsl.update(DSL.table("etl_jobs"))
           .set(DSL.field("records_extracted"), count)
           .where(DSL.field("job_id").eq(jobId))
           .execute();
    }

    @Transactional
    public void updateRecordsTransformed(Long jobId, int count) {
        dsl.update(DSL.table("etl_jobs"))
           .set(DSL.field("records_transformed"), count)
           .where(DSL.field("job_id").eq(jobId))
           .execute();
    }

    @Transactional
    public void updateRecordsLoaded(Long jobId, int count) {
        dsl.update(DSL.table("etl_jobs"))
           .set(DSL.field("records_loaded"), count)
           .where(DSL.field("job_id").eq(jobId))
           .execute();
    }

    @Transactional
    public void updateJobStatus(Long jobId, JobStatus status, String errorMessage) {
        dsl.update(DSL.table("etl_jobs"))
           .set(DSL.field("status"), status.name())
           .set(DSL.field("end_time"), Instant.now())
           .set(DSL.field("error_message"), errorMessage)
           .where(DSL.field("job_id").eq(jobId))
           .execute();
    }

    private EtlJob mapToEtlJob(Record record) {
        // Map jOOQ Record to EtlJob entity (implementation details)
        // ... extract fields and construct EtlJob object
        return new EtlJob(/* ... */);
    }
}
```

`src/main/java/dev/velocirawesome/etl/repository/CountryRepository.java`:

```java
package dev.velocirawesome.etl.repository;

import dev.velocirawesome.etl.model.entity.Country;
import org.jooq.BatchBindStep;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;  // Jackson 3 package
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class CountryRepository {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public CountryRepository(DSLContext dsl) {
        this.dsl = dsl;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public void createJobTable(Long jobId) {
        String tableName = "countries_job_" + jobId;
        dsl.execute("CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "code VARCHAR(3) PRIMARY KEY, " +
                    "data JSON NOT NULL)");
    }

    @Transactional
    public void insertCountries(Long jobId, List<Country> countries) {
        String tableName = "countries_job_" + jobId;

        BatchBindStep batch = dsl.batch(
            dsl.insertInto(DSL.table(tableName))
               .columns(DSL.field("code"), DSL.field("data"))
               .values((Object) null, null)
        );

        for (Country country : countries) {
            batch.bind(country.getCode(), country.getData().toString());
        }

        batch.execute();
    }

    public List<Country> getCountriesByJobId(Long jobId) {
        String tableName = "countries_job_" + jobId;

        return dsl.selectFrom(DSL.table(tableName))
                  .fetch()
                  .stream()
                  .map(this::mapToCountry)
                  .collect(Collectors.toList());
    }

    private Country mapToCountry(Record record) {
        try {
            String code = record.getValue(DSL.field("code", String.class));
            String dataJson = record.getValue(DSL.field("data", String.class));
            JsonNode data = objectMapper.readTree(dataJson);
            return new Country(code, data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to map Country", e);
        }
    }
}
```

---

### Phase 3: Service Layer (15 minutes)

#### 3.1 Extraction Service

`src/main/java/dev/velocirawesome/etl/service/ExtractionService.java`:

```java
package dev.velocirawesome.etl.service;

import dev.velocirawesome.etl.exception.ExtractionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;  // Jackson 3 package
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ExtractionService() {
        this.restClient = RestClient.create();
        this.objectMapper = new ObjectMapper();
    }

    public List<JsonNode> fetchData(String sourceUrl) {
        log.info("Starting extraction from: {}", sourceUrl);

        try {
            String jsonResponse = restClient.get()
                                            .uri(sourceUrl)
                                            .retrieve()
                                            .body(String.class);

            JsonNode root = objectMapper.readTree(jsonResponse);

            List<JsonNode> records = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(records::add);
            }

            log.info("Extracted {} records", records.size());
            return records;

        } catch (Exception e) {
            log.error("Extraction failed: {}", e.getMessage());
            throw new ExtractionException("Failed to fetch data from sourceUrl: " + e.getMessage(), e);
        }
    }
}
```

#### 3.2 Transformation Service

`src/main/java/dev/velocirawesome/etl/service/TransformationService.java`:

```java
package dev.velocirawesome.etl.service;

import dev.velocirawesome.etl.model.entity.Country;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;  // Jackson 3 package

import java.util.ArrayList;
import java.util.List;

@Service
public class TransformationService {

    private static final Logger log = LoggerFactory.getLogger(TransformationService.class);

    public List<Country> transform(List<JsonNode> rawRecords) {
        log.info("Starting transformation for {} records", rawRecords.size());

        List<Country> countries = new ArrayList<>();
        for (JsonNode record : rawRecords) {
            try {
                String code = extractCode(record);
                if (code == null || code.isEmpty()) {
                    log.warn("Skipping record with missing code");
                    continue;
                }

                JsonNode name = record.get("name");
                if (name == null || name.isNull()) {
                    log.warn("Skipping record with missing name for code: {}", code);
                    continue;
                }

                Country country = new Country(code, record);
                countries.add(country);

            } catch (Exception e) {
                log.warn("Failed to transform record: {}", e.getMessage());
            }
        }

        log.info("Transformed {} valid records", countries.size());
        return countries;
    }

    private String extractCode(JsonNode record) {
        // Extract country code from JSON (implementation varies by source format)
        // REST Countries API uses "cca2" for 2-letter code
        return record.has("cca2") ? record.get("cca2").asText() : null;
    }
}
```

#### 3.3 Loading Service

`src/main/java/dev/velocirawesome/etl/service/LoadingService.java`:

```java
package dev.velocirawesome.etl.service;

import dev.velocirawesome.etl.exception.LoadingException;
import dev.velocirawesome.etl.model.entity.Country;
import dev.velocirawesome.etl.repository.CountryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LoadingService {

    private static final Logger log = LoggerFactory.getLogger(LoadingService.class);

    private final CountryRepository countryRepository;

    public LoadingService(CountryRepository countryRepository) {
        this.countryRepository = countryRepository;
    }

    @Transactional
    public void load(Long jobId, List<Country> countries) {
        log.info("Starting loading for job {} with {} records", jobId, countries.size());

        try {
            countryRepository.createJobTable(jobId);
            countryRepository.insertCountries(jobId, countries);
            log.info("Loaded {} records to countries_job_{}", countries.size(), jobId);

        } catch (Exception e) {
            log.error("Loading failed: {}", e.getMessage());
            throw new LoadingException("Failed to load countries: " + e.getMessage(), e);
        }
    }
}
```

#### 3.4 Orchestrator Service

`src/main/java/dev/velocirawesome/etl/service/EtlJobService.java`:

```java
package dev.velocirawesome.etl.service;

import dev.velocirawesome.etl.model.entity.Country;
import dev.velocirawesome.etl.model.entity.JobStatus;
import dev.velocirawesome.etl.repository.EtlJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;  // Jackson 3 package

import java.util.List;

@Service
public class EtlJobService {

    private static final Logger log = LoggerFactory.getLogger(EtlJobService.class);

    private final EtlJobRepository jobRepository;
    private final ExtractionService extractionService;
    private final TransformationService transformationService;
    private final LoadingService loadingService;

    public EtlJobService(EtlJobRepository jobRepository,
                         ExtractionService extractionService,
                         TransformationService transformationService,
                         LoadingService loadingService) {
        this.jobRepository = jobRepository;
        this.extractionService = extractionService;
        this.transformationService = transformationService;
        this.loadingService = loadingService;
    }

    @Async("taskExecutor")
    public void executePipeline(Long jobId, String sourceUrl) {
        log.info("Starting ETL pipeline for job {}", jobId);

        try {
            // Phase 1: Extract
            List<JsonNode> rawRecords = extractionService.fetchData(sourceUrl);
            jobRepository.updateRecordsExtracted(jobId, rawRecords.size());

            // Phase 2: Transform
            List<Country> countries = transformationService.transform(rawRecords);
            jobRepository.updateRecordsTransformed(jobId, countries.size());

            // Phase 3: Load
            loadingService.load(jobId, countries);
            jobRepository.updateRecordsLoaded(jobId, countries.size());

            // Mark success
            jobRepository.updateJobStatus(jobId, JobStatus.SUCCESS, null);
            log.info("ETL pipeline completed successfully for job {}", jobId);

        } catch (Exception e) {
            log.error("ETL pipeline failed for job {}: {}", jobId, e.getMessage());
            jobRepository.updateJobStatus(jobId, JobStatus.FAILED, e.getMessage());
        }
    }
}
```

---

### Phase 4: Controller Layer (10 minutes)

#### 4.1 Define DTOs

`src/main/java/dev/velocirawesome/etl/model/dto/EtlRunRequest.java`:

```java
package dev.velocirawesome.etl.model.dto;

public class EtlRunRequest {
    private String sourceUrl;

    public EtlRunRequest() {}

    public EtlRunRequest(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    // Getters, setters
}
```

(Create similar DTOs for `EtlRunResponse`, `EtlStatusResponse`, `CountryRecord` based on OpenAPI spec)

#### 4.2 ETL Controller

`src/main/java/dev/velocirawesome/etl/controller/EtlController.java`:

```java
package dev.velocirawesome.etl.controller;

import dev.velocirawesome.etl.model.dto.EtlRunRequest;
import dev.velocirawesome.etl.model.dto.EtlRunResponse;
import dev.velocirawesome.etl.model.dto.EtlStatusResponse;
import dev.velocirawesome.etl.model.entity.EtlJob;
import dev.velocirawesome.etl.model.entity.JobStatus;
import dev.velocirawesome.etl.repository.EtlJobRepository;
import dev.velocirawesome.etl.service.EtlJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/etl")
public class EtlController {

    private final EtlJobService etlJobService;
    private final EtlJobRepository jobRepository;

    public EtlController(EtlJobService etlJobService, EtlJobRepository jobRepository) {
        this.etlJobService = etlJobService;
        this.jobRepository = jobRepository;
    }

    @PostMapping("/run")
    public ResponseEntity<EtlRunResponse> runEtlJob(@RequestBody EtlRunRequest request) {
        // Create job and start async execution
        Long jobId = jobRepository.createJob(request.getSourceUrl());
        etlJobService.executePipeline(jobId, request.getSourceUrl());

        return ResponseEntity.accepted().body(
            new EtlRunResponse(jobId, JobStatus.RUNNING, "ETL job started successfully")
        );
    }

    @GetMapping("/status")
    public ResponseEntity<EtlStatusResponse> getStatus() {
        EtlJob latestJob = jobRepository.getLatestJob();
        if (latestJob == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(mapToStatusResponse(latestJob));
    }

    private EtlStatusResponse mapToStatusResponse(EtlJob job) {
        // Map EtlJob to EtlStatusResponse (implementation details)
        return new EtlStatusResponse(/* ... */);
    }
}
```

#### 4.3 Country Controller

`src/main/java/dev/velocirawesome/etl/controller/CountryController.java`:

```java
package dev.velocirawesome.etl.controller;

import dev.velocirawesome.etl.model.dto.CountryRecord;
import dev.velocirawesome.etl.model.entity.Country;
import dev.velocirawesome.etl.model.entity.EtlJob;
import dev.velocirawesome.etl.repository.CountryRepository;
import dev.velocirawesome.etl.repository.EtlJobRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/country")
public class CountryController {

    private final EtlJobRepository jobRepository;
    private final CountryRepository countryRepository;

    public CountryController(EtlJobRepository jobRepository, CountryRepository countryRepository) {
        this.jobRepository = jobRepository;
        this.countryRepository = countryRepository;
    }

    @GetMapping
    public ResponseEntity<List<CountryRecord>> getCountries() {
        // Find latest successful job
        EtlJob latestSuccessfulJob = jobRepository.getLatestSuccessfulJob();
        if (latestSuccessfulJob == null) {
            return ResponseEntity.notFound().build();
        }

        List<Country> countries = countryRepository.getCountriesByJobId(latestSuccessfulJob.getJobId());
        List<CountryRecord> records = countries.stream()
            .map(c -> new CountryRecord(c.getCode(), c.getData()))
            .collect(Collectors.toList());

        return ResponseEntity.ok(records);
    }
}
```

---

### Phase 5: Testing & Validation (10 minutes)

#### 5.1 Integration Test

`src/test/java/dev/velocirawesome/etl/integration/EtlIntegrationTest.java`:

```java
package dev.velocirawesome.etl.integration;

import dev.velocirawesome.etl.model.dto.CountryRecord;
import dev.velocirawesome.etl.model.dto.EtlRunRequest;
import dev.velocirawesome.etl.model.dto.EtlRunResponse;
import dev.velocirawesome.etl.model.dto.EtlStatusResponse;
import dev.velocirawesome.etl.model.entity.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EtlIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    public void testEtlPipeline_withRealUrl() throws Exception {
        RestClient client = RestClient.create("http://localhost:" + port);

        // Step 1: Trigger job
        EtlRunResponse runResponse = client.post()
            .uri("/etl/run")
            .body(new EtlRunRequest("https://restcountries.com/v3.1/all"))
            .retrieve()
            .body(EtlRunResponse.class);

        assertThat(runResponse.getStatus()).isEqualTo(JobStatus.RUNNING);

        // Step 2: Poll status until complete
        EtlStatusResponse statusResponse;
        do {
            Thread.sleep(1000);
            statusResponse = client.get()
                .uri("/etl/status")
                .retrieve()
                .body(EtlStatusResponse.class);
        } while (statusResponse.getStatus() == JobStatus.RUNNING);

        assertThat(statusResponse.getStatus()).isEqualTo(JobStatus.SUCCESS);
        assertThat(statusResponse.getRecordsLoaded()).isGreaterThan(10);

        // Step 3: Retrieve countries
        List<CountryRecord> countries = client.get()
            .uri("/country")
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

        assertThat(countries).isNotEmpty();
    }
}
```

#### 5.2 Unit Test

`src/test/java/dev/velocirawesome/etl/unit/TransformationServiceTest.java`:

```java
package dev.velocirawesome.etl.unit;

import dev.velocirawesome.etl.model.entity.Country;
import dev.velocirawesome.etl.service.TransformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TransformationServiceTest {

    private TransformationService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        service = new TransformationService();
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testTransform_validRecords() throws Exception {
        String json = "[{\"cca2\":\"US\",\"name\":{\"common\":\"United States\"}}," +
                      "{\"cca2\":\"CA\",\"name\":{\"common\":\"Canada\"}}]";
        JsonNode root = objectMapper.readTree(json);

        List<JsonNode> rawRecords = List.of(root.get(0), root.get(1));
        List<Country> countries = service.transform(rawRecords);

        assertThat(countries).hasSize(2);
        assertThat(countries.get(0).getCode()).isEqualTo("US");
    }

    @Test
    public void testTransform_filtersInvalidRecords() throws Exception {
        String json = "[{\"cca2\":\"\",\"name\":{\"common\":\"Invalid\"}}, " +
                      "{\"name\":{\"common\":\"No Code\"}}]";
        JsonNode root = objectMapper.readTree(json);

        List<JsonNode> rawRecords = List.of(root.get(0), root.get(1));
        List<Country> countries = service.transform(rawRecords);

        assertThat(countries).isEmpty();
    }
}
```

---

## Running the Application

### 1. Build and Run

**Initial compilation** (first time only):
```bash
mvn compile
mvn spring-boot:run
```

**Subsequent runs** (iterate faster):
```bash
# Recompile only (no clean, no test):
mvn compile

# Then restart the application:
mvn spring-boot:run
```

**If you encounter compilation errors, clean the build**:
```bash
mvn clean compile
mvn spring-boot:run
```

### 2. Test with curl

**Trigger ETL job**:
```bash
curl -X POST http://localhost:8080/etl/run \
  -H "Content-Type: application/json" \
  -d '{"sourceUrl": "https://restcountries.com/v3.1/all"}'
```

**Check job status**:
```bash
curl http://localhost:8080/etl/status
```

**Retrieve countries**:
```bash
curl http://localhost:8080/country
```

### 3. Testing Strategy (Iterate Faster)

**Run all tests** (one-time validation):
```bash
mvn test
```

**Run a specific test class** (focus on one component):
```bash
mvn test -Dtest=TransformationServiceTest
```

**Run a specific test method** (debug a single scenario):
```bash
mvn test -Dtest=TransformationServiceTest#testTransform_validRecords
```

**Run all tests in a folder** (integration tests only):
```bash
mvn test -Dtest=*IntegrationTest
```

**Skip tests during build** (if you're confident in code):
```bash
mvn compile -DskipTests
```

**Quick compilation + run without ANY tests**:
```bash
mvn compile -DskipTests && mvn spring-boot:run
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| H2 database not initializing | Check `application.yml` datasource URL and schema initialization |
| jOOQ DSLContext null | Verify `JooqConfig` bean is created and DataSource is configured |
| Async method not executing | Ensure `@EnableAsync` is present and `taskExecutor` bean is configured |
| HTTP fetch fails | Verify sourceUrl is reachable; check firewall/proxy settings |
| JSON parsing errors (Jackson 3) | Ensure imports use `tools.jackson.*` package (not `com.fasterxml.jackson.*`) |

---

## Jackson 3 Migration Notes

Jackson 3.0 changed the Java package from `com.fasterxml.jackson` to `tools.jackson`:

**Maven Dependency**:
```xml
<dependency>
    <groupId>tools.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>3.0.0</version>
</dependency>
```

**Import Statements**:
```java
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonSerialize;
```

See [Jackson 3 Migration Guide](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md) for details.

---

## Next Steps

After completing this quickstart:
1. Add more robust error handling (retry logic, circuit breakers)
2. Implement pagination for `/country` endpoint
3. Add metrics and monitoring (Spring Actuator)
4. Deploy to cloud platform (AWS, GCP, Azure)
5. Add authentication and authorization

---

## Time Allocation Summary

| Phase | Duration | Tasks |
|-------|----------|-------|
| Setup | 5 min | Project init via start.spring.io, dependencies, config |
| Core Config | 10 min | jOOQ, Async, Schema init |
| Data Layer | 10 min | Entities, repositories with Jackson 3 |
| Service Layer | 15 min | Extract, Transform, Load, Orchestrator |
| Controller Layer | 10 min | ETL and Country controllers |
| Testing | 10 min | Integration and unit tests |
| **Total** | **60 min** | Complete implementation |

---

## Sources

- [Spring Initializr (Spring Boot 4.0.0)](https://start.spring.io/)
- [Jackson Release 3.0 · FasterXML/jackson Wiki · GitHub](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0)
- [Introducing Jackson 3 support in Spring](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/)
- [Migrating to Jackson 3](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md)
