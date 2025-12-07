package dev.velocirawesome.etl.service;

import dev.velocirawesome.etl.exception.TransformationException;
import dev.velocirawesome.etl.model.entity.Country;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TransformationService {

    private static final Logger logger = LoggerFactory.getLogger(TransformationService.class);

    public List<Country> transform(List<JsonNode> records) {
        try {
            logger.info("Starting transformation of {} records", records.size());

            List<Country> countries = new ArrayList<>();
            int skipped = 0;

            for (JsonNode record : records) {
                try {
                    // Validate required field: cca3 (country code)
                    if (record.has("cca3")) {
                        String code = record.get("cca3").asString();

                        // Transform: filter fields to keep only those starting with 'n' (case-insensitive)
                        JsonNode transformedRecord = filter(record);

                        Country country = new Country(code, transformedRecord);
                        countries.add(country);
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to transform record: {}", record, e);
                    skipped++;
                }
            }

            logger.info("Transformation successful. Transformed {} records, skipped {}",
                    countries.size(), skipped);
            return countries;

        } catch (Exception e) {
            logger.error("Transformation failed", e);
            throw new TransformationException("Failed to transform records", e);
        }
    }

    /**
     * ExampleJSON object filter to keep only top-level fields whose names start with 'n' (case-insensitive).
     *
     * @param node the source JSON node
     * @return a new JSON object with only fields starting with 'n'
     */
    private JsonNode filter(JsonNode node) {
        if (!node.isObject()) {
            return node;
        }

        ObjectNode filtered = ((ObjectNode) node).objectNode();

        node.properties().forEach(entry -> {
            String fieldName = entry.getKey();
            if (fieldName.toLowerCase().startsWith("n")) {
                filtered.set(fieldName, entry.getValue());
            }
        });

        return filtered;
    }
}
