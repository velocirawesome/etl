package dev.velocirawesome.etl.service.json;

/**
 * Functional interface for filtering JSON object field names.
 * Used to determine which fields to keep during JSON transformation.
 *
 * <p>This interface enables lambda expressions for flexible field filtering:
 * <pre>
 * // Prefix filter
 * FieldFilter filter = name -> name.toLowerCase().startsWith("n");
 *
 * // Suffix filter
 * FieldFilter filter = name -> name.toLowerCase().endsWith("name");
 *
 * // Complex logic
 * FieldFilter filter = name -> {
 *     if (name == null) return false;
 *     return name.length() > 5 && name.contains("country");
 * };
 * </pre>
 */
@FunctionalInterface
public interface FieldFilter {

    /**
     * Tests whether a field name matches this filter's criteria.
     *
     * @param fieldName the name of the JSON field to test
     * @return true if the field should be kept, false if it should be filtered out
     */
    boolean matches(String fieldName);
}
