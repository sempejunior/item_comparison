package com.hackerrank.sample.service;

import com.hackerrank.sample.exception.ProductNotFoundException;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.repository.CatalogProductEntity;
import com.hackerrank.sample.repository.CatalogProductRepository;
import com.hackerrank.sample.repository.OfferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    @Test
    void getById_throwsWhenMissing() {
        CatalogProductRepository repo = mock(CatalogProductRepository.class);
        OfferRepository offers = mock(OfferRepository.class);
        when(repo.findById(eq(99L))).thenReturn(Optional.empty());

        ProductService service = new ProductService(repo, offers);
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getById_returnsDetailWithoutBuyBoxWhenNoStock() {
        CatalogProductRepository repo = mock(CatalogProductRepository.class);
        OfferRepository offers = mock(OfferRepository.class);
        CatalogProductEntity p = new CatalogProductEntity(
                1L, "X", null, null, 4.0, Category.SMARTPHONE, Map.of("memory", "8 GB"));
        when(repo.findById(eq(1L))).thenReturn(Optional.of(p));
        when(offers.findAllByCatalogProductId(eq(1L))).thenReturn(List.of());

        ProductService service = new ProductService(repo, offers);
        var detail = service.getById(1L);
        assertThat(detail.id()).isEqualTo(1L);
        assertThat(detail.buyBox()).isNull();
    }

    @Test
    void list_paginates() {
        CatalogProductRepository repo = mock(CatalogProductRepository.class);
        OfferRepository offers = mock(OfferRepository.class);
        CatalogProductEntity p = new CatalogProductEntity(
                1L, "X", null, null, 4.0, Category.SMARTPHONE, Map.of());
        when(repo.findAll(PageRequest.of(0, 20)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(p)));

        ProductService service = new ProductService(repo, offers);
        var page = service.list(null, PageRequest.of(0, 20));
        assertThat(page.items()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1L);
    }
}
