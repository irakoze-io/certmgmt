package tech.seccertificate.certmgmt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for validating recipient data against template version field schema.
 * 
 * <p>The field schema defines the expected structure and constraints for recipient data
 * when generating certificates. This validator ensures that:
 * <ul>
 *   <li>All required fields are present</li>
 *   <li>Field types match the schema definition</li>
 *   <li>Field values meet any constraints (min/max length, patterns, etc.)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldSchemaValidator {

    private final ObjectMapper objectMapper;

    /**
     * Validates recipient data against the field schema from template version.
     * 
     * @param recipientData The recipient data to validate (as JSON string)
     * @param fieldSchema The field schema from template version (as JSON string)
     * @throws IllegalArgumentException if validation fails
     */
    public void validateRecipientData(String recipientData, String fieldSchema) {
        if (fieldSchema == null || fieldSchema.trim().isEmpty()) {
            log.warn("Field schema is null or empty, skipping validation");
            return;
        }

        if (recipientData == null || recipientData.trim().isEmpty()) {
            throw new IllegalArgumentException("Recipient data cannot be null or empty");
        }

        try {
            // Parse field schema
            Map<String, Object> schemaMap = parseJsonToMap(fieldSchema);
            if (schemaMap == null || schemaMap.isEmpty()) {
                log.warn("Field schema is empty, skipping validation");
                return;
            }

            // Parse recipient data
            Map<String, Object> recipientMap = parseJsonToMap(recipientData);
            if (recipientMap == null || recipientMap.isEmpty()) {
                throw new IllegalArgumentException("Recipient data cannot be empty");
            }

            // Validate each field in schema
            List<String> errors = new ArrayList<>();

            for (Map.Entry<String, Object> schemaEntry : schemaMap.entrySet()) {
                String fieldName = schemaEntry.getKey();
                Object fieldDefinition = schemaEntry.getValue();

                if (fieldDefinition instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fieldDef = (Map<String, Object>) fieldDefinition;
                    
                    // Check if field is required
                    boolean required = getBooleanValue(fieldDef, "required", false);
                    boolean present = recipientMap.containsKey(fieldName);

                    if (required && !present) {
                        errors.add(String.format("Required field '%s' is missing", fieldName));
                        continue;
                    }

                    // If field is present, validate its type and constraints
                    if (present) {
                        Object fieldValue = recipientMap.get(fieldName);
                        validateField(fieldName, fieldValue, fieldDef, errors);
                    }
                }
            }

            // Check for extra fields not in schema (warn but don't fail)
            Set<String> schemaFields = schemaMap.keySet();
            Set<String> recipientFields = recipientMap.keySet();
            Set<String> extraFields = new HashSet<>(recipientFields);
            extraFields.removeAll(schemaFields);
            
            if (!extraFields.isEmpty()) {
                log.debug("Recipient data contains fields not in schema: {}", extraFields);
                // Don't add to errors - extra fields are allowed but may be ignored
            }

            if (!errors.isEmpty()) {
                String errorMessage = String.join("; ", errors);
                throw new IllegalArgumentException("Recipient data validation failed: " + errorMessage);
            }

            log.debug("Recipient data validation passed for {} fields", schemaMap.size());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to validate recipient data against field schema", e);
            throw new IllegalArgumentException(
                    "Failed to validate recipient data: " + e.getMessage(), e);
        }
    }

    /**
     * Validates a single field value against its schema definition.
     */
    @SuppressWarnings("unchecked")
    private void validateField(String fieldName, Object fieldValue, 
                              Map<String, Object> fieldDef, List<String> errors) {
        if (fieldValue == null) {
            // Null values are only allowed if field is not required
            // (already checked above)
            return;
        }

        // Validate type
        String expectedType = getStringValue(fieldDef, "type", "string");
        String actualType = getJavaType(fieldValue);

        if (!isTypeCompatible(expectedType, actualType)) {
            errors.add(String.format("Field '%s' has invalid type. Expected: %s, Got: %s", 
                    fieldName, expectedType, actualType));
            return;
        }

        // Validate string constraints
        if ("string".equals(expectedType) && fieldValue instanceof String) {
            String strValue = (String) fieldValue;
            
            // Min length
            if (fieldDef.containsKey("minLength")) {
                int minLength = getIntValue(fieldDef, "minLength", 0);
                if (strValue.length() < minLength) {
                    errors.add(String.format("Field '%s' is too short. Minimum length: %d", 
                            fieldName, minLength));
                }
            }

            // Max length
            if (fieldDef.containsKey("maxLength")) {
                int maxLength = getIntValue(fieldDef, "maxLength", Integer.MAX_VALUE);
                if (strValue.length() > maxLength) {
                    errors.add(String.format("Field '%s' is too long. Maximum length: %d", 
                            fieldName, maxLength));
                }
            }

            // Pattern (regex)
            if (fieldDef.containsKey("pattern")) {
                String pattern = getStringValue(fieldDef, "pattern", null);
                if (pattern != null && !strValue.matches(pattern)) {
                    errors.add(String.format("Field '%s' does not match required pattern: %s", 
                            fieldName, pattern));
                }
            }
        }

        // Validate number constraints
        if (("number".equals(expectedType) || "integer".equals(expectedType)) 
                && (fieldValue instanceof Number)) {
            double numValue = ((Number) fieldValue).doubleValue();

            // Minimum
            if (fieldDef.containsKey("minimum")) {
                double minimum = getDoubleValue(fieldDef, "minimum", Double.NEGATIVE_INFINITY);
                if (numValue < minimum) {
                    errors.add(String.format("Field '%s' is below minimum value: %s", 
                            fieldName, minimum));
                }
            }

            // Maximum
            if (fieldDef.containsKey("maximum")) {
                double maximum = getDoubleValue(fieldDef, "maximum", Double.POSITIVE_INFINITY);
                if (numValue > maximum) {
                    errors.add(String.format("Field '%s' exceeds maximum value: %s", 
                            fieldName, maximum));
                }
            }
        }
    }

    /**
     * Checks if the actual Java type is compatible with the expected schema type.
     */
    private boolean isTypeCompatible(String expectedType, String actualType) {
        if (expectedType == null || actualType == null) {
            return true; // Skip type checking if not specified
        }

        return switch (expectedType.toLowerCase()) {
            case "string" -> "string".equals(actualType);
            case "number", "integer" -> "number".equals(actualType) || "integer".equals(actualType);
            case "boolean" -> "boolean".equals(actualType);
            case "array" -> "array".equals(actualType) || "list".equals(actualType);
            case "object" -> "map".equals(actualType) || "object".equals(actualType);
            default -> true; // Unknown types are allowed
        };
    }

    /**
     * Gets the Java type name for a value.
     */
    private String getJavaType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Number) {
            if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
                return "integer";
            }
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof List || value.getClass().isArray()) {
            return "array";
        }
        if (value instanceof Map) {
            return "map";
        }
        return value.getClass().getSimpleName().toLowerCase();
    }

    /**
     * Parses JSON string to Map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
            log.warn("Parsed JSON is not a map: {}", parsed.getClass().getName());
            return new HashMap<>();
        } catch (Exception e) {
            log.error("Failed to parse JSON: {}", json, e);
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage(), e);
        }
    }

    // Helper methods for extracting values from maps with defaults

    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    private boolean getBooleanValue(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
