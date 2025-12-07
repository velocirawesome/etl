package dev.velocirawesome.etl.service.json;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for projecting (filtering) fields from JSON objects based on field name patterns.
 * This class separates the JSON field filtering logic from the transformation orchestration,
 * making it reusable and testable.
 *
 * <p>The projection process:
 * <ol>
 *   <li>Convert JsonNode to JSON string</li>
 *   <li>Parse with JsonPath to extract all top-level fields</li>
 *   <li>Filter fields using the configured FieldFilter</li>
 *   <li>Convert filtered map back to JsonNode</li>
 * </ol>
 *
 * <p>Example usage:
 * <pre>
 * FieldFilter filter = name -> name.toLowerCase().startsWith("n");
 * JsonFieldProjector projector = new JsonFieldProjector(filter, objectMapper);
 * JsonNode filtered = projector.project(originalNode);
 * </pre>
 */
public class JsonFieldProjector {

    private static final Logger logger = LoggerFactory.getLogger(JsonFieldProjector.class);

    private final ObjectMapper objectMapper;
    private final Configuration jsonPathConfig;
    private final FieldFilter fieldFilter;

    /**
     * Creates a new JsonFieldProjector with the specified field filter and ObjectMapper.
     *
     * @param fieldFilter the filter to apply to field names
     * @param objectMapper the Jackson ObjectMapper to use for JSON processing
     */
    public JsonFieldProjector(FieldFilter fieldFilter, ObjectMapper objectMapper) {
        this.fieldFilter = fieldFilter;
        this.objectMapper = objectMapper;
        this.jsonPathConfig = Configuration.defaultConfiguration()
                .addOptions(Option.SUPPRESS_EXCEPTIONS);
    }

    /**
     * Projects (filters) a JSON object to keep only top-level fields that match the configured filter.
     * Non-object nodes are returned unchanged.
     *
     * @param node the source JSON node to filter
     * @return a new JsonNode containing only fields that match the filter, or the original node if not an object
     */
    public JsonNode project(JsonNode node) {
        if (!node.isObject()) {
            return node;
        }

        try {
            // Convert JsonNode to JSON string for JsonPath processing
            String jsonString = objectMapper.writeValueAsString(node);

            // Parse JSON with JsonPath
            Object document = jsonPathConfig.jsonProvider().parse(jsonString);

            // Get all top-level keys using JsonPath ($ = root object)
            Map<String, Object> jsonMap = JsonPath.using(jsonPathConfig).parse(document).read("$");

            // Filter keys using the configured FieldFilter
            // Use LinkedHashMap to preserve insertion order and null values
            Map<String, Object> filteredMap = jsonMap.entrySet().stream()
                    .filter(entry -> fieldFilter.matches(entry.getKey()))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (v1, v2) -> v1,  // merge function (not used here)
                            LinkedHashMap::new  // preserve order
                    ));

            // Convert back to JsonNode
            String filteredJson = objectMapper.writeValueAsString(filteredMap);
            return objectMapper.readTree(filteredJson);

        } catch (Exception e) {
            logger.warn("Error projecting JSON fields with filter, returning empty object", e);
            return objectMapper.createObjectNode();
        }
    }

    /**
     * Returns the FieldFilter used by this projector.
     *
     * @return the configured field filter
     */
    public FieldFilter getFieldFilter() {
        return fieldFilter;
    }
}
