package com.hackerrank.sample.service.compare;

import com.hackerrank.sample.model.Condition;
import com.hackerrank.sample.repository.OfferEntity;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * BuyBox heuristic per ADR-0004 / SPEC-002 §3.3:
 * stock {@code > 0} → tier preference (NEW > REFURBISHED > USED) → lowest
 * price → highest reputation → lowest sellerId lex.
 */
public final class BuyBoxSelector {

    private BuyBoxSelector() {
    }

    public static Optional<OfferEntity> select(List<OfferEntity> offers) {
        if (offers == null || offers.isEmpty()) {
            return Optional.empty();
        }
        return offers.stream()
                .filter(o -> o.getStock() != null && o.getStock() > 0)
                .min(Comparator
                        .comparingInt((OfferEntity o) -> tierRank(o.getCondition()))
                        .thenComparing(OfferEntity::getPrice)
                        .thenComparing(Comparator.comparingInt(OfferEntity::getSellerReputation).reversed())
                        .thenComparing(OfferEntity::getSellerId));
    }

    private static int tierRank(Condition condition) {
        return switch (condition) {
            case NEW -> 0;
            case REFURBISHED -> 1;
            case USED -> 2;
        };
    }
}
