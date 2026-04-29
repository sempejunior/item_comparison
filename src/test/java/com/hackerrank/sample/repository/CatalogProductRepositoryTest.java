package com.hackerrank.sample.repository;

import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.Condition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CatalogProductRepositoryTest {

    @Autowired
    private CatalogProductRepository catalogProductRepository;

    @Autowired
    private OfferRepository offerRepository;

    @Test
    void findAllByCategory_appliesFilterAndPagination() {
        catalogProductRepository.save(new CatalogProductEntity(
                1L, "S24", null, null, null, Category.SMARTPHONE, Map.of()));
        catalogProductRepository.save(new CatalogProductEntity(
                2L, "OLED", null, null, null, Category.SMART_TV, Map.of()));
        catalogProductRepository.save(new CatalogProductEntity(
                3L, "Pixel", null, null, null, Category.SMARTPHONE, Map.of()));

        Page<CatalogProductEntity> page = catalogProductRepository.findAllByCategory(
                Category.SMARTPHONE, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(CatalogProductEntity::getId)
                .containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void findAllByIdIn_returnsEmptyForEmptyCollection() {
        List<CatalogProductEntity> result = catalogProductRepository.findAllByIdIn(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void offerRepository_findAllByCatalogProductIdIn_groupsOffersByProduct() {
        catalogProductRepository.save(new CatalogProductEntity(
                10L, "S24", null, null, null, Category.SMARTPHONE, Map.of()));
        offerRepository.save(new OfferEntity(
                100L, 10L, "S1", "Seller", 4,
                new BigDecimal("100.00"), "BRL", Condition.NEW, Boolean.TRUE, 5));
        offerRepository.save(new OfferEntity(
                101L, 10L, "S2", "Seller2", 4,
                new BigDecimal("90.00"), "BRL", Condition.NEW, Boolean.TRUE, 0));

        List<OfferEntity> offers = offerRepository.findAllByCatalogProductIdIn(List.of(10L));
        assertThat(offers).hasSize(2);
    }
}
