# ETL Microservice Implementation Research

## Context

This research document covers implementation patterns for a demonstration ETL microservice with the following technical stack:

- **Java 25** + **Spring Boot 7** microservice
- **jOOQ** for dynamic SQL (runtime table creation for job-specific Country tables)
- **Spring HttpServiceProxyFactory** for HTTP client (avoiding reactive types)
- **Spring @Async** for background job execution
- **H2 in-memory database**
- **60-minute implementation constraint**

---

## 1. jOOQ Dynamic Table Creation

### Decision
Use jOOQ's `DSLContext` with plain SQL via `execute()` for DDL operations, combined with `table()` and `field()` methods for DML operations on dynamically-named tables.

### Rationale
- jOOQ's type-safe DSL doesn't support dynamic table names in DDL operations
- Plain SQL execution for CREATE TABLE provides full control and clarity
- Dynamic `table()` and `field()` methods allow type-safe DML on runtime-created tables
- This hybrid approach balances safety with flexibility for the demo constraint

### Alternatives Considered
1. **Pure jOOQ code generation**: Would require compile-time schema knowledge, incompatible with runtime table creation
2. **JDBC Template**: Simpler but loses jOOQ's query building capabilities
3. **Dynamic jOOQ Records**: Overly complex for the 60-minute constraint

### Implementation Notes

#### CREATE TABLE Pattern
```java
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class CountryJobRepository {

    private final DSLContext dsl;

    public CountryJobRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void createJobTable(String jobId) {
        String tableName = "countries_job_" + jobId;

        dsl.execute(
            "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
            "  id VARCHAR(10) PRIMARY KEY, " +
            "  name VARCHAR(255) NOT NULL, " +
            "  capital VARCHAR(255), " +
            "  region VARCHAR(100), " +
            "  population BIGINT, " +
            "  area DECIMAL(15,2)" +
            ")"
        );
    }
}
```

#### INSERT Pattern with Dynamic Table
```java
import org.jooq.Table;
import org.jooq.impl.DSL;

public void insertCountry(String jobId, CountryDTO country) {
    String tableName = "countries_job_" + jobId;
    Table<?> table = DSL.table(tableName);

    dsl.insertInto(table)
       .columns(
           DSL.field("id"),
           DSL.field("name"),
           DSL.field("capital"),
           DSL.field("region"),
           DSL.field("population"),
           DSL.field("area")
       )
       .values(
           country.getId(),
           country.getName(),
           country.getCapital(),
           country.getRegion(),
           country.getPopulation(),
           country.getArea()
       )
       .execute();
}
```

#### Batch INSERT Pattern (Performance Optimization)
```java
public void insertCountries(String jobId, List<CountryDTO> countries) {
    String tableName = "countries_job_" + jobId;
    Table<?> table = DSL.table(tableName);

    var batch = dsl.batch(
        countries.stream()
            .map(country -> dsl.insertInto(table)
                .columns(
                    DSL.field("id"),
                    DSL.field("name"),
                    DSL.field("capital"),
                    DSL.field("region"),
                    DSL.field("population"),
                    DSL.field("area")
                )
                .values(
                    country.getId(),
                    country.getName(),
                    country.getCapital(),
                    country.getRegion(),
                    country.getPopulation(),
                    country.getArea()
                )
            )
            .toList()
    );

    batch.execute();
}
```

#### SELECT Pattern from Job-Specific Tables
```java
import org.jooq.Record;
import org.jooq.Result;

public List<CountryDTO> getCountriesForJob(String jobId) {
    String tableName = "countries_job_" + jobId;
    Table<?> table = DSL.table(tableName);

    Result<Record> result = dsl.select()
        .from(table)
        .fetch();

    return result.stream()
        .map(record -> new CountryDTO(
            record.get("id", String.class),
            record.get("name", String.class),
            record.get("capital", String.class),
            record.get("region", String.class),
            record.get("population", Long.class),
            record.get("area", java.math.BigDecimal.class)
        ))
        .toList();
}
```

