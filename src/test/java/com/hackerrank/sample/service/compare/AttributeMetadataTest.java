package com.hackerrank.sample.service.compare;

import com.hackerrank.sample.model.Category;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributeMetadataTest {

    @Test
    void defaultRegistry_loadsAllNineKnownKeysWithExpectedDirection() {
        AttributeMetadata registry = AttributeMetadata.defaultRegistry();

        assertThat(registry.directionFor("battery")).isEqualTo(AttributeMetadata.Direction.HIGHER_BETTER);
        assertThat(registry.directionFor("memory")).isEqualTo(AttributeMetadata.Direction.HIGHER_BETTER);
        assertThat(registry.directionFor("storage")).isEqualTo(AttributeMetadata.Direction.HIGHER_BETTER);
        assertThat(registry.directionFor("screen_size_inches")).isEqualTo(AttributeMetadata.Direction.HIGHER_BETTER);
        assertThat(registry.directionFor("refresh_rate_hz")).isEqualTo(AttributeMetadata.Direction.HIGHER_BETTER);
        assertThat(registry.directionFor("hdmi_ports")).isEqualTo(AttributeMetadata.Direction.HIGHER_BETTER);
        assertThat(registry.directionFor("capacity_l")).isEqualTo(AttributeMetadata.Direction.HIGHER_BETTER);
        assertThat(registry.directionFor("weight")).isEqualTo(AttributeMetadata.Direction.LOWER_BETTER);
        assertThat(registry.directionFor("weight_kg")).isEqualTo(AttributeMetadata.Direction.LOWER_BETTER);
    }

    @Test
    void directionFor_unknownKey_returnsNone() {
        AttributeMetadata registry = AttributeMetadata.defaultRegistry();
        assertThat(registry.directionFor("totally_unknown_attribute")).isEqualTo(AttributeMetadata.Direction.NONE);
    }

    @Test
    void directionFor_nullKey_returnsNone() {
        AttributeMetadata registry = AttributeMetadata.defaultRegistry();
        assertThat(registry.directionFor(null)).isEqualTo(AttributeMetadata.Direction.NONE);
    }

    @Test
    void defaultRegistry_loadsCategoryRankingsForEveryCategoryEnumValue() {
        AttributeMetadata registry = AttributeMetadata.defaultRegistry();

        for (Category category : Category.values()) {
            assertThat(registry.rankingPathsFor(category))
                    .as("rankings for %s", category)
                    .isNotEmpty()
                    .startsWith("buyBox.price", "rating");
        }
    }

    @Test
    void rankingPathsFor_smartphone_matchesPlannedShape() {
        AttributeMetadata registry = AttributeMetadata.defaultRegistry();

        assertThat(registry.rankingPathsFor(Category.SMARTPHONE))
                .containsExactly(
                        "buyBox.price",
                        "rating",
                        "attributes.battery",
                        "attributes.memory",
                        "attributes.storage");
    }

    @Test
    void rankingPathsFor_nullCategory_returnsEmptyList() {
        AttributeMetadata registry = AttributeMetadata.defaultRegistry();
        assertThat(registry.rankingPathsFor(null)).isEmpty();
    }

    @Test
    void parser_failsFastWhenCategoryRankingsMissesAnEnumValue() {
        assertThatThrownBy(() -> invokeLoadFromClasspath("/attribute-metadata-missing-category.json"))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .extracting(Throwable::getCause)
                .satisfies(cause -> assertThat(cause.getMessage())
                        .contains("missing entries for")
                        .contains("HEADPHONES")
                        .contains("NOTEBOOK")
                        .contains("REFRIGERATOR")
                        .contains("SMART_TV"));
    }

    @Test
    void packagePrivateConstructor_acceptsCategoryRankings() {
        AttributeMetadata custom = new AttributeMetadata(
                Map.of("battery", AttributeMetadata.Direction.HIGHER_BETTER),
                Map.of(Category.SMARTPHONE, List.of("buyBox.price", "rating")));

        assertThat(custom.rankingPathsFor(Category.SMARTPHONE))
                .containsExactly("buyBox.price", "rating");
        assertThat(custom.rankingPathsFor(Category.NOTEBOOK)).isEmpty();
    }

    @Test
    void packagePrivateConstructor_exposesArbitraryMappingsForTesting() {
        AttributeMetadata custom = new AttributeMetadata(Map.of(
                "battery_life", AttributeMetadata.Direction.HIGHER_BETTER,
                "noise_level", AttributeMetadata.Direction.LOWER_BETTER));

        assertThat(custom.directionFor("battery_life")).isEqualTo(AttributeMetadata.Direction.HIGHER_BETTER);
        assertThat(custom.directionFor("noise_level")).isEqualTo(AttributeMetadata.Direction.LOWER_BETTER);
        assertThat(custom.directionFor("battery")).isEqualTo(AttributeMetadata.Direction.NONE);
    }

    @Test
    void parser_isTolerantToMissingBlankAndUnknownDirections_andCaseInsensitive() throws Exception {
        AttributeMetadata loaded = invokeLoadFromClasspath("/test-attribute-metadata-malformed.json");

        assertThat(loaded.directionFor("good_high")).isEqualTo(AttributeMetadata.Direction.HIGHER_BETTER);
        assertThat(loaded.directionFor("good_low")).isEqualTo(AttributeMetadata.Direction.LOWER_BETTER);
        assertThat(loaded.directionFor("missing_dir")).isEqualTo(AttributeMetadata.Direction.NONE);
        assertThat(loaded.directionFor("blank_dir")).isEqualTo(AttributeMetadata.Direction.NONE);
        assertThat(loaded.directionFor("weird_dir")).isEqualTo(AttributeMetadata.Direction.NONE);
        assertThat(loaded.directionFor("case_test")).isEqualTo(AttributeMetadata.Direction.HIGHER_BETTER);
    }

    @Test
    void parser_returnsEmptyRegistryWhenResourceIsMissing() throws Exception {
        AttributeMetadata loaded = invokeLoadFromClasspath("/this-resource-does-not-exist.json");
        assertThat(loaded.directionFor("anything")).isEqualTo(AttributeMetadata.Direction.NONE);
    }

    private static AttributeMetadata invokeLoadFromClasspath(String resource) throws Exception {
        Method method = AttributeMetadata.class.getDeclaredMethod("loadFromClasspath", String.class);
        method.setAccessible(true);
        return (AttributeMetadata) method.invoke(null, resource);
    }
}
