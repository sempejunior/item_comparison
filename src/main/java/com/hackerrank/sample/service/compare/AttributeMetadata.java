package com.hackerrank.sample.service.compare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackerrank.sample.model.Category;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Per-attribute domain metadata loaded once from
 * {@code attribute-metadata.json}. Holds the comparison direction
 * (higher-better / lower-better / none) used by
 * {@link DifferencesCalculator} to decide whether a numeric attribute
 * has a winner, plus the per-category ranking paths consumed by
 * the category-insights endpoint.
 */
public final class AttributeMetadata {

    public enum Direction { HIGHER_BETTER, LOWER_BETTER, NONE }

    private static final String DEFAULT_RESOURCE = "/attribute-metadata.json";
    private static final AttributeMetadata DEFAULT = loadFromClasspath(DEFAULT_RESOURCE);

    private final Map<String, Direction> directions;
    private final Map<Category, List<String>> categoryRankings;

    AttributeMetadata(Map<String, Direction> directions) {
        this(directions, Map.of());
    }

    AttributeMetadata(Map<String, Direction> directions, Map<Category, List<String>> categoryRankings) {
        this.directions = Collections.unmodifiableMap(new LinkedHashMap<>(directions));
        EnumMap<Category, List<String>> copy = new EnumMap<>(Category.class);
        categoryRankings.forEach((cat, paths) -> copy.put(cat, List.copyOf(paths)));
        this.categoryRankings = Collections.unmodifiableMap(copy);
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

    public List<String> rankingPathsFor(Category category) {
        if (category == null) {
            return List.of();
        }
        return categoryRankings.getOrDefault(category, List.of());
    }

    private static AttributeMetadata loadFromClasspath(String resource) {
        try (InputStream in = AttributeMetadata.class.getResourceAsStream(resource)) {
            if (in == null) {
                return new AttributeMetadata(Map.of());
            }
            JsonNode root = new ObjectMapper().readTree(in);
            JsonNode attrs = root.path("attributes");
            Map<String, Direction> directions = new LinkedHashMap<>();
            attrs.fields().forEachRemaining(entry -> {
                String dirRaw = entry.getValue().path("direction").asText("");
                directions.put(entry.getKey(), parseDirection(dirRaw));
            });
            Map<Category, List<String>> rankings = parseCategoryRankings(root.path("categoryRankings"), resource);
            return new AttributeMetadata(directions, rankings);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load " + resource, ex);
        }
    }

    private static Map<Category, List<String>> parseCategoryRankings(JsonNode node, String resource) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        EnumMap<Category, List<String>> result = new EnumMap<>(Category.class);
        node.fields().forEachRemaining(entry -> {
            Category category;
            try {
                category = Category.valueOf(entry.getKey());
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException(
                        "Unknown category '" + entry.getKey() + "' in categoryRankings of " + resource, ex);
            }
            List<String> paths = new ArrayList<>();
            entry.getValue().forEach(pathNode -> paths.add(pathNode.asText()));
            result.put(category, paths);
        });
        TreeSet<String> missing = new TreeSet<>();
        for (Category category : Category.values()) {
            if (!result.containsKey(category)) {
                missing.add(category.name());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "categoryRankings in " + resource + " is missing entries for: " + missing);
        }
        return result;
    }

    private static Direction parseDirection(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "higher_better" -> Direction.HIGHER_BETTER;
            case "lower_better" -> Direction.LOWER_BETTER;
            default -> Direction.NONE;
        };
    }
}
