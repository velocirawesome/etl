package dev.velocirawesome.etl.config;

import dev.velocirawesome.etl.service.json.FieldFilter;
import dev.velocirawesome.etl.service.json.JsonFieldProjector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Configuration for JSON transformation behavior.
 * Defines which field filtering strategy to use during ETL transformation.
 *
 * <p>To change the filtering behavior, simply modify the {@code fieldFilter()} bean.
 * Examples:
 * <pre>
 * // Prefix filter (current)
 * return name -> name != null && name.toLowerCase().startsWith("n");
 *
 * // Suffix filter
 * return name -> name != null && name.toLowerCase().endsWith("name");
 *
 * // Contains filter
 * return name -> name != null && name.toLowerCase().contains("country");
 *
 * // Regex filter
 * return name -> name != null && name.matches("^[nN].*");
 *
 * // Complex logic
 * return name -> {
 *     if (name == null) return false;
 *     String lower = name.toLowerCase();
 *     return lower.startsWith("n") || lower.startsWith("c");
 * };
 * </pre>
 */
@Configuration
public class TransformationConfig {

    /**
     * Defines the field filter to use for JSON transformation as a lambda expression.
     * This provides compile-time configuration with maximum flexibility.
     *
     * <p>Current configuration: Keep fields starting with 'n' (case-insensitive)
     *
     * @return the field filter lambda
     */
    @Bean
    public FieldFilter fieldFilter() {
        return fieldName -> fieldName != null &&
                           fieldName.toLowerCase().startsWith("n");
    }

    /**
     * Creates the JsonFieldProjector with the configured filter.
     *
     * @param fieldFilter the configured field filter
     * @param objectMapper the Jackson ObjectMapper
     * @return the JsonFieldProjector instance
     */
    @Bean
    public JsonFieldProjector jsonFieldProjector(FieldFilter fieldFilter, ObjectMapper objectMapper) {
        return new JsonFieldProjector(fieldFilter, objectMapper);
    }

    /**
     * Provides the ObjectMapper for JSON processing.
     * Spring Boot auto-configures this bean, but we explicitly declare it here
     * to ensure it's available for injection into JsonFieldProjector.
     *
     * @return the ObjectMapper instance
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
