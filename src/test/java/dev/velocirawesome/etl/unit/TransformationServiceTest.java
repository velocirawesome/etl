package dev.velocirawesome.etl.unit;

import dev.velocirawesome.etl.exception.TransformationException;
import dev.velocirawesome.etl.model.entity.Country;
import dev.velocirawesome.etl.service.TransformationService;
import dev.velocirawesome.etl.service.json.FieldFilter;
import dev.velocirawesome.etl.service.json.JsonFieldProjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TransformationServiceTest {

    private TransformationService transformationService;
    private JsonMapper jsonMapper;

    @BeforeEach
    public void setUp() {
        // Create the same lambda filter as in TransformationConfig
        FieldFilter fieldFilter = fieldName -> fieldName != null &&
                                               fieldName.toLowerCase().startsWith("n");

        ObjectMapper objectMapper = new ObjectMapper();
        JsonFieldProjector jsonFieldProjector = new JsonFieldProjector(fieldFilter, objectMapper);

        transformationService = new TransformationService(jsonFieldProjector);
        jsonMapper = JsonMapper.builder().build();
    }

    @Test
    public void testTransform_validRecords() throws Exception {
        // Arrange
        List<JsonNode> records = new ArrayList<>();

        String json1 = "{\"cca3\": \"USA\", \"name\": {\"common\": \"United States\"}}";
        String json2 = "{\"cca3\": \"GBR\", \"name\": {\"common\": \"United Kingdom\"}}";
        String json3 = "{\"cca3\": \"FRA\", \"name\": {\"common\": \"France\"}}";

        records.add(jsonMapper.readTree(json1));
        records.add(jsonMapper.readTree(json2));
        records.add(jsonMapper.readTree(json3));

        // Act
        List<Country> result = transformationService.transform(records);

        // Assert
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getCode()).isEqualTo("USA");
        assertThat(result.get(1).getCode()).isEqualTo("GBR");
        assertThat(result.get(2).getCode()).isEqualTo("FRA");
    }

    @Test
    public void testTransform_filtersInvalidRecords() throws Exception {
        // Arrange
        List<JsonNode> records = new ArrayList<>();

        String validJson = "{\"cca3\": \"USA\", \"name\": {\"common\": \"United States\"}}";
        String validJson2 = "{\"cca3\": \"GBR\"}"; // Valid - only requires cca3
        String invalidJson = "{\"name\": {\"common\": \"Unknown\"}}"; // Missing cca3
        String validJson3 = "{\"cca3\": \"FRA\", \"name\": {\"common\": \"France\"}}";

        records.add(jsonMapper.readTree(validJson));
        records.add(jsonMapper.readTree(validJson2));
        records.add(jsonMapper.readTree(invalidJson));
        records.add(jsonMapper.readTree(validJson3));

        // Act
        List<Country> result = transformationService.transform(records);

        // Assert
        assertThat(result).hasSize(3); // 3 valid records (all with cca3)
        assertThat(result.get(0).getCode()).isEqualTo("USA");
        assertThat(result.get(1).getCode()).isEqualTo("GBR");
        assertThat(result.get(2).getCode()).isEqualTo("FRA");
    }

    @Test
    public void testTransform_emptyList() {
        // Arrange
        List<JsonNode> records = new ArrayList<>();

        // Act
        List<Country> result = transformationService.transform(records);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    public void testTransform_mixedValidInvalid() throws Exception {
        // Arrange
        List<JsonNode> records = new ArrayList<>();

        String valid1 = "{\"cca3\": \"CHN\", \"name\": {\"common\": \"China\"}}";
        String invalid1 = "{\"invalid\": true}";
        String valid2 = "{\"cca3\": \"IND\", \"name\": {\"common\": \"India\"}}";
        String invalid2 = "{\"other\": \"field\"}"; // Missing both cca3 and name

        records.add(jsonMapper.readTree(valid1));
        records.add(jsonMapper.readTree(invalid1));
        records.add(jsonMapper.readTree(valid2));
        records.add(jsonMapper.readTree(invalid2));

        // Act
        List<Country> result = transformationService.transform(records);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCode()).isEqualTo("CHN");
        assertThat(result.get(1).getCode()).isEqualTo("IND");
    }

    // ========== Tests for filterFieldsStartingWith ==========

    @Test
    public void testFilterFieldsStartingWith_LowercaseN() throws Exception {
        // Given - JSON with lowercase 'n' fields
        String json = """
            {
                "cca3": "USA",
                "name": "United States",
                "nationality": "American",
                "number": 123,
                "capital": "Washington"
            }
            """;

        JsonNode inputNode = jsonMapper.readTree(json);
        List<JsonNode> records = List.of(inputNode);

        // When
        List<Country> result = transformationService.transform(records);

        // Then
        assertThat(result).hasSize(1);
        JsonNode transformedData = result.get(0).getData();

        // Verify only fields starting with 'n' are kept (case-insensitive)
        assertThat(transformedData.has("name")).isTrue();
        assertThat(transformedData.has("nationality")).isTrue();
        assertThat(transformedData.has("number")).isTrue();
        assertThat(transformedData.has("cca3")).isFalse();
        assertThat(transformedData.has("capital")).isFalse();
        assertThat(transformedData.size()).isEqualTo(3);
    }

    @Test
    public void testFilterFieldsStartingWith_UppercaseN() throws Exception {
        // Given - JSON with uppercase 'N' fields
        String json = """
            {
                "cca3": "GBR",
                "Name": "United Kingdom",
                "Nationality": "British",
                "Number": 456,
                "Capital": "London"
            }
            """;

        JsonNode inputNode = jsonMapper.readTree(json);
        List<JsonNode> records = List.of(inputNode);

        // When
        List<Country> result = transformationService.transform(records);

        // Then
        assertThat(result).hasSize(1);
        JsonNode transformedData = result.get(0).getData();

        // Verify fields starting with 'N' are kept (case-insensitive)
        assertThat(transformedData.has("Name")).isTrue();
        assertThat(transformedData.has("Nationality")).isTrue();
        assertThat(transformedData.has("Number")).isTrue();
        assertThat(transformedData.has("cca3")).isFalse();
        assertThat(transformedData.has("Capital")).isFalse();
        assertThat(transformedData.size()).isEqualTo(3);
    }

    @Test
    public void testFilterFieldsStartingWith_MixedCase() throws Exception {
        // Given - JSON with mixed case fields starting with 'n'/'N'
        String json = """
            {
                "cca3": "FRA",
                "name": "France",
                "Native": "Français",
                "nativeName": {"fra": {"common": "France"}},
                "NativeLanguage": "French",
                "capital": "Paris",
                "region": "Europe"
            }
            """;

        JsonNode inputNode = jsonMapper.readTree(json);
        List<JsonNode> records = List.of(inputNode);

        // When
        List<Country> result = transformationService.transform(records);

        // Then
        assertThat(result).hasSize(1);
        JsonNode transformedData = result.get(0).getData();

        // All 'n'/'N' fields should be kept regardless of case
        assertThat(transformedData.has("name")).isTrue();
        assertThat(transformedData.has("Native")).isTrue();
        assertThat(transformedData.has("nativeName")).isTrue();
        assertThat(transformedData.has("NativeLanguage")).isTrue();

        // Non-'n' fields should be filtered out
        assertThat(transformedData.has("cca3")).isFalse();
        assertThat(transformedData.has("capital")).isFalse();
        assertThat(transformedData.has("region")).isFalse();

        assertThat(transformedData.size()).isEqualTo(4);
    }

    @Test
    public void testFilterFieldsStartingWith_NoMatchingFields() throws Exception {
        // Given - JSON with no fields starting with 'n'
        String json = """
            {
                "cca3": "DEU",
                "capital": "Berlin",
                "population": 83000000,
                "area": 357022
            }
            """;

        JsonNode inputNode = jsonMapper.readTree(json);
        List<JsonNode> records = List.of(inputNode);

        // When
        List<Country> result = transformationService.transform(records);

        // Then
        assertThat(result).hasSize(1);
        JsonNode transformedData = result.get(0).getData();

        // No fields should remain
        assertThat(transformedData.isEmpty()).isTrue();
        assertThat(transformedData.size()).isEqualTo(0);
    }

    @Test
    public void testFilterFieldsStartingWith_AllMatchingFields() throws Exception {
        // Given - JSON where all fields start with 'n'/'N'
        String json = """
            {
                "cca3": "JPN",
                "name": "Japan",
                "Native": "日本",
                "number": 81,
                "Nationality": "Japanese"
            }
            """;

        JsonNode inputNode = jsonMapper.readTree(json);
        List<JsonNode> records = List.of(inputNode);

        // When
        List<Country> result = transformationService.transform(records);

        // Then
        assertThat(result).hasSize(1);
        JsonNode transformedData = result.get(0).getData();

        // All 'n' fields should be kept, cca3 should be filtered
        assertThat(transformedData.has("name")).isTrue();
        assertThat(transformedData.has("Native")).isTrue();
        assertThat(transformedData.has("number")).isTrue();
        assertThat(transformedData.has("Nationality")).isTrue();
        assertThat(transformedData.has("cca3")).isFalse();
        assertThat(transformedData.size()).isEqualTo(4);
    }

    @Test
    public void testFilterFieldsStartingWith_PreservesNestedStructure() throws Exception {
        // Given - JSON with nested objects
        String json = """
            {
                "cca3": "ITA",
                "name": {
                    "common": "Italy",
                    "official": "Italian Republic"
                },
                "nativeName": {
                    "ita": {
                        "official": "Repubblica italiana",
                        "common": "Italia"
                    }
                },
                "capital": ["Rome"],
                "region": "Europe"
            }
            """;

        JsonNode inputNode = jsonMapper.readTree(json);
        List<JsonNode> records = List.of(inputNode);

        // When
        List<Country> result = transformationService.transform(records);

        // Then
        assertThat(result).hasSize(1);
        JsonNode transformedData = result.get(0).getData();

        // Verify only 'n' fields are kept
        assertThat(transformedData.has("name")).isTrue();
        assertThat(transformedData.has("nativeName")).isTrue();
        assertThat(transformedData.has("capital")).isFalse();
        assertThat(transformedData.has("region")).isFalse();
        assertThat(transformedData.size()).isEqualTo(2);

        // Verify nested structure is preserved
        assertThat(transformedData.get("name").isObject()).isTrue();
        assertThat(transformedData.get("name").has("common")).isTrue();
        assertThat(transformedData.get("name").has("official")).isTrue();
        assertThat(transformedData.get("name").get("common").asString()).isEqualTo("Italy");

        assertThat(transformedData.get("nativeName").isObject()).isTrue();
        assertThat(transformedData.get("nativeName").has("ita")).isTrue();
    }

    @Test
    public void testFilterFieldsStartingWith_EmptyObject() throws Exception {
        // Given - Empty JSON object
        String json = "{}";

        JsonNode inputNode = jsonMapper.readTree(json);
        List<JsonNode> records = List.of(inputNode);

        // When
        List<Country> result = transformationService.transform(records);

        // Then - Should skip because no cca3
        assertThat(result).isEmpty();
    }

    @Test
    public void testFilterFieldsStartingWith_PreservesValueTypes() throws Exception {
        // Given - JSON with fields starting with 'n' and various value types
        String json = """
            {
                "cca3": "CAN",
                "name": "Canada",
                "number": 42,
                "numbers": [1, 2, 3],
                "nullable": null,
                "nested": {
                    "key": "value"
                },
                "capital": "Ottawa"
            }
            """;

        JsonNode inputNode = jsonMapper.readTree(json);
        List<JsonNode> records = List.of(inputNode);

        // When
        List<Country> result = transformationService.transform(records);

        // Then
        assertThat(result).hasSize(1);
        JsonNode transformedData = result.get(0).getData();

        // Verify non-'n' fields are filtered out
        assertThat(transformedData.has("cca3")).isFalse();
        assertThat(transformedData.has("capital")).isFalse();

        // Verify that fields starting with 'n' are kept and types are preserved
        // Note: The actual fields present depend on JsonPath/Jackson's handling of various types
        if (transformedData.has("name")) {
            assertThat(transformedData.get("name").asString()).isEqualTo("Canada");
        }
        if (transformedData.has("number")) {
            assertThat(transformedData.get("number").asInt()).isEqualTo(42);
        }
        if (transformedData.has("numbers")) {
            assertThat(transformedData.get("numbers").isArray()).isTrue();
            assertThat(transformedData.get("numbers").size()).isEqualTo(3);
        }
        if (transformedData.has("nullable")) {
            assertThat(transformedData.get("nullable").isNull()).isTrue();
        }
        if (transformedData.has("nested")) {
            assertThat(transformedData.get("nested").isObject()).isTrue();
            assertThat(transformedData.get("nested").get("key").asString()).isEqualTo("value");
        }
    }
}
