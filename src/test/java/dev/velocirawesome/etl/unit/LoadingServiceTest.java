package dev.velocirawesome.etl.unit;

import dev.velocirawesome.etl.exception.LoadingException;
import dev.velocirawesome.etl.model.entity.Country;
import dev.velocirawesome.etl.repository.CountryRepository;
import dev.velocirawesome.etl.service.LoadingService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LoadingService.
 * Tests verify transactional loading behavior, error handling,
 * and interaction with CountryRepository.
 */
@ExtendWith(MockitoExtension.class)
public class LoadingServiceTest {

    @Mock
    private CountryRepository countryRepository;

    @InjectMocks
    private LoadingService loadingService;

    private JsonMapper jsonMapper;
    private List<Country> mockCountries;

    @BeforeEach
    void setUp() throws Exception {
        jsonMapper = JsonMapper.builder().build();

        // Set up mock countries
        mockCountries = new ArrayList<>();

        JsonNode usaData = jsonMapper.readTree("{\"name\":\"United States\",\"population\":331000000}");
        mockCountries.add(new Country("USA", usaData));

        JsonNode canData = jsonMapper.readTree("{\"name\":\"Canada\",\"population\":38000000}");
        mockCountries.add(new Country("CAN", canData));

        JsonNode mexData = jsonMapper.readTree("{\"name\":\"Mexico\",\"population\":128000000}");
        mockCountries.add(new Country("MEX", mexData));
    }

    // ===== Success Cases =====

    @Test
    void testLoad_Success_CreatesTableAndInsertsCountries() {
        // Given
        Long jobId = 1L;
        doNothing().when(countryRepository).createJobTable(jobId);
        doNothing().when(countryRepository).insertCountries(jobId, mockCountries);

        // When
        loadingService.load(jobId, mockCountries);

        // Then
        verify(countryRepository, times(1)).createJobTable(jobId);
        verify(countryRepository, times(1)).insertCountries(jobId, mockCountries);
    }

    @Test
    void testLoad_EmptyList_CreatesTableWithNoInserts() {
        // Given
        Long jobId = 2L;
        List<Country> emptyList = new ArrayList<>();
        doNothing().when(countryRepository).createJobTable(jobId);
        doNothing().when(countryRepository).insertCountries(jobId, emptyList);

        // When
        loadingService.load(jobId, emptyList);

        // Then
        verify(countryRepository, times(1)).createJobTable(jobId);
        verify(countryRepository, times(1)).insertCountries(jobId, emptyList);
    }

    @Test
    void testLoad_SingleCountry_Succeeds() throws Exception {
        // Given
        Long jobId = 3L;
        JsonNode singleData = jsonMapper.readTree("{\"name\":\"France\",\"population\":67000000}");
        List<Country> singleCountry = List.of(new Country("FRA", singleData));

        doNothing().when(countryRepository).createJobTable(jobId);
        doNothing().when(countryRepository).insertCountries(jobId, singleCountry);

        // When
        loadingService.load(jobId, singleCountry);

        // Then
        verify(countryRepository, times(1)).createJobTable(jobId);
        verify(countryRepository, times(1)).insertCountries(jobId, singleCountry);
    }

    @Test
    void testLoad_LargeDataset_HandlesSuccessfully() throws Exception {
        // Given
        Long jobId = 4L;
        List<Country> largeDataset = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            JsonNode data = jsonMapper.readTree(String.format("{\"name\":\"Country%d\",\"population\":%d}", i, i * 1000000));
            largeDataset.add(new Country(String.format("C%02d", i), data));
        }

        doNothing().when(countryRepository).createJobTable(jobId);
        doNothing().when(countryRepository).insertCountries(jobId, largeDataset);

        // When
        loadingService.load(jobId, largeDataset);

