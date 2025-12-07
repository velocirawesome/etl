package dev.velocirawesome.etl.service;

import dev.velocirawesome.etl.exception.TransformationException;
import dev.velocirawesome.etl.model.entity.Country;
import dev.velocirawesome.etl.service.json.JsonFieldProjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Service
public class TransformationService {

    private static final Logger logger = LoggerFactory.getLogger(TransformationService.class);
    private final JsonFieldProjector jsonFieldProjector;

    /**
     * Constructor injection of JsonFieldProjector (configured in TransformationConfig).
     *
     * @param jsonFieldProjector the JSON field projection utility
     */
    public TransformationService(JsonFieldProjector jsonFieldProjector) {
        this.jsonFieldProjector = jsonFieldProjector;
    }

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

                        // Transform: project fields using configured filter
                        JsonNode transformedRecord = jsonFieldProjector.project(record);

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
}