#### Table Existence Check
```java
public boolean tableExists(String jobId) {
    String tableName = "countries_job_" + jobId;

    Integer count = dsl.selectCount()
        .from(DSL.table("INFORMATION_SCHEMA.TABLES"))
        .where(DSL.field("TABLE_NAME").eq(tableName.toUpperCase()))
        .fetchOne(0, Integer.class);

    return count != null && count > 0;
}
```

---

## 2. Spring HttpServiceProxyFactory

### Decision
Use Spring 6.1+ `HttpServiceProxyFactory` with `RestClient` adapter for declarative HTTP interface, avoiding WebClient and reactive types.

### Rationale
- Native Spring Boot 7 feature (requires Spring 6.1+)
- Declarative interface-based approach reduces boilerplate
- `RestClient` provides blocking I/O suitable for simple ETL demo
- Type-safe with compile-time validation
- No reactive dependencies needed (simpler for demo)

### Alternatives Considered
1. **RestTemplate**: Legacy approach, verbose, not recommended in modern Spring
2. **WebClient (reactive)**: Overkill for simple ETL, adds complexity with Mono/Flux
3. **Feign Client**: External dependency, HttpServiceProxyFactory is now native to Spring

### Implementation Notes

#### Configuration Bean Setup
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
            .baseUrl("https://restcountries.com/v3.1")
            .defaultHeader("Accept", "application/json")
            .build();
    }

    @Bean
    public CountriesApiClient countriesApiClient(RestClient restClient) {
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builderFor(adapter)
            .build();

        return factory.createClient(CountriesApiClient.class);
    }
}
```

#### Interface Definition Pattern
```java
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

@HttpExchange
public interface CountriesApiClient {

    @GetExchange("/all")
    List<CountryApiResponse> getAllCountries();

    @GetExchange("/alpha/{code}")
    CountryApiResponse getCountryByCode(@PathVariable String code);

    @GetExchange("/region/{region}")
    List<CountryApiResponse> getCountriesByRegion(@PathVariable String region);
}
```

#### Response DTO Pattern
```java
import com.fasterxml.jackson.annotation.JsonProperty;

