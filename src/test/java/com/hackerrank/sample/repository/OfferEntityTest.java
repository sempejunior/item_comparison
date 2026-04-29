package com.hackerrank.sample.repository;

import com.hackerrank.sample.model.Condition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfferEntityTest {

    private OfferEntity build(BigDecimal price, String currency, Integer reputation, Integer stock) {
        return new OfferEntity(
                101L,
                1L,
                "MELI-A1",
                "TechMarket",
                reputation,
                price,
                currency,
                Condition.NEW,
                Boolean.TRUE,
                stock);
    }

    @Test
    void buildsHappyPath() {
        OfferEntity offer = build(new BigDecimal("4999.005"), "BRL", 5, 12);
        assertThat(offer.getPrice()).isEqualByComparingTo(new BigDecimal("4999.01"));
        assertThat(offer.getCurrency()).isEqualTo("BRL");
        assertThat(offer.getStock()).isEqualTo(12);
    }

    @Test
    void rejectsNegativePrice_INV5() {
        assertThatThrownBy(() -> build(new BigDecimal("-0.01"), "BRL", 5, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price");
    }

    @Test
    void rejectsBadCurrency_INV6() {
        assertThatThrownBy(() -> build(BigDecimal.ONE, "XXY", 5, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void rejectsReputationOutOfRange_INV7() {
        assertThatThrownBy(() -> build(BigDecimal.ONE, "BRL", 6, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sellerReputation");
        assertThatThrownBy(() -> build(BigDecimal.ONE, "BRL", -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeStock_INV8() {
        assertThatThrownBy(() -> build(BigDecimal.ONE, "BRL", 3, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stock");
    }

    @Test
    void rejectsBlankSellerId() {
        assertThatThrownBy(() -> new OfferEntity(
                1L, 1L, "  ", "Name", 3, BigDecimal.ONE, "BRL",
                Condition.NEW, Boolean.TRUE, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sellerId");
    }
}
