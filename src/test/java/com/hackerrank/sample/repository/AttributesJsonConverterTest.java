package com.hackerrank.sample.repository;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AttributesJsonConverterTest {

    private final AttributesJsonConverter converter = new AttributesJsonConverter();

    @Test
    void convertToDatabaseColumn_returnsEmptyJsonForNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("{}");
    }

    @Test
    void convertToDatabaseColumn_returnsEmptyJsonForEmptyMap() {
        assertThat(converter.convertToDatabaseColumn(Map.of())).isEqualTo("{}");
    }

    @Test
    void roundTripsScalarMap() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("battery", "4000 mAh");
        attributes.put("memory", "8 GB");
        attributes.put("freeShipping", Boolean.TRUE);
        attributes.put("rating", 4.6);

        String serialized = converter.convertToDatabaseColumn(attributes);
        Map<String, Object> deserialized = converter.convertToEntityAttribute(serialized);

        assertThat(deserialized)
                .containsEntry("battery", "4000 mAh")
                .containsEntry("memory", "8 GB")
                .containsEntry("freeShipping", true)
                .containsEntry("rating", 4.6);
    }

    @Test
    void convertToEntityAttribute_returnsEmptyMapForNullOrBlank() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
        assertThat(converter.convertToEntityAttribute("   ")).isEmpty();
    }
}
