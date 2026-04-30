package com.hackerrank.sample.service;

import com.hackerrank.sample.exception.ProductNotFoundException;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.Condition;
import com.hackerrank.sample.model.ProductDetail;
import com.hackerrank.sample.repository.CatalogProductEntity;
import com.hackerrank.sample.repository.CatalogProductRepository;
import com.hackerrank.sample.repository.OfferEntity;
import com.hackerrank.sample.repository.OfferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
                .thenReturn(new PageImpl<>(List.of(p)));

        ProductService service = new ProductService(repo, offers);
        var page = service.list(null, PageRequest.of(0, 20));
        assertThat(page.items()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1L);
    }

    @Test
    void getByIds_nullReturnsEmptyAndSkipsRepositories() {
        CatalogProductRepository repo = mock(CatalogProductRepository.class);
        OfferRepository offers = mock(OfferRepository.class);
        ProductService service = new ProductService(repo, offers);

        assertThat(service.getByIds(null)).isEmpty();

        verify(repo, never()).findAllByIdIn(any());
        verify(offers, never()).findAllByCatalogProductIdIn(any());
    }

    @Test
    void getByIds_emptyReturnsEmptyAndSkipsRepositories() {
        CatalogProductRepository repo = mock(CatalogProductRepository.class);
        OfferRepository offers = mock(OfferRepository.class);
        ProductService service = new ProductService(repo, offers);

        assertThat(service.getByIds(List.of())).isEmpty();

        verify(repo, never()).findAllByIdIn(any());
        verify(offers, never()).findAllByCatalogProductIdIn(any());
    }

    @Test
    void getByIds_batchesRepositoryCalls_preservesRequestOrder_andGroupsOffersPerProduct() {
        CatalogProductRepository repo = mock(CatalogProductRepository.class);
        OfferRepository offers = mock(OfferRepository.class);

        CatalogProductEntity p1 = new CatalogProductEntity(
                1L, "Phone A", null, null, 4.5, Category.SMARTPHONE, Map.of("memory", "8 GB"));
        CatalogProductEntity p2 = new CatalogProductEntity(
                2L, "Phone B", null, null, 4.0, Category.SMARTPHONE, Map.of("memory", "12 GB"));

        OfferEntity offer1a = offer(101L, 1L, "s1", new BigDecimal("4000.00"));
        OfferEntity offer1b = offer(102L, 1L, "s2", new BigDecimal("4200.00"));
        OfferEntity offer2 = offer(201L, 2L, "s3", new BigDecimal("5000.00"));

        when(repo.findAllByIdIn(any())).thenReturn(List.of(p2, p1));
        when(offers.findAllByCatalogProductIdIn(any()))
                .thenReturn(List.of(offer2, offer1a, offer1b));

        ProductService service = new ProductService(repo, offers);
        List<ProductDetail> result = service.getByIds(List.of(2L, 1L));

        assertThat(result).extracting(ProductDetail::id).containsExactly(2L, 1L);
        assertThat(result.get(0).offers()).extracting(ProductDetail.Offer::id)
                .containsExactlyInAnyOrder(201L);
        assertThat(result.get(1).offers()).extracting(ProductDetail.Offer::id)
                .containsExactlyInAnyOrder(101L, 102L);

        verify(repo).findAllByIdIn(any(Collection.class));
        verify(offers).findAllByCatalogProductIdIn(any(Collection.class));
    }

    @Test
    void getByIds_throwsProductNotFoundForMissingId() {
        CatalogProductRepository repo = mock(CatalogProductRepository.class);
        OfferRepository offers = mock(OfferRepository.class);

        CatalogProductEntity p1 = new CatalogProductEntity(
                1L, "Phone A", null, null, 4.5, Category.SMARTPHONE, Map.of());
        when(repo.findAllByIdIn(any())).thenReturn(List.of(p1));
        when(offers.findAllByCatalogProductIdIn(any())).thenReturn(List.of());

        ProductService service = new ProductService(repo, offers);

        assertThatThrownBy(() -> service.getByIds(List.of(1L, 999L)))
                .isInstanceOf(ProductNotFoundException.class)
                .extracting(ex -> ((ProductNotFoundException) ex).getProductId())
                .isEqualTo(999L);
    }

    @Test
    void getByIds_productWithoutOffers_yieldsEmptyOffersAndNullBuyBox() {
        CatalogProductRepository repo = mock(CatalogProductRepository.class);
        OfferRepository offers = mock(OfferRepository.class);

        CatalogProductEntity p1 = new CatalogProductEntity(
                7L, "Lonely", null, null, 4.0, Category.SMARTPHONE, Map.of());
        when(repo.findAllByIdIn(any())).thenReturn(List.of(p1));
        when(offers.findAllByCatalogProductIdIn(any())).thenReturn(List.of());

        ProductService service = new ProductService(repo, offers);
        List<ProductDetail> result = service.getByIds(List.of(7L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).offers()).isEmpty();
        assertThat(result.get(0).buyBox()).isNull();
    }

    private static OfferEntity offer(long id, long productId, String sellerId, BigDecimal price) {
        return new OfferEntity(
                id, productId, sellerId, "Seller " + sellerId, 4,
                price, "BRL", Condition.NEW, true, 10);
    }
}
