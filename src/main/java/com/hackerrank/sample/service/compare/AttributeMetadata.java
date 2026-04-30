package com.hackerrank.sample.service.compare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Per-attribute domain metadata loaded once from
 * {@code attribute-metadata.json}. Holds the comparison direction
 * (higher-better / lower-better / none) used by
 * {@link DifferencesCalculator} to decide whether a numeric attribute
 * has a winner.
 */
public final class AttributeMetadata {

    public enum Direction { HIGHER_BETTER, LOWER_BETTER, NONE }

    private static final String DEFAULT_RESOURCE = "/attribute-metadata.json";
    private static final AttributeMetadata DEFAULT = loadFromClasspath(DEFAULT_RESOURCE);

    private final Map<String, Direction> directions;

    AttributeMetadata(Map<String, Direction> directions) {
        this.directions = Collections.unmodifiableMap(new LinkedHashMap<>(directions));
    }

    public static AttributeMetadata defaultRegistry() {
        return DEFAULT;
    }

    public Direction directionFor(String attributeKey) {
        if (attributeKey == null) {
            return Direction.NONE;
        }
        return directions.getOrDefault(attributeKey, Direction.NONE);
    }

    private static AttributeMetadata loadFromClasspath(String resource) {
        try (InputStream in = AttributeMetadata.class.getResourceAsStream(resource)) {
            if (in == null) {
                return new AttributeMetadata(Map.of());
            }
            JsonNode root = new ObjectMapper().readTree(in);
            JsonNode attrs = root.path("attributes");
            Map<String, Direction> map = new LinkedHashMap<>();
            attrs.fields().forEachRemaining(entry -> {
                String dirRaw = entry.getValue().path("direction").asText("");
                map.put(entry.getKey(), parseDirection(dirRaw));
            });
            return new AttributeMetadata(map);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load " + resource, ex);
        }
    }

    private static Direction parseDirection(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "higher_better" -> Direction.HIGHER_BETTER;
            case "lower_better" -> Direction.LOWER_BETTER;
            default -> Direction.NONE;
        };
    }
}
