package com.hackerrank.sample.repository;

import com.hackerrank.sample.model.Category;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogProductEntityTest {

    @Test
    void buildsHappyPath() {
        CatalogProductEntity product = new CatalogProductEntity(
                1L,
                "Galaxy S24",
                "flagship",
                "https://example.com/s24.jpg",
                4.6,
                Category.SMARTPHONE,
                Map.of("battery", "4000 mAh", "memory", "8 GB"));

        assertThat(product.getId()).isEqualTo(1L);
        assertThat(product.getCategory()).isEqualTo(Category.SMARTPHONE);
        assertThat(product.getAttributes()).containsEntry("battery", "4000 mAh");
    }

    @Test
    void rejectsNullCategory_INV2() {
        assertThatThrownBy(() -> new CatalogProductEntity(
                1L, "x", null, null, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category");
    }

    @Test
    void rejectsRatingOutOfRange_INV3() {
        assertThatThrownBy(() -> new CatalogProductEntity(
                1L, "x", null, null, 5.5, Category.SMARTPHONE, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rating");
        assertThatThrownBy(() -> new CatalogProductEntity(
                1L, "x", null, null, -0.1, Category.SMARTPHONE, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBadAttributeKey_INV4() {
        assertThatThrownBy(() -> new CatalogProductEntity(
                1L, "x", null, null, null, Category.SMARTPHONE, Map.of("1bad", "v")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attribute key");
    }

    @Test
    void rejectsNonScalarAttributeValue_INV4() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("nested", Map.of("x", 1));
        assertThatThrownBy(() -> new CatalogProductEntity(
                1L, "x", null, null, null, Category.SMARTPHONE, attrs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attribute value");
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new CatalogProductEntity(
                1L, " ", null, null, null, Category.SMARTPHONE, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void rejectsTooManyAttributes() {
        Map<String, Object> tooMany = new HashMap<>();
        for (int i = 0; i < 21; i++) {
            tooMany.put("k" + i, "v");
        }
        assertThatThrownBy(() -> new CatalogProductEntity(
                1L, "x", null, null, null, Category.SMARTPHONE, tooMany))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
