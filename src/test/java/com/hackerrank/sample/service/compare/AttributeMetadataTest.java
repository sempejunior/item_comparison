package com.hackerrank.sample.service.compare;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
