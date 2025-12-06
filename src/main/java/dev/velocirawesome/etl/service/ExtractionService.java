 package dev.velocirawesome.etl.service;

import dev.velocirawesome.etl.exception.ExtractionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(ExtractionService.class);
    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public ExtractionService() {
        this.restClient = RestClient.create();
        this.jsonMapper = JsonMapper.builder().build();
    }

    public List<JsonNode> fetchData(String sourceUrl) {
        try {
            logger.info("Starting extraction from URL: {}", sourceUrl);

            String response = restClient.get()
                    .uri(sourceUrl)
                    .retrieve()
                    .body(String.class);

            if (response == null) {
                throw new ExtractionException("Empty response from " + sourceUrl);
            }

            JsonNode root = jsonMapper.readTree(response);
            List<JsonNode> records = new ArrayList<>();

            if (root.isArray()) {
                root.forEach(records::add);
            } else {
                records.add(root);
            }

            logger.info("Extraction successful. Extracted {} records from {}", records.size(), sourceUrl);
            return records;

        } catch (Exception e) {
            logger.error("Extraction failed for URL: {}", sourceUrl, e);
            throw new ExtractionException("Failed to extract data from " + sourceUrl, e);
        }
    }
}