public record CountryApiResponse(
    @JsonProperty("cca3") String id,
    @JsonProperty("name") NameInfo name,
    @JsonProperty("capital") List<String> capital,
    @JsonProperty("region") String region,
    @JsonProperty("population") Long population,
    @JsonProperty("area") java.math.BigDecimal area
) {
    public record NameInfo(
        @JsonProperty("common") String common
    ) {}

    public String getCapitalName() {
        return capital != null && !capital.isEmpty() ? capital.get(0) : null;
    }
}
```

#### Error Handling for HTTP Failures
```java
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
            .baseUrl("https://restcountries.com/v3.1")
            .defaultHeader("Accept", "application/json")
            .defaultStatusHandler(HttpStatusCode::is4xxClientError, (request, response) -> {
                throw new CountriesApiException(
                    "Client error: " + response.getStatusCode() +
                    " - " + new String(response.getBody().readAllBytes())
                );
            })
            .defaultStatusHandler(HttpStatusCode::is5xxServerError, (request, response) -> {
                throw new CountriesApiException(
                    "Server error: " + response.getStatusCode() +
                    " - API temporarily unavailable"
                );
            })
            .build();
    }
}
```

#### Service Layer with Error Handling
```java
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class CountriesApiService {

    private static final Logger log = LoggerFactory.getLogger(CountriesApiService.class);
    private final CountriesApiClient apiClient;

    public CountriesApiService(CountriesApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public List<CountryApiResponse> fetchAllCountries() {
        try {
            log.info("Fetching all countries from API");
            List<CountryApiResponse> countries = apiClient.getAllCountries();
            log.info("Successfully fetched {} countries", countries.size());
            return countries;
        } catch (CountriesApiException e) {
            log.error("API error while fetching countries: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while fetching countries", e);
            throw new CountriesApiException("Failed to fetch countries: " + e.getMessage(), e);
        }
    }
}
```

#### Custom Exception
```java
public class CountriesApiException extends RuntimeException {
    public CountriesApiException(String message) {
        super(message);
    }

    public CountriesApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

## 3. Spring @Async Best Practices

### Decision
Use Spring's `@Async` with `ThreadPoolTaskExecutor` configured for lightweight demo workload, with `ConcurrentHashMap` for thread-safe status tracking.

### Rationale
- Simple annotation-based approach suitable for demo complexity
- ThreadPoolTaskExecutor provides fine-grained control over thread pool sizing
- ConcurrentHashMap offers better concurrency than AtomicReference for multiple job tracking
- Clear separation between REST controller thread and async job execution thread

### Alternatives Considered
1. **CompletableFuture.supplyAsync()**: Manual thread management, less Spring integration
2. **@Scheduled with polling**: Wrong pattern for on-demand job execution
3. **Message queue (RabbitMQ/Kafka)**: Over-engineering for 60-minute demo
4. **Virtual threads (Java 21+)**: Requires Java 21+, adds complexity for marginal demo benefit

### Implementation Notes

#### @EnableAsync Configuration
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "etlTaskExecutor")
    public Executor etlTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size: number of concurrent ETL jobs
        executor.setCorePoolSize(2);

        // Max pool size: upper limit for burst capacity
        executor.setMaxPoolSize(5);

        // Queue capacity: jobs waiting for thread availability
        executor.setQueueCapacity(10);

        // Thread naming for debugging
        executor.setThreadNamePrefix("etl-job-");

        // Graceful shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}
```

#### ThreadPoolTaskExecutor Sizing Rationale
- **corePoolSize=2**: Sufficient for demo with 1-2 concurrent jobs
- **maxPoolSize=5**: Handles burst load without resource exhaustion
- **queueCapacity=10**: Reasonable buffer for demo scenarios
- **Production sizing**: Would use `Runtime.getRuntime().availableProcessors()` as baseline

#### Async Method Pattern
```java
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EtlJobService {

    private static final Logger log = LoggerFactory.getLogger(EtlJobService.class);

    @Async("etlTaskExecutor")
    public void executeJob(String jobId) {
        log.info("Starting ETL job {} on thread {}", jobId, Thread.currentThread().getName());

        try {
            // Extract phase
            extractData(jobId);

            // Transform phase
            transformData(jobId);

            // Load phase
            loadData(jobId);

            log.info("Completed ETL job {}", jobId);
        } catch (Exception e) {
            log.error("Failed ETL job {}", jobId, e);
            throw e;
        }
    }
}
```

#### Exception Handling in Async Methods
```java
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class EtlJobService {

    private final JobStatusTracker statusTracker;

    public EtlJobService(JobStatusTracker statusTracker) {
        this.statusTracker = statusTracker;
    }

    @Async("etlTaskExecutor")
    public CompletableFuture<Void> executeJob(String jobId) {
        try {
            statusTracker.updateStatus(jobId, JobStatus.RUNNING);

            // ETL execution logic
            performEtl(jobId);

            statusTracker.updateStatus(jobId, JobStatus.COMPLETED);
            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("Job {} failed", jobId, e);
            statusTracker.updateStatus(jobId, JobStatus.FAILED);
            statusTracker.updateErrorMessage(jobId, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
}
```

#### Thread-Safe Status Tracking with ConcurrentHashMap
```java
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JobStatusTracker {

    private final Map<String, JobStatusInfo> jobStatuses = new ConcurrentHashMap<>();

    public void createJob(String jobId) {
        JobStatusInfo info = new JobStatusInfo(
            jobId,
            JobStatus.PENDING,
            LocalDateTime.now(),
            null,
            null
        );
        jobStatuses.put(jobId, info);
    }

    public void updateStatus(String jobId, JobStatus status) {
        jobStatuses.computeIfPresent(jobId, (id, info) ->
            new JobStatusInfo(
                info.jobId(),
                status,
                info.startTime(),
                status.isTerminal() ? LocalDateTime.now() : null,
                info.errorMessage()
            )
        );
    }

    public void updateErrorMessage(String jobId, String errorMessage) {
        jobStatuses.computeIfPresent(jobId, (id, info) ->
            new JobStatusInfo(
                info.jobId(),
                info.status(),
                info.startTime(),
                info.endTime(),
                errorMessage
            )
        );
    }

    public JobStatusInfo getStatus(String jobId) {
        return jobStatuses.get(jobId);
    }

    public Map<String, JobStatusInfo> getAllStatuses() {
        return Map.copyOf(jobStatuses);
    }
}

public record JobStatusInfo(
    String jobId,
    JobStatus status,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String errorMessage
) {}

public enum JobStatus {
    PENDING(false),
    RUNNING(false),
    COMPLETED(true),
    FAILED(true);

    private final boolean terminal;

    JobStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
```

#### AsyncUncaughtExceptionHandler (Global Error Handler)
```java
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        return etlTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }

    // ... etlTaskExecutor() bean definition
}

class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomAsyncExceptionHandler.class);

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        log.error("Uncaught async exception in method: {} with params: {}",
                  method.getName(), params, ex);
    }
}
```

---

## 4. H2 In-Memory Configuration

### Decision
Use H2 in-memory database with `DB_CLOSE_DELAY=-1` for persistence during application lifetime, with jOOQ configured for H2 SQLDialect.

### Rationale
- Zero external dependencies for demo deployment
- Fast startup and teardown for development iteration
- `DB_CLOSE_DELAY=-1` keeps database alive between connections
- MySQL mode provides compatibility with common SQL patterns
- jOOQ's H2 dialect handles database-specific SQL generation

### Alternatives Considered
1. **PostgreSQL with Testcontainers**: More realistic but adds Docker dependency
2. **Embedded PostgreSQL**: Library overhead for minimal demo benefit
3. **H2 file-based**: Persistence not needed for demo, adds cleanup complexity

### Implementation Notes

#### application.yml Configuration
```yaml
spring:
  application:
    name: etl-microservice

  datasource:
    # H2 in-memory database
    url: jdbc:h2:mem:etldb;DB_CLOSE_DELAY=-1;MODE=MySQL
    username: sa
    password:
    driver-class-name: org.h2.Driver

  # H2 Console (optional, useful for debugging)
  h2:
    console:
      enabled: true
      path: /h2-console
      settings:
        web-allow-others: false

  # JPA/Hibernate settings (if using alongside jOOQ)
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: none
    show-sql: false

  # SQL initialization (if needed)
  sql:
    init:
      mode: never

