package dev.velocirawesome.etl.unit;

import dev.velocirawesome.etl.controller.CountryController;
import dev.velocirawesome.etl.model.entity.Country;
import dev.velocirawesome.etl.model.entity.EtlJob;
import dev.velocirawesome.etl.model.entity.JobStatus;
import dev.velocirawesome.etl.repository.CountryRepository;
import dev.velocirawesome.etl.repository.EtlJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CountryController.class)
public class CountryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EtlJobRepository jobRepository;

    @MockitoBean
    private CountryRepository countryRepository;

    private EtlJob mockJob;
    private List<Country> mockCountries;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() throws Exception {
        jsonMapper = JsonMapper.builder().build();

        // Set up mock job
        mockJob = new EtlJob();
        mockJob.setJobId(1L);
        mockJob.setSourceUrl("https://example.com/data");
        mockJob.setStatus(JobStatus.SUCCESS);
        mockJob.setStartTime(LocalDateTime.now().minusMinutes(5));
        mockJob.setEndTime(LocalDateTime.now());
        mockJob.setRecordsExtracted(3L);
        mockJob.setRecordsTransformed(3L);
        mockJob.setRecordsLoaded(3L);

        // Set up mock countries
        mockCountries = new ArrayList<>();

        JsonNode usaData = jsonMapper.readTree("{\"name\":\"United States\",\"population\":331000000}");
        Country usa = new Country("USA", usaData);
        mockCountries.add(usa);

        JsonNode gbrData = jsonMapper.readTree("{\"name\":\"United Kingdom\",\"population\":67000000}");
        Country gbr = new Country("GBR", gbrData);
        mockCountries.add(gbr);

        JsonNode fraData = jsonMapper.readTree("{\"name\":\"France\",\"population\":67000000}");
        Country fra = new Country("FRA", fraData);
        mockCountries.add(fra);
    }

    // ===== GET /country Tests =====

    @Test
    void testGetCountries_Success() throws Exception {
        // Arrange
        when(jobRepository.getLatestSuccessfulJob()).thenReturn(mockJob);
        when(countryRepository.getCountriesByJobId(1L)).thenReturn(mockCountries);

        // Act & Assert
        mockMvc.perform(get("/country"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].code").value("USA"))
                .andExpect(jsonPath("$[0].data.name").value("United States"))
                .andExpect(jsonPath("$[0].data.population").value(331000000))
                .andExpect(jsonPath("$[1].code").value("GBR"))
                .andExpect(jsonPath("$[1].data.name").value("United Kingdom"))
                .andExpect(jsonPath("$[2].code").value("FRA"))
                .andExpect(jsonPath("$[2].data.name").value("France"));

        // Verify
        verify(jobRepository, times(1)).getLatestSuccessfulJob();
        verify(countryRepository, times(1)).getCountriesByJobId(1L);
    }

    @Test
    void testGetCountries_EmptyList() throws Exception {
        // Arrange - Job exists but no countries loaded
        when(jobRepository.getLatestSuccessfulJob()).thenReturn(mockJob);
        when(countryRepository.getCountriesByJobId(1L)).thenReturn(new ArrayList<>());

        // Act & Assert
        mockMvc.perform(get("/country"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)))
                .andExpect(jsonPath("$", is(empty())));

        verify(jobRepository, times(1)).getLatestSuccessfulJob();
        verify(countryRepository, times(1)).getCountriesByJobId(1L);
    }

    @Test
    void testGetCountries_NoSuccessfulJobFound() throws Exception {
        // Arrange
        when(jobRepository.getLatestSuccessfulJob()).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/country"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No successful ETL jobs found"));

        // Verify repository was called but country repo was not
        verify(jobRepository, times(1)).getLatestSuccessfulJob();
        verify(countryRepository, never()).getCountriesByJobId(anyLong());
    }

    @Test
    void testGetCountries_JobRepositoryException() throws Exception {
        // Arrange
        when(jobRepository.getLatestSuccessfulJob())
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        mockMvc.perform(get("/country"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Database connection failed"));

        verify(jobRepository, times(1)).getLatestSuccessfulJob();
        verify(countryRepository, never()).getCountriesByJobId(anyLong());
    }

    @Test
    void testGetCountries_CountryRepositoryException() throws Exception {
        // Arrange
        when(jobRepository.getLatestSuccessfulJob()).thenReturn(mockJob);
        when(countryRepository.getCountriesByJobId(1L))
                .thenThrow(new RuntimeException("Table not found"));

        // Act & Assert
        mockMvc.perform(get("/country"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Table not found"));

        verify(jobRepository, times(1)).getLatestSuccessfulJob();
        verify(countryRepository, times(1)).getCountriesByJobId(1L);
    }

    @Test
    void testGetCountries_SingleCountry() throws Exception {
        // Arrange
        List<Country> singleCountry = new ArrayList<>();
        singleCountry.add(mockCountries.get(0)); // Only USA

        when(jobRepository.getLatestSuccessfulJob()).thenReturn(mockJob);
        when(countryRepository.getCountriesByJobId(1L)).thenReturn(singleCountry);

        // Act & Assert
        mockMvc.perform(get("/country"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("USA"))
                .andExpect(jsonPath("$[0].data.name").value("United States"));
    }

    @Test
    void testGetCountries_ComplexJsonData() throws Exception {
        // Arrange - Country with nested JSON data
        JsonNode complexData = jsonMapper.readTree("""
                {
                    "name": {
                        "common": "Canada",
                        "official": "Canada"
                    },
                    "capital": ["Ottawa"],
                    "population": 38000000,
                    "languages": {
                        "eng": "English",
                        "fra": "French"
                    }
                }
                """);
        Country complexCountry = new Country("CAN", complexData);

        List<Country> complexList = new ArrayList<>();
        complexList.add(complexCountry);

        when(jobRepository.getLatestSuccessfulJob()).thenReturn(mockJob);
        when(countryRepository.getCountriesByJobId(1L)).thenReturn(complexList);

        // Act & Assert
        mockMvc.perform(get("/country"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("CAN"))
                .andExpect(jsonPath("$[0].data.name.common").value("Canada"))
                .andExpect(jsonPath("$[0].data.name.official").value("Canada"))
                .andExpect(jsonPath("$[0].data.capital[0]").value("Ottawa"))
                .andExpect(jsonPath("$[0].data.languages.eng").value("English"))
                .andExpect(jsonPath("$[0].data.languages.fra").value("French"));
    }

    // ===== Exception Handler Tests =====

    @Test
    void testExceptionHandler_GenericException() throws Exception {
        // Arrange
        when(jobRepository.getLatestSuccessfulJob())
                .thenThrow(new IllegalStateException("Unexpected error"));

        // Act & Assert
        mockMvc.perform(get("/country"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Unexpected error"));
    }
}
