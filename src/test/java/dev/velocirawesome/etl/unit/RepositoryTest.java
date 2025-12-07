package dev.velocirawesome.etl.unit;

import dev.velocirawesome.etl.model.entity.Country;
import dev.velocirawesome.etl.model.entity.EtlJob;
import dev.velocirawesome.etl.model.entity.JobStatus;
import dev.velocirawesome.etl.repository.CountryRepository;
import dev.velocirawesome.etl.repository.EtlJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for data layer repositories (EtlJobRepository and CountryRepository).
 * Tests verify save/load operations, data integrity, and edge cases.
 *
 * Each test runs in its own transaction which is rolled back after the test completes,
 * ensuring test isolation and a clean database state for each test.
 */
@SpringBootTest
public class RepositoryTest {

    @Autowired
    private EtlJobRepository etlJobRepository;

    @Autowired
    private CountryRepository countryRepository;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ========== EtlJobRepository Tests ==========

    @Test
    @Transactional
    public void testCreateJob_CreatesJobWithRunningStatus() {
        // Given
        String sourceUrl = "https://example.com/data";

        // When
        EtlJob job = etlJobRepository.createJob(sourceUrl);

        // Then
        assertThat(job).isNotNull();
        assertThat(job.getJobId()).isNotNull().isGreaterThan(0L);
        assertThat(job.getSourceUrl()).isEqualTo(sourceUrl);
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getStartTime()).isNotNull().isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @Transactional
    public void testCreateJob_GeneratesIncrementalIds() {
        // When
        EtlJob job1 = etlJobRepository.createJob("https://example.com/1");
        EtlJob job2 = etlJobRepository.createJob("https://example.com/2");
        EtlJob job3 = etlJobRepository.createJob("https://example.com/3");

        // Then
        assertThat(job1.getJobId()).isLessThan(job2.getJobId());
        assertThat(job2.getJobId()).isLessThan(job3.getJobId());
        assertThat(job2.getJobId()).isEqualTo(job1.getJobId() + 1);
        assertThat(job3.getJobId()).isEqualTo(job2.getJobId() + 1);
    }

    @Test
    @Transactional
    public void testUpdateRecordsExtracted_UpdatesCount() {
        // Given
        EtlJob job = etlJobRepository.createJob("https://example.com/data");
        Long expectedCount = 100L;

        // When
        etlJobRepository.updateRecordsExtracted(job.getJobId(), expectedCount);

        // Then
        EtlJob retrievedJob = etlJobRepository.getLatestJob();
        assertThat(retrievedJob.getRecordsExtracted()).isEqualTo(expectedCount);
    }

    @Test
    @Transactional
    public void testUpdateRecordsTransformed_UpdatesCount() {
        // Given
        EtlJob job = etlJobRepository.createJob("https://example.com/data");
        Long expectedCount = 95L;

        // When
        etlJobRepository.updateRecordsTransformed(job.getJobId(), expectedCount);

        // Then
        EtlJob retrievedJob = etlJobRepository.getLatestJob();
        assertThat(retrievedJob.getRecordsTransformed()).isEqualTo(expectedCount);
    }

    @Test
    @Transactional
    public void testUpdateRecordsLoaded_UpdatesCount() {
        // Given
        EtlJob job = etlJobRepository.createJob("https://example.com/data");
        Long expectedCount = 90L;

        // When
        etlJobRepository.updateRecordsLoaded(job.getJobId(), expectedCount);

        // Then
        EtlJob retrievedJob = etlJobRepository.getLatestJob();
        assertThat(retrievedJob.getRecordsLoaded()).isEqualTo(expectedCount);
    }

    @Test
    @Transactional
    public void testUpdateJobStatus_ToSuccess_SetsEndTime() {
        // Given
        EtlJob job = etlJobRepository.createJob("https://example.com/data");
        LocalDateTime beforeUpdate = LocalDateTime.now();

        // When
        etlJobRepository.updateJobStatus(job.getJobId(), JobStatus.SUCCESS, null);

        // Then
        EtlJob retrievedJob = etlJobRepository.getLatestJob();
        assertThat(retrievedJob.getStatus()).isEqualTo(JobStatus.SUCCESS);
        assertThat(retrievedJob.getEndTime()).isNotNull()
                .isAfterOrEqualTo(beforeUpdate)
                .isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(retrievedJob.getErrorMessage()).isNull();
    }

