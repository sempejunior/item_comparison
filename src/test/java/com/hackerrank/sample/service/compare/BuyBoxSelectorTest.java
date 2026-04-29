package com.hackerrank.sample.service.compare;

import com.hackerrank.sample.model.Condition;
import com.hackerrank.sample.repository.OfferEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BuyBoxSelectorTest {

    private OfferEntity offer(long id, String sellerId, int reputation,
                              String price, Condition condition, int stock) {
        return new OfferEntity(id, 1L, sellerId, "S" + id, reputation,
                new BigDecimal(price), "BRL", condition, Boolean.TRUE, stock);
    }

    @Test
    void emptyAndNullReturnEmpty() {
        assertThat(BuyBoxSelector.select(List.of())).isEmpty();
        assertThat(BuyBoxSelector.select(null)).isEmpty();
    }

    @Test
    void allOutOfStockReturnsEmpty() {
        var offers = List.of(
                offer(1, "A", 5, "10", Condition.NEW, 0),
                offer(2, "B", 4, "9", Condition.NEW, 0));
        assertThat(BuyBoxSelector.select(offers)).isEmpty();
    }

    @Test
    void prefersNewOverRefurbishedOverUsed() {
        var offers = List.of(
                offer(1, "A", 5, "100", Condition.USED, 5),
                offer(2, "B", 5, "200", Condition.REFURBISHED, 5),
                offer(3, "C", 5, "300", Condition.NEW, 5));
        assertThat(BuyBoxSelector.select(offers)).map(OfferEntity::getId).contains(3L);
    }

    @Test
    void withinTierPicksLowestPrice() {
        var offers = List.of(
                offer(1, "A", 5, "300", Condition.NEW, 5),
                offer(2, "B", 4, "200", Condition.NEW, 5));
        assertThat(BuyBoxSelector.select(offers)).map(OfferEntity::getId).contains(2L);
    }

    @Test
    void priceTieBreaksOnReputationThenSellerIdLex() {
        var offers = List.of(
                offer(1, "Z", 4, "100", Condition.NEW, 5),
                offer(2, "A", 5, "100", Condition.NEW, 5),
                offer(3, "M", 5, "100", Condition.NEW, 5));
        Optional<OfferEntity> winner = BuyBoxSelector.select(offers);
        assertThat(winner).map(OfferEntity::getId).contains(2L);
    }

    @Test
    void onlyUsedAvailableSelectsUsed() {
        var offers = List.of(
                offer(1, "A", 5, "100", Condition.USED, 3));
        assertThat(BuyBoxSelector.select(offers)).map(OfferEntity::getId).contains(1L);
    }
}