# jOOQ configuration
jooq:
  sql-dialect: H2

# Logging
logging:
  level:
    org.jooq: INFO
    org.springframework.web: INFO
    com.velocirawesome.etl: DEBUG
```

#### Configuration URL Breakdown
- `jdbc:h2:mem:etldb` - In-memory database named "etldb"
- `DB_CLOSE_DELAY=-1` - Keep database alive until JVM shutdown
- `MODE=MySQL` - MySQL compatibility mode for familiar SQL syntax

#### jOOQ Configuration Bean
```java
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;

@Configuration
public class JooqConfig {

    @Bean
    public DSLContext dslContext(DataSource dataSource) {
        return DSL.using(dataSource, SQLDialect.H2);
    }
}
```

#### Alternative: Detailed jOOQ Configuration with Transaction Support
```java
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.springframework.boot.autoconfigure.jooq.JooqProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class JooqConfig {

    @Bean
    public TransactionAwareDataSourceProxy transactionAwareDataSource(DataSource dataSource) {
        return new TransactionAwareDataSourceProxy(dataSource);
    }

    @Bean
    public org.jooq.Configuration jooqConfiguration(
            TransactionAwareDataSourceProxy dataSource,
            PlatformTransactionManager transactionManager) {

        DefaultConfiguration config = new DefaultConfiguration();
        config.setSQLDialect(SQLDialect.H2);
        config.setDataSource(dataSource);

        return config;
    }

    @Bean
    public DSLContext dslContext(org.jooq.Configuration configuration) {
        return new DefaultDSLContext(configuration);
    }
}
```

#### Maven Dependencies
```xml
<dependencies>
    <!-- Spring Boot Starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- jOOQ -->
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

    <!-- JDBC -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
