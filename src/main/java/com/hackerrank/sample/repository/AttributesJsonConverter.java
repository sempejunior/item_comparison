package com.hackerrank.sample.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JPA converter that persists the open `attributes` map as a JSON string.
 *
 * Rationale lives in SPEC-002 §2.2: a flexible per-category map keeps the
 * schema closed and avoids a per-category subclass explosion. Values are
 * primitives (String, Number, Boolean) — nested objects are rejected on
 * load by the seed validator, not here.
 */
@Converter
public class AttributesJsonConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() {};
    private static final String EMPTY = "{}";

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return EMPTY;
        }
        try {
            return MAPPER.writeValueAsString(attributes);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize attributes", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize attributes: " + dbData, e);
        }
    }
}
