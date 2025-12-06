package dev.velocirawesome.etl.unit;

import dev.velocirawesome.etl.exception.TransformationException;
import dev.velocirawesome.etl.model.entity.Country;
import dev.velocirawesome.etl.service.TransformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TransformationServiceTest {

    private TransformationService transformationService;
    private JsonMapper jsonMapper;

    @BeforeEach
    public void setUp() {
        transformationService = new TransformationService();
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
        String invalidJson1 = "{\"cca3\": \"GBR\"}"; // Missing name
        String invalidJson2 = "{\"name\": {\"common\": \"Unknown\"}}"; // Missing cca3
        String invalidJson3 = "{\"cca3\": \"FRA\", \"name\": {\"common\": \"France\"}}";

        records.add(jsonMapper.readTree(validJson));
        records.add(jsonMapper.readTree(invalidJson1));
        records.add(jsonMapper.readTree(invalidJson2));
        records.add(jsonMapper.readTree(invalidJson3));

        // Act
        List<Country> result = transformationService.transform(records);

        // Assert
        assertThat(result).hasSize(2); // Only 2 valid records
        assertThat(result.get(0).getCode()).isEqualTo("USA");
        assertThat(result.get(1).getCode()).isEqualTo("FRA");
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
}
