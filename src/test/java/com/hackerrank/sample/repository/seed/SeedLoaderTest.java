package com.hackerrank.sample.repository.seed;

import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.Condition;
import com.hackerrank.sample.repository.CatalogProductRepository;
import com.hackerrank.sample.repository.OfferEntity;
import com.hackerrank.sample.repository.OfferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SeedLoaderTest {

    @Autowired
    private CatalogProductRepository products;

    @Autowired
    private OfferRepository offers;

    @Test
    void seedLoadsExpectedCounts() {
        assertThat(products.count()).isEqualTo(250L);
        assertThat(offers.count()).isGreaterThanOrEqualTo(500L);
    }

    @Test
    void seedCoversAllCategoriesEvenly() {
        for (Category c : Category.values()) {
            assertThat(products.findAllByCategory(c, PageRequest.of(0, 50))
                    .getTotalElements())
                    .as("category %s", c)
                    .isEqualTo(50L);
        }
    }

    @Test
    void zeroStockEdgeCaseIsPresent() {
        List<OfferEntity> productOffers = offers.findAllByCatalogProductId(50L);
        assertThat(productOffers).isNotEmpty();
        assertThat(productOffers).allMatch(o -> o.getStock() == 0);
    }

    @Test
    void usedRefurbishedOnlyEdgeCaseIsPresent() {
        List<OfferEntity> productOffers = offers.findAllByCatalogProductId(201L);
        assertThat(productOffers).isNotEmpty();
        assertThat(productOffers).noneMatch(o -> o.getCondition() == Condition.NEW);
    }
}