        // Then
        verify(countryRepository, times(1)).createJobTable(jobId);
        verify(countryRepository, times(1)).insertCountries(jobId, largeDataset);
    }

    // ===== Error Cases =====

    @Test
    void testLoad_TableCreationFails_ThrowsLoadingException() {
        // Given
        Long jobId = 5L;
        doThrow(new RuntimeException("Table creation failed"))
                .when(countryRepository).createJobTable(jobId);

        // When/Then
        assertThatThrownBy(() -> loadingService.load(jobId, mockCountries))
                .isInstanceOf(LoadingException.class)
                .hasMessageContaining("Failed to load countries for job " + jobId)
                .hasCauseInstanceOf(RuntimeException.class);

        // Verify table creation was attempted but insert was not
        verify(countryRepository, times(1)).createJobTable(jobId);
        verify(countryRepository, never()).insertCountries(anyLong(), anyList());
    }

    @Test
    void testLoad_InsertFails_ThrowsLoadingException() {
        // Given
        Long jobId = 6L;
        doNothing().when(countryRepository).createJobTable(jobId);
        doThrow(new RuntimeException("Insert failed"))
                .when(countryRepository).insertCountries(jobId, mockCountries);

        // When/Then
        assertThatThrownBy(() -> loadingService.load(jobId, mockCountries))
                .isInstanceOf(LoadingException.class)
                .hasMessageContaining("Failed to load countries for job " + jobId)
                .hasCauseInstanceOf(RuntimeException.class);

        // Verify both operations were attempted
        verify(countryRepository, times(1)).createJobTable(jobId);
        verify(countryRepository, times(1)).insertCountries(jobId, mockCountries);
    }

    @Test
    void testLoad_DatabaseConnectionFailure_ThrowsLoadingException() {
        // Given
        Long jobId = 7L;
        doThrow(new RuntimeException("Database connection lost"))
                .when(countryRepository).createJobTable(jobId);

        // When/Then
        assertThatThrownBy(() -> loadingService.load(jobId, mockCountries))
                .isInstanceOf(LoadingException.class)
                .hasMessageContaining("Failed to load countries")
                .hasCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("Database connection lost");
    }

    @Test
    void testLoad_NullJobId_HandledByRepository() {
        // Given
        Long nullJobId = null;
        doThrow(new IllegalArgumentException("Job ID cannot be null"))
                .when(countryRepository).createJobTable(nullJobId);

        // When/Then
        assertThatThrownBy(() -> loadingService.load(nullJobId, mockCountries))
                .isInstanceOf(LoadingException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    // ===== Transactional Behavior Tests =====

    @Test
    void testLoad_OperationsInCorrectOrder() {
        // Given
        Long jobId = 8L;
        doNothing().when(countryRepository).createJobTable(jobId);
        doNothing().when(countryRepository).insertCountries(jobId, mockCountries);

        // When
        loadingService.load(jobId, mockCountries);

        // Then - Verify order of operations using InOrder
        var inOrder = inOrder(countryRepository);
        inOrder.verify(countryRepository).createJobTable(jobId);
        inOrder.verify(countryRepository).insertCountries(jobId, mockCountries);
    }

    @Test
    void testLoad_CalledWithCorrectParameters() {
        // Given
        Long jobId = 9L;
        doNothing().when(countryRepository).createJobTable(jobId);
        doNothing().when(countryRepository).insertCountries(jobId, mockCountries);

        // When
        loadingService.load(jobId, mockCountries);

        // Then - Verify exact parameters
        verify(countryRepository).createJobTable(eq(jobId));
        verify(countryRepository).insertCountries(eq(jobId), eq(mockCountries));
    }

    // ===== Edge Cases =====

    @Test
    void testLoad_SpecialCharactersInData_HandledCorrectly() throws Exception {
        // Given
        Long jobId = 10L;
        JsonNode specialData = jsonMapper.readTree(
                "{\"name\":\"Test 'Country' with \\\"quotes\\\"\",\"unicode\":\"Ñoño 日本\"}"
        );
        List<Country> specialCountries = List.of(new Country("TST", specialData));

        doNothing().when(countryRepository).createJobTable(jobId);
        doNothing().when(countryRepository).insertCountries(jobId, specialCountries);

        // When
        loadingService.load(jobId, specialCountries);

        // Then
        verify(countryRepository, times(1)).createJobTable(jobId);
        verify(countryRepository, times(1)).insertCountries(jobId, specialCountries);
    }

    @Test
    void testLoad_ComplexNestedJson_HandledCorrectly() throws Exception {
        // Given
        Long jobId = 11L;
        JsonNode complexData = jsonMapper.readTree("""
                {
                    "name": {"common": "France", "official": "French Republic"},
                    "capital": ["Paris"],
                    "population": 67000000,
                    "languages": {"fra": "French"},
                    "currencies": {"EUR": {"name": "Euro", "symbol": "€"}},
                    "borders": ["BEL", "DEU", "ESP"]
                }
                """);
        List<Country> complexCountries = List.of(new Country("FRA", complexData));

        doNothing().when(countryRepository).createJobTable(jobId);
        doNothing().when(countryRepository).insertCountries(jobId, complexCountries);

        // When
        loadingService.load(jobId, complexCountries);

        // Then
        verify(countryRepository, times(1)).createJobTable(jobId);
        verify(countryRepository, times(1)).insertCountries(jobId, complexCountries);
    }

    @Test
    void testLoad_MultipleJobsInSequence_EachCreatesOwnTable() {
        // Given
        Long jobId1 = 100L;
        Long jobId2 = 101L;
        Long jobId3 = 102L;

        // When
        loadingService.load(jobId1, mockCountries);
        loadingService.load(jobId2, mockCountries);
        loadingService.load(jobId3, mockCountries);

        // Then - Each job should create its own table
        verify(countryRepository, times(1)).createJobTable(jobId1);
        verify(countryRepository, times(1)).createJobTable(jobId2);
        verify(countryRepository, times(1)).createJobTable(jobId3);
        verify(countryRepository, times(3)).insertCountries(anyLong(), eq(mockCountries));
    }
}
