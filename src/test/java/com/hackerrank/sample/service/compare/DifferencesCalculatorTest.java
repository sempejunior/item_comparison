package com.hackerrank.sample.service.compare;

import com.hackerrank.sample.model.BuyBox;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.Condition;
import com.hackerrank.sample.model.DifferenceEntry;
import com.hackerrank.sample.model.ProductDetail;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DifferencesCalculatorTest {

    private static ProductDetail product(long id, String battery, String memory, String storage,
                                         BigDecimal price, String currency) {
        BuyBox buyBox = new BuyBox(id, "S", "Seller", 4, price, currency, Condition.NEW, true, 5);
        return new ProductDetail(id, "P" + id, "d", "img", 4.0, Category.SMARTPHONE,
                Map.of("battery", battery, "memory", memory, "storage", storage, "brand", "Samsung"),
                List.of(), buyBox);
    }

    @Test
    void onlyDifferingPathsAppear() {
        ProductDetail a = product(1L, "4000 mAh", "8 GB", "128 GB", new BigDecimal("4000"), "BRL");
        ProductDetail b = product(2L, "3000 mAh", "8 GB", "128 GB", new BigDecimal("5000"), "BRL");
        DifferencesCalculator.DiffResult out = DifferencesCalculator.compute(List.of(a, b), FieldSet.defaultSet());

        assertThat(out.differences()).extracting(DifferenceEntry::path)
                .contains("buyBox.price", "attributes.battery")
                .doesNotContain("attributes.memory", "attributes.storage");
        assertThat(out.exclusiveAttributes()).isEmpty();
    }

    @Test
    void buyBoxPriceWinnerIsLower() {
        ProductDetail a = product(1L, "4000 mAh", "8 GB", "128 GB", new BigDecimal("4000"), "BRL");
        ProductDetail b = product(2L, "4000 mAh", "8 GB", "128 GB", new BigDecimal("5000"), "BRL");
        DifferencesCalculator.DiffResult out = DifferencesCalculator.compute(List.of(a, b), FieldSet.defaultSet());

        DifferenceEntry priceEntry = out.differences().stream()
                .filter(e -> "buyBox.price".equals(e.path())).findFirst().orElseThrow();
        assertThat(priceEntry.isComparable()).isTrue();
        assertThat(priceEntry.winnerId()).isEqualTo(1L);
    }

    @Test
    void batteryWinnerIsHigher() {
        ProductDetail a = product(1L, "3000 mAh", "8 GB", "128 GB", new BigDecimal("4000"), "BRL");
        ProductDetail b = product(2L, "4500 mAh", "8 GB", "128 GB", new BigDecimal("4000"), "BRL");
        DifferencesCalculator.DiffResult out = DifferencesCalculator.compute(List.of(a, b), FieldSet.defaultSet());

        DifferenceEntry batteryEntry = out.differences().stream()
                .filter(e -> "attributes.battery".equals(e.path())).findFirst().orElseThrow();
        assertThat(batteryEntry.winnerId()).isEqualTo(2L);
    }

    @Test
    void currencyMismatchIsNotComparable() {
        ProductDetail a = product(1L, "4000 mAh", "8 GB", "128 GB", new BigDecimal("4000"), "BRL");
        ProductDetail b = product(2L, "4000 mAh", "8 GB", "128 GB", new BigDecimal("1000"), "USD");
        DifferencesCalculator.DiffResult out = DifferencesCalculator.compute(List.of(a, b), FieldSet.defaultSet());

        DifferenceEntry priceEntry = out.differences().stream()
                .filter(e -> "buyBox.price".equals(e.path())).findFirst().orElseThrow();
        assertThat(priceEntry.isComparable()).isFalse();
        assertThat(priceEntry.winnerId()).isNull();
    }

    @Test
    void brandStringIsNotComparable() {
        ProductDetail a = new ProductDetail(1L, "P1", "d", "img", 4.0, Category.SMARTPHONE,
                Map.of("brand", "Samsung"), List.of(), null);
        ProductDetail b = new ProductDetail(2L, "P2", "d", "img", 4.0, Category.SMARTPHONE,
                Map.of("brand", "Apple"), List.of(), null);
        DifferencesCalculator.DiffResult out = DifferencesCalculator.compute(List.of(a, b), FieldSet.defaultSet());

        DifferenceEntry brand = out.differences().stream()
                .filter(e -> "attributes.brand".equals(e.path())).findFirst().orElseThrow();
        assertThat(brand.isComparable()).isFalse();
    }

    @Test
    void sparseFieldsRestrictDiffScope() {
        ProductDetail a = product(1L, "4000 mAh", "8 GB", "128 GB", new BigDecimal("4000"), "BRL");
        ProductDetail b = product(2L, "3000 mAh", "16 GB", "128 GB", new BigDecimal("5000"), "BRL");
        FieldSet f = FieldSetProjector.parse("buyBox.price");
        DifferencesCalculator.DiffResult out = DifferencesCalculator.compute(List.of(a, b), f);

        assertThat(out.differences()).extracting(DifferenceEntry::path).containsExactly("buyBox.price");
    }

    @Test
    void mixedAttributeKeys_diffOperatesOnIntersection_andExclusivesPopulated() {
        ProductDetail phone = new ProductDetail(1L, "Phone", "d", "img", 4.0, Category.SMARTPHONE,
                Map.of("battery", "4000 mAh", "memory", "8 GB", "storage", "256 GB",
                        "brand", "Samsung", "os", "Android"),
                List.of(),
                new BuyBox(1L, "S", "Seller", 4, new BigDecimal("4000"), "BRL", Condition.NEW, true, 5));
        ProductDetail laptop = new ProductDetail(21L, "Laptop", "d", "img", 4.5, Category.NOTEBOOK,
                Map.of("memory", "16 GB", "storage", "1024 GB", "brand", "Lenovo", "cpu", "i7"),
                List.of(),
                new BuyBox(21L, "S2", "Seller2", 4, new BigDecimal("12000"), "BRL", Condition.NEW, true, 3));

        DifferencesCalculator.DiffResult out = DifferencesCalculator.compute(List.of(phone, laptop), FieldSet.defaultSet());

        assertThat(out.differences()).extracting(DifferenceEntry::path)
                .contains("buyBox.price", "attributes.memory", "attributes.storage", "attributes.brand")
                .doesNotContain("attributes.battery", "attributes.os", "attributes.cpu");
        assertThat(out.exclusiveAttributes()).containsKeys(1L, 21L);
        assertThat(out.exclusiveAttributes().get(1L)).containsExactly("battery", "os");
        assertThat(out.exclusiveAttributes().get(21L)).containsExactly("cpu");
    }

    @Test
    void sparseOverride_includesPathEvenWhenMissingOnOneSide() {
        ProductDetail phone = new ProductDetail(1L, "Phone", "d", "img", 4.0, Category.SMARTPHONE,
                Map.of("battery", "4000 mAh", "memory", "8 GB"),
                List.of(),
                new BuyBox(1L, "S", "Seller", 4, new BigDecimal("4000"), "BRL", Condition.NEW, true, 5));
        ProductDetail laptop = new ProductDetail(21L, "Laptop", "d", "img", 4.5, Category.NOTEBOOK,
                Map.of("memory", "16 GB"),
                List.of(),
                new BuyBox(21L, "S2", "Seller2", 4, new BigDecimal("12000"), "BRL", Condition.NEW, true, 3));

        FieldSet f = FieldSetProjector.parse("attributes.battery");
        DifferencesCalculator.DiffResult out = DifferencesCalculator.compute(List.of(phone, laptop), f);

        DifferenceEntry battery = out.differences().stream()
                .filter(e -> "attributes.battery".equals(e.path())).findFirst().orElseThrow();
        assertThat(battery.isComparable()).isFalse();
        assertThat(battery.values().get(21L)).isNull();
        assertThat(out.exclusiveAttributes()).containsKey(1L);
        assertThat(out.exclusiveAttributes().get(1L)).containsExactly("battery");
    }

    @Test
    void customMetadataOverload_overridesDirectionForArbitraryAttribute() {
        ProductDetail a = new ProductDetail(1L, "P1", "d", "img", 4.0, Category.SMARTPHONE,
                Map.of("noise_level", "30 dB"), List.of(), null);
        ProductDetail b = new ProductDetail(2L, "P2", "d", "img", 4.0, Category.SMARTPHONE,
                Map.of("noise_level", "45 dB"), List.of(), null);

        FieldSet f = FieldSetProjector.parse("attributes.noise_level");
        AttributeMetadata defaultRegistry = AttributeMetadata.defaultRegistry();
        AttributeMetadata custom = new AttributeMetadata(Map.of(
                "noise_level", AttributeMetadata.Direction.LOWER_BETTER));

        DifferencesCalculator.DiffResult withDefault =
                DifferencesCalculator.compute(List.of(a, b), f, defaultRegistry);
        DifferenceEntry defaultEntry = withDefault.differences().stream()
                .filter(e -> "attributes.noise_level".equals(e.path())).findFirst().orElseThrow();
        assertThat(defaultEntry.isComparable()).isFalse();
        assertThat(defaultEntry.winnerId()).isNull();

        DifferencesCalculator.DiffResult withCustom =
                DifferencesCalculator.compute(List.of(a, b), f, custom);
        DifferenceEntry customEntry = withCustom.differences().stream()
                .filter(e -> "attributes.noise_level".equals(e.path())).findFirst().orElseThrow();
        assertThat(customEntry.isComparable()).isTrue();
        assertThat(customEntry.winnerId()).isEqualTo(1L);
    }
}