</dependencies>
```

#### Verification Query
```java
import org.jooq.DSLContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DatabaseVerifier implements CommandLineRunner {

    private final DSLContext dsl;

    public DatabaseVerifier(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void run(String... args) {
        String version = dsl.select(DSL.field("H2VERSION()"))
            .fetchOne(0, String.class);

        log.info("H2 Database initialized - version: {}", version);
    }
}
```

---

## 5. ETL Pipeline Error Handling

### Decision
Implement phase-bounded try-catch blocks with explicit status transitions, granular logging, and transaction rollback for the Load phase.

### Rationale
- Clear failure attribution to specific ETL phase
- Status tracking provides real-time job monitoring via API
- Logging at phase boundaries enables troubleshooting
- Transaction rollback prevents partial data corruption
- Graceful degradation allows system to continue serving other requests

### Alternatives Considered
1. **Global try-catch only**: Loses granularity on failure source
2. **Nested transactions per row**: Excessive overhead for demo workload
3. **Retry logic with exponential backoff**: Over-engineering for demo API stability assumptions
4. **Dead letter queue**: Requires message queue infrastructure

### Implementation Notes

#### Phase-Bounded Try-Catch Pattern
```java
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EtlJobService {

    private static final Logger log = LoggerFactory.getLogger(EtlJobService.class);

    private final CountriesApiService apiService;
    private final DataTransformService transformService;
    private final CountryJobRepository repository;
    private final JobStatusTracker statusTracker;

    @Async("etlTaskExecutor")
    public void executeJob(String jobId) {
        log.info("[Job:{}] Starting ETL pipeline", jobId);
        statusTracker.updateStatus(jobId, JobStatus.RUNNING);

        List<CountryApiResponse> extractedData = null;
        List<CountryDTO> transformedData = null;

        // EXTRACT Phase
        try {
            log.info("[Job:{}] EXTRACT phase started", jobId);
            extractedData = apiService.fetchAllCountries();
            log.info("[Job:{}] EXTRACT phase completed - {} records", jobId, extractedData.size());
        } catch (Exception e) {
            log.error("[Job:{}] EXTRACT phase failed", jobId, e);
            statusTracker.updateStatus(jobId, JobStatus.FAILED);
            statusTracker.updateErrorMessage(jobId, "Extract failed: " + e.getMessage());
            return; // Abort pipeline
        }

        // TRANSFORM Phase
        try {
            log.info("[Job:{}] TRANSFORM phase started", jobId);
            transformedData = transformService.transform(extractedData);
            log.info("[Job:{}] TRANSFORM phase completed - {} records", jobId, transformedData.size());
        } catch (Exception e) {
            log.error("[Job:{}] TRANSFORM phase failed", jobId, e);
            statusTracker.updateStatus(jobId, JobStatus.FAILED);
            statusTracker.updateErrorMessage(jobId, "Transform failed: " + e.getMessage());
            return; // Abort pipeline
        }

        // LOAD Phase
        try {
            log.info("[Job:{}] LOAD phase started", jobId);
            repository.createJobTable(jobId);
            repository.insertCountries(jobId, transformedData);
            log.info("[Job:{}] LOAD phase completed - {} records inserted", jobId, transformedData.size());
        } catch (Exception e) {
            log.error("[Job:{}] LOAD phase failed", jobId, e);
            statusTracker.updateStatus(jobId, JobStatus.FAILED);
            statusTracker.updateErrorMessage(jobId, "Load failed: " + e.getMessage());
            return; // Abort pipeline
        }

        log.info("[Job:{}] ETL pipeline completed successfully", jobId);
        statusTracker.updateStatus(jobId, JobStatus.COMPLETED);
    }
}
```

#### Transaction Handling for Load Phase
```java
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.jooq.DSLContext;

@Repository
public class CountryJobRepository {

    private final DSLContext dsl;

    public CountryJobRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional
    public void loadCountriesForJob(String jobId, List<CountryDTO> countries) {
        // DDL operation (CREATE TABLE) - not transactional in most databases
        createJobTable(jobId);

        // DML operation (INSERT) - transactional, will rollback on exception
        insertCountries(jobId, countries);
    }

    private void createJobTable(String jobId) {
        String tableName = "countries_job_" + jobId;
        dsl.execute(
            "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
            "  id VARCHAR(10) PRIMARY KEY, " +
            "  name VARCHAR(255) NOT NULL, " +
            "  capital VARCHAR(255), " +
            "  region VARCHAR(100), " +
            "  population BIGINT, " +
            "  area DECIMAL(15,2)" +
            ")"
        );
    }

    private void insertCountries(String jobId, List<CountryDTO> countries) {
        // Batch insert with automatic rollback on failure
        // (implementation from Section 1)
    }
}
```

#### Structured Logging with MDC (Mapped Diagnostic Context)
```java
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EtlJobService {

    @Async("etlTaskExecutor")
    public void executeJob(String jobId) {
        // Add job ID to logging context
        MDC.put("jobId", jobId);
        MDC.put("thread", Thread.currentThread().getName());

        try {
            log.info("Starting ETL pipeline");
            // ETL phases...
        } finally {
            // Clean up MDC to prevent memory leak
            MDC.clear();
        }
    }
}
```

#### Logback Configuration for Structured Logging
```xml
<!-- src/main/resources/logback-spring.xml -->
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [jobId:%X{jobId}] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="com.velocirawesome.etl" level="DEBUG"/>
    <logger name="org.jooq" level="INFO"/>
    <logger name="org.springframework.web" level="INFO"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

#### Partial Failure Recovery Pattern
```java
@Service
public class EtlJobService {

    @Async("etlTaskExecutor")
    public void executeJob(String jobId) {
        MDC.put("jobId", jobId);

        try {
            // EXTRACT
            List<CountryApiResponse> extractedData = extractPhase(jobId);
            if (extractedData == null) return; // Failed

            // TRANSFORM
            List<CountryDTO> transformedData = transformPhase(jobId, extractedData);
            if (transformedData == null) return; // Failed

            // LOAD
            loadPhase(jobId, transformedData);

        } finally {
            MDC.clear();
        }
    }

    private List<CountryApiResponse> extractPhase(String jobId) {
        try {
            log.info("EXTRACT phase started");
            List<CountryApiResponse> data = apiService.fetchAllCountries();
            log.info("EXTRACT phase completed - {} records", data.size());
            return data;
        } catch (Exception e) {
            log.error("EXTRACT phase failed", e);
            statusTracker.updateStatus(jobId, JobStatus.FAILED);
            statusTracker.updateErrorMessage(jobId, "Extract: " + e.getMessage());
            return null;
        }
    }

    private List<CountryDTO> transformPhase(String jobId, List<CountryApiResponse> input) {
        try {
            log.info("TRANSFORM phase started");
            List<CountryDTO> data = transformService.transform(input);
            log.info("TRANSFORM phase completed - {} records", data.size());
            return data;
        } catch (Exception e) {
            log.error("TRANSFORM phase failed", e);
            statusTracker.updateStatus(jobId, JobStatus.FAILED);
            statusTracker.updateErrorMessage(jobId, "Transform: " + e.getMessage());
            return null;
        }
    }

    private void loadPhase(String jobId, List<CountryDTO> input) {
        try {
            log.info("LOAD phase started");
            repository.loadCountriesForJob(jobId, input);
            log.info("LOAD phase completed - {} records", input.size());
            statusTracker.updateStatus(jobId, JobStatus.COMPLETED);
        } catch (Exception e) {
            log.error("LOAD phase failed", e);
            statusTracker.updateStatus(jobId, JobStatus.FAILED);
            statusTracker.updateErrorMessage(jobId, "Load: " + e.getMessage());
        }
    }
}
```

#### REST Controller Error Responses
```java
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final EtlJobService jobService;
    private final JobStatusTracker statusTracker;

    @PostMapping
    public ResponseEntity<JobResponse> createJob() {
        try {
            String jobId = UUID.randomUUID().toString();
            statusTracker.createJob(jobId);
            jobService.executeJob(jobId);

            return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new JobResponse(jobId, "Job submitted"));
        } catch (Exception e) {
            log.error("Failed to create job", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new JobResponse(null, "Failed to submit job: " + e.getMessage()));
        }
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobStatusInfo> getJobStatus(@PathVariable String jobId) {
        JobStatusInfo status = statusTracker.getStatus(jobId);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(status);
    }
}
```

#### Global Exception Handler
```java
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CountriesApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(CountriesApiException e) {
        log.error("API exception occurred", e);
        return ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(new ErrorResponse("External API error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        log.error("Unexpected exception occurred", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("Internal error", e.getMessage()));
    }
}

record ErrorResponse(String error, String message) {}
```

---

## Summary of Key Decisions

| Component | Decision | Key Benefit |
|-----------|----------|-------------|
| **Dynamic SQL** | jOOQ DSLContext with `table()` and `field()` | Type-safe DML with runtime table names |
| **HTTP Client** | Spring HttpServiceProxyFactory + RestClient | Declarative, blocking, zero reactive complexity |
| **Async Execution** | @Async with ThreadPoolTaskExecutor | Simple annotation-based, Spring-managed threading |
| **Status Tracking** | ConcurrentHashMap | Thread-safe multi-job tracking without locks |
| **Database** | H2 in-memory with DB_CLOSE_DELAY=-1 | Zero dependencies, fast iteration |
| **Error Handling** | Phase-bounded try-catch with MDC logging | Clear failure attribution and debugging |
| **Transactions** | @Transactional on Load phase only | Prevents partial data corruption |

---

## Implementation Checklist

- [ ] Configure H2 datasource in `application.yml`
- [ ] Create jOOQ `DSLContext` configuration bean
- [ ] Define `CountriesApiClient` interface with `@HttpExchange`
- [ ] Configure `HttpServiceProxyFactory` with `RestClient`
- [ ] Create `@EnableAsync` configuration with `ThreadPoolTaskExecutor`
- [ ] Implement `JobStatusTracker` with `ConcurrentHashMap`
- [ ] Implement `CountryJobRepository` with dynamic table creation
- [ ] Implement `EtlJobService` with phase-bounded error handling
- [ ] Add MDC logging configuration in `logback-spring.xml`
- [ ] Create REST controller endpoints (POST /jobs, GET /jobs/{id})
- [ ] Add global exception handler with `@RestControllerAdvice`
- [ ] Test full ETL pipeline with API integration

---

## Time Allocation Estimate (60-minute constraint)

| Task | Estimated Time | Notes |
|------|----------------|-------|
| Project setup + dependencies | 5 min | Spring Initializr with jOOQ, H2, Web |
| H2 + jOOQ configuration | 5 min | application.yml + config beans |
| HTTP client setup | 10 min | Interface + factory + DTOs |
| Async configuration | 5 min | @EnableAsync + executor bean |
| Status tracker implementation | 5 min | ConcurrentHashMap wrapper |
| Repository layer | 10 min | Dynamic table creation + batch insert |
| Service layer (ETL logic) | 10 min | Phase-bounded execution |
| Controller + error handling | 5 min | REST endpoints + global handler |
| Testing + debugging | 5 min | Manual API testing |
| **Total** | **60 min** | |

---

## References

- [Spring Boot 7.0.0 Documentation](https://docs.spring.io/spring-boot/docs/3.2.x/reference/html/)
- [jOOQ Manual - Dynamic SQL](https://www.jooq.org/doc/latest/manual/sql-building/dynamic-sql/)
- [Spring Framework - HTTP Interface](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-http-interface)
- [Spring Framework - @Async](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-async)
- [H2 Database Documentation](https://www.h2database.com/html/main.html)
- [RESTCountries API Documentation](https://restcountries.com/)

---

*Generated: 2025-12-05 for ETL Microservice (001-shell-app)*