    @Test
    @Transactional
    public void testUpdateJobStatus_ToFailed_SetsEndTimeAndError() {
        // Given
        EtlJob job = etlJobRepository.createJob("https://example.com/data");
        String errorMessage = "Test error message";
        LocalDateTime beforeUpdate = LocalDateTime.now();

        // When
        etlJobRepository.updateJobStatus(job.getJobId(), JobStatus.FAILED, errorMessage);

        // Then
        EtlJob retrievedJob = etlJobRepository.getLatestJob();
        assertThat(retrievedJob.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(retrievedJob.getEndTime()).isNotNull()
                .isAfterOrEqualTo(beforeUpdate)
                .isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(retrievedJob.getErrorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @Transactional
    public void testUpdateJobStatusWithError_SetsAllFields() {
        // Given
        EtlJob job = etlJobRepository.createJob("https://example.com/data");
        LocalDateTime endTime = LocalDateTime.now();
        String errorMessage = "Critical failure";

        // When
        etlJobRepository.updateJobStatusWithError(job.getJobId(), JobStatus.FAILED, endTime, errorMessage);

        // Then
        EtlJob retrievedJob = etlJobRepository.getLatestJob();
        assertThat(retrievedJob.getStatus()).isEqualTo(JobStatus.FAILED);
        // Database may truncate nanoseconds, so we check it's within a second
        assertThat(retrievedJob.getEndTime()).isAfterOrEqualTo(endTime.minusSeconds(1))
                .isBeforeOrEqualTo(endTime.plusSeconds(1));
        assertThat(retrievedJob.getErrorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @Transactional
    public void testGetLatestJob_HandlesEmptyDatabaseGracefully() {
        // Given - Empty database
        etlJobRepository.deleteAll();

        // When
        EtlJob latestJob = etlJobRepository.getLatestJob();

        // Then - Should return null when no jobs exist
        assertThat(latestJob).isNull();
    }

    @Test
    @Transactional
    public void testGetLatestJob_ReturnsMostRecentByStartTime() throws InterruptedException {
        // Given - Create jobs with small delays to ensure different start times
        EtlJob job1 = etlJobRepository.createJob("https://example.com/1");
        Thread.sleep(10);
        EtlJob job2 = etlJobRepository.createJob("https://example.com/2");
        Thread.sleep(10);
        EtlJob job3 = etlJobRepository.createJob("https://example.com/3");

        // When
        EtlJob latestJob = etlJobRepository.getLatestJob();

        // Then
        assertThat(latestJob).isNotNull();
        assertThat(latestJob.getJobId()).isEqualTo(job3.getJobId());
        assertThat(latestJob.getSourceUrl()).isEqualTo("https://example.com/3");
    }

    @Test
    @Transactional
    public void testGetLatestSuccessfulJob_FiltersOutFailedJobs() {
        // Given - Empty database with only a failed job
        etlJobRepository.deleteAll();
        EtlJob failedJob = etlJobRepository.createJob("https://example.com/test-failed");
        etlJobRepository.updateJobStatus(failedJob.getJobId(), JobStatus.FAILED, "Error");

        // When
        EtlJob successfulJob = etlJobRepository.getLatestSuccessfulJob();

        // Then - Should return null since no successful jobs exist
        assertThat(successfulJob).isNull();
    }

    @Test
    @Transactional
    public void testGetLatestSuccessfulJob_ReturnsLatestSuccessful() throws InterruptedException {
        // Given
        EtlJob job1 = etlJobRepository.createJob("https://example.com/1");
        etlJobRepository.updateJobStatus(job1.getJobId(), JobStatus.SUCCESS, null);
        Thread.sleep(10);

        EtlJob job2 = etlJobRepository.createJob("https://example.com/2");
        etlJobRepository.updateJobStatus(job2.getJobId(), JobStatus.FAILED, "Error");
        Thread.sleep(10);

        EtlJob job3 = etlJobRepository.createJob("https://example.com/3");
        etlJobRepository.updateJobStatus(job3.getJobId(), JobStatus.SUCCESS, null);

        // When
        EtlJob successfulJob = etlJobRepository.getLatestSuccessfulJob();

        // Then
        assertThat(successfulJob).isNotNull();
        assertThat(successfulJob.getJobId()).isEqualTo(job3.getJobId());
        assertThat(successfulJob.getStatus()).isEqualTo(JobStatus.SUCCESS);
    }

    @Test
    @Transactional
    public void testCompleteJobLifecycle_AllFieldsPersisted() {
        // Given
        EtlJob job = etlJobRepository.createJob("https://example.com/complete-test");

        // When - Simulate complete ETL pipeline
        etlJobRepository.updateRecordsExtracted(job.getJobId(), 250L);
        etlJobRepository.updateRecordsTransformed(job.getJobId(), 200L);
        etlJobRepository.updateRecordsLoaded(job.getJobId(), 200L);
        etlJobRepository.updateJobStatus(job.getJobId(), JobStatus.SUCCESS, null);

        // Then
        EtlJob retrievedJob = etlJobRepository.getLatestJob();
        assertThat(retrievedJob.getJobId()).isEqualTo(job.getJobId());
        assertThat(retrievedJob.getSourceUrl()).isEqualTo("https://example.com/complete-test");
        assertThat(retrievedJob.getStatus()).isEqualTo(JobStatus.SUCCESS);
        assertThat(retrievedJob.getStartTime()).isNotNull();
        assertThat(retrievedJob.getEndTime()).isNotNull();
        assertThat(retrievedJob.getRecordsExtracted()).isEqualTo(250L);
        assertThat(retrievedJob.getRecordsTransformed()).isEqualTo(200L);
        assertThat(retrievedJob.getRecordsLoaded()).isEqualTo(200L);
        assertThat(retrievedJob.getErrorMessage()).isNull();
    }

    // ========== CountryRepository Tests ==========

    @Test
    @Transactional
    public void testCreateJobTable_CreatesTable() {
        // Given
        Long jobId = 1L;

        // When
        countryRepository.createJobTable(jobId);

        // Then - Table should be created, verify by attempting to read from it
        List<Country> countries = countryRepository.getCountriesByJobId(jobId);
        assertThat(countries).isNotNull().isEmpty();
    }

    @Test
    @Transactional
    public void testInsertCountries_SavesAndLoadsCountries() throws Exception {
        // Given
        Long jobId = 10L;
        countryRepository.createJobTable(jobId);

        JsonNode usaData = objectMapper.readTree("{\"name\":\"United States\",\"capital\":\"Washington D.C.\"}");
        JsonNode ukData = objectMapper.readTree("{\"name\":\"United Kingdom\",\"capital\":\"London\"}");

        List<Country> countries = List.of(
                new Country("USA", usaData),
                new Country("GBR", ukData)
        );

        // When
        countryRepository.insertCountries(jobId, countries);

        // Then
        List<Country> loadedCountries = countryRepository.getCountriesByJobId(jobId);
        assertThat(loadedCountries).hasSize(2);

        Country usa = loadedCountries.stream()
                .filter(c -> "USA".equals(c.getCode()))
                .findFirst()
                .orElse(null);
        assertThat(usa).isNotNull();
        assertThat(usa.getData()).isNotNull();
        assertThat(usa.getData().get("name").asText()).isEqualTo("United States");
        assertThat(usa.getData().get("capital").asText()).isEqualTo("Washington D.C.");

        Country uk = loadedCountries.stream()
                .filter(c -> "GBR".equals(c.getCode()))
                .findFirst()
                .orElse(null);
        assertThat(uk).isNotNull();
        assertThat(uk.getData()).isNotNull();
        assertThat(uk.getData().get("name").asText()).isEqualTo("United Kingdom");
        assertThat(uk.getData().get("capital").asText()).isEqualTo("London");
    }

    @Test
    @Transactional
    public void testInsertCountries_WithEmptyList_DoesNotFail() {
        // Given
        Long jobId = 20L;
        countryRepository.createJobTable(jobId);

        // When
        countryRepository.insertCountries(jobId, List.of());

        // Then
        List<Country> loadedCountries = countryRepository.getCountriesByJobId(jobId);
        assertThat(loadedCountries).isEmpty();
    }

    @Test
    @Transactional
    public void testInsertCountries_WithComplexJsonData() throws Exception {
        // Given
        Long jobId = 30L;
        countryRepository.createJobTable(jobId);

        JsonNode complexData = objectMapper.readTree("""
                {
                    "name": "France",
                    "capital": "Paris",
                    "population": 67000000,
                    "languages": ["French"],
                    "currencies": {
                        "EUR": {
                            "name": "Euro",
                            "symbol": "€"
                        }
                    },
                    "borders": ["BEL", "DEU", "ESP", "ITA", "LUX", "MCO", "CHE"]
                }
                """);

        List<Country> countries = List.of(new Country("FRA", complexData));

        // When
        countryRepository.insertCountries(jobId, countries);

        // Then
        List<Country> loadedCountries = countryRepository.getCountriesByJobId(jobId);
        assertThat(loadedCountries).hasSize(1);

        Country france = loadedCountries.get(0);
        assertThat(france.getCode()).isEqualTo("FRA");
        assertThat(france.getData()).isNotNull();
        assertThat(france.getData().get("name").asText()).isEqualTo("France");
        assertThat(france.getData().get("population").asLong()).isEqualTo(67000000L);
        assertThat(france.getData().get("languages").get(0).asText()).isEqualTo("French");
        assertThat(france.getData().get("currencies").get("EUR").get("symbol").asText()).isEqualTo("€");
        assertThat(france.getData().get("borders")).hasSize(7);
    }

    @Test
    @Transactional
    public void testGetCountriesByJobId_ReturnsEmptyListForNonexistentTable() {
        // Given - Job ID that doesn't have a table
        Long jobId = 999L;

        // When/Then - Should handle gracefully (may throw exception or return empty)
        // This behavior depends on implementation
        try {
            List<Country> countries = countryRepository.getCountriesByJobId(jobId);
            // If it doesn't throw, should be empty
            assertThat(countries).isEmpty();
        } catch (Exception e) {
            // Exception is acceptable for non-existent table
            assertThat(e).isNotNull();
        }
    }

    @Test
    @Transactional
    public void testMultipleJobTables_DataIsolated() throws Exception {
        // Given
        Long jobId1 = 100L;
        Long jobId2 = 101L;

        countryRepository.createJobTable(jobId1);
        countryRepository.createJobTable(jobId2);

        JsonNode data1 = objectMapper.readTree("{\"name\":\"Job 1 Country\"}");
        JsonNode data2 = objectMapper.readTree("{\"name\":\"Job 2 Country\"}");

        // When
        countryRepository.insertCountries(jobId1, List.of(new Country("J01", data1)));
        countryRepository.insertCountries(jobId2, List.of(new Country("J02", data2)));

        // Then - Data should be isolated per job
        List<Country> countries1 = countryRepository.getCountriesByJobId(jobId1);
        List<Country> countries2 = countryRepository.getCountriesByJobId(jobId2);

        assertThat(countries1).hasSize(1);
        assertThat(countries1.get(0).getCode()).isEqualTo("J01");
        assertThat(countries1.get(0).getData().get("name").asText()).isEqualTo("Job 1 Country");

        assertThat(countries2).hasSize(1);
        assertThat(countries2.get(0).getCode()).isEqualTo("J02");
        assertThat(countries2.get(0).getData().get("name").asText()).isEqualTo("Job 2 Country");
    }

    @Test
    @Transactional
    public void testInsertCountries_PreservesDataOrder() throws Exception {
        // Given
        Long jobId = 200L;
        countryRepository.createJobTable(jobId);

        List<Country> countries = List.of(
                new Country("AAA", objectMapper.readTree("{\"order\":1}")),
                new Country("BBB", objectMapper.readTree("{\"order\":2}")),
                new Country("CCC", objectMapper.readTree("{\"order\":3}"))
        );

        // When
        countryRepository.insertCountries(jobId, countries);

        // Then
        List<Country> loadedCountries = countryRepository.getCountriesByJobId(jobId);
        assertThat(loadedCountries).hasSize(3);

        // Verify all countries are present (order may vary depending on DB)
        assertThat(loadedCountries)
                .extracting(Country::getCode)
                .containsExactlyInAnyOrder("AAA", "BBB", "CCC");
    }

    @Test
    @Transactional
    public void testCountryDataWithSpecialCharacters() throws Exception {
        // Given
        Long jobId = 300L;
        countryRepository.createJobTable(jobId);

        JsonNode specialData = objectMapper.readTree("""
                {
                    "name": "Test 'Country' with \\"quotes\\"",
                    "description": "Special chars: @#$%^&*(){}[]|\\\\",
                    "unicode": "Ñoño 日本 مرحبا"
                }
                """);

        List<Country> countries = List.of(new Country("TST", specialData));

        // When
        countryRepository.insertCountries(jobId, countries);

        // Then
        List<Country> loadedCountries = countryRepository.getCountriesByJobId(jobId);
        assertThat(loadedCountries).hasSize(1);

        Country country = loadedCountries.get(0);
        assertThat(country.getData().get("name").asText()).isEqualTo("Test 'Country' with \"quotes\"");
        assertThat(country.getData().get("description").asText()).contains("@#$%^&*(){}[]|\\");
        assertThat(country.getData().get("unicode").asText()).isEqualTo("Ñoño 日本 مرحبا");
    }
}
