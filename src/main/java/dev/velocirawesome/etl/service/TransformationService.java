package dev.velocirawesome.etl.service;

import dev.velocirawesome.etl.exception.TransformationException;
import dev.velocirawesome.etl.model.entity.Country;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

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
                    // Validate required fields: code and name
                    if (record.has("cca3") && record.has("name")) {
                        String code = record.get("cca3").asText();
                        Country country = new Country(code, record);
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
