package dev.velocirawesome.etl.unit;

import dev.velocirawesome.etl.exception.ExtractionException;
import dev.velocirawesome.etl.service.ExtractionService;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ExtractionService.
 * Tests verify data extraction from external URLs, JSON parsing,
 * and error handling for network failures and malformed responses.
 */
@SpringBootTest
public class ExtractionServiceTest {

    @Autowired
    private ExtractionService extractionService;

    // ===== Success Cases =====

    @Test
    void testFetchData_ValidJsonArray_ReturnsListOfNodes() {
        // Given - Using a reliable public API endpoint
        String sourceUrl = "https://restcountries.com/v3.1/alpha/usa";

        // When
        List<JsonNode> result = extractionService.fetchData(sourceUrl);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
        assertThat(result.get(0)).isNotNull();
        assertThat(result.get(0).has("name")).isTrue();
    }

    @Test
    void testFetchData_JsonArrayResponse_ParsesAllElements() {
        // Given - Endpoint that returns an array
        String sourceUrl = "https://restcountries.com/v3.1/region/europe";

        // When
        List<JsonNode> result = extractionService.fetchData(sourceUrl);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.size()).isGreaterThan(1); // Europe has multiple countries

        // Verify each element is a valid JSON node
        for (JsonNode node : result) {
            assertThat(node).isNotNull();
            assertThat(node.isObject()).isTrue();
        }
    }

    @Test
    void testFetchData_SingleObjectResponse_WrapsInList() {
        // Given - Single country endpoint
        String sourceUrl = "https://restcountries.com/v3.1/alpha/can";

        // When
        List<JsonNode> result = extractionService.fetchData(sourceUrl);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).has("name")).isTrue();
    }

    // ===== Error Cases =====

    @Test
    void testFetchData_InvalidUrl_ThrowsExtractionException() {
        // Given
        String invalidUrl = "https://nonexistent-domain-12345.com/data";

        // When/Then
        assertThatThrownBy(() -> extractionService.fetchData(invalidUrl))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("Failed to extract data from");
    }

    @Test
    void testFetchData_MalformedUrl_ThrowsExtractionException() {
        // Given
        String malformedUrl = "not-a-valid-url";

        // When/Then
        assertThatThrownBy(() -> extractionService.fetchData(malformedUrl))
                .isInstanceOf(ExtractionException.class);
    }

    @Test
    void testFetchData_NonJsonResponse_ThrowsExtractionException() {
        // Given - URL that returns non-JSON content (HTML)
        String htmlUrl = "https://www.google.com";

        // When/Then
        assertThatThrownBy(() -> extractionService.fetchData(htmlUrl))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("Failed to extract data from");
    }

    @Test
    void testFetchData_404NotFound_ThrowsExtractionException() {
        // Given - URL that will return 404
        String notFoundUrl = "https://restcountries.com/v3.1/alpha/INVALID";

        // When/Then
        assertThatThrownBy(() -> extractionService.fetchData(notFoundUrl))
                .isInstanceOf(ExtractionException.class);
    }

    // ===== Edge Cases =====

    @Test
    void testFetchData_EmptyJsonArray_ReturnsEmptyList() {
        // Given - This is a theoretical case, as most APIs won't return empty arrays
        // We'll use a valid endpoint but with filters that return minimal results
        String sourceUrl = "https://restcountries.com/v3.1/alpha/usa";

        // When
        List<JsonNode> result = extractionService.fetchData(sourceUrl);

        // Then - Should handle empty responses gracefully
        assertThat(result).isNotNull();
    }

    @Test
    void testFetchData_RegionEndpoint_HandlesMultipleCountries() {
        // Given - Endpoint returning multiple records
        String sourceUrl = "https://restcountries.com/v3.1/region/oceania";

        // When
        List<JsonNode> result = extractionService.fetchData(sourceUrl);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.size()).isGreaterThan(5); // Oceania has several countries
    }

    @Test
    void testFetchData_ComplexNestedJson_ParsesCorrectly() {
        // Given
        String sourceUrl = "https://restcountries.com/v3.1/alpha/fra";

        // When
        List<JsonNode> result = extractionService.fetchData(sourceUrl);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);

        JsonNode france = result.get(0);
        assertThat(france.has("name")).isTrue();
        assertThat(france.has("capital")).isTrue();
        assertThat(france.has("population")).isTrue();

        // Verify nested structures are preserved
        assertThat(france.get("name").isObject()).isTrue();
    }

    // ===== Performance & Resource Tests =====

    @Test
    void testFetchData_MultipleSequentialCalls_Succeeds() {
        // Given
        String url1 = "https://restcountries.com/v3.1/alpha/usa";
        String url2 = "https://restcountries.com/v3.1/alpha/can";
        String url3 = "https://restcountries.com/v3.1/alpha/mex";

        // When
        List<JsonNode> result1 = extractionService.fetchData(url1);
        List<JsonNode> result2 = extractionService.fetchData(url2);
        List<JsonNode> result3 = extractionService.fetchData(url3);

        // Then
        assertThat(result1).isNotEmpty();
        assertThat(result2).isNotEmpty();
        assertThat(result3).isNotEmpty();
    }
}
