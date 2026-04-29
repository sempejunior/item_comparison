package com.hackerrank.sample.service.compare;

import com.hackerrank.sample.exception.InvalidCompareRequestException;
import com.hackerrank.sample.exception.ProductNotFoundException;
import com.hackerrank.sample.exception.ProductsNotFoundException;
import com.hackerrank.sample.model.BuyBox;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.CompareResponse;
import com.hackerrank.sample.model.Condition;
import com.hackerrank.sample.model.Language;
import com.hackerrank.sample.model.ProductDetail;
import com.hackerrank.sample.service.ProductService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompareServiceTest {

    private static ProductDetail product(long id, BigDecimal price) {
        return new ProductDetail(id, "P" + id, "d", "img", 4.0, Category.SMARTPHONE,
                Map.of("battery", (3000 + id * 100) + " mAh"),
                List.of(),
                new BuyBox(id, "S", "Seller", 4, price, "BRL", Condition.NEW, true, 5));
    }

    @Test
    void happyPath_returnsItemsAndDifferences() {
        ProductService ps = mock(ProductService.class);
        when(ps.getById(eq(1L))).thenReturn(product(1L, new BigDecimal("4000")));
        when(ps.getById(eq(2L))).thenReturn(product(2L, new BigDecimal("5000")));
        CompareService svc = new CompareService(ps);

        CompareResponse out = svc.compare(List.of(1L, 2L), null, Language.PT_BR);

        assertThat(out.items()).hasSize(2);
        assertThat(out.language()).isEqualTo("pt-BR");
        assertThat(out.fields()).isNull();
        assertThat(out.crossCategory()).isFalse();
        assertThat(out.exclusiveAttributes()).isNull();
        assertThat(out.differences()).extracting(d -> d.path()).contains("buyBox.price");
    }

    @Test
    void crossCategory_setsFlagAndExclusiveAttributes() {
        ProductDetail phone = new ProductDetail(1L, "Phone", "d", "img", 4.0, Category.SMARTPHONE,
                Map.of("battery", "4000 mAh", "memory", "8 GB", "brand", "Samsung"),
                List.of(),
                new BuyBox(1L, "S", "Seller", 4, new BigDecimal("4000"), "BRL", Condition.NEW, true, 5));
        ProductDetail laptop = new ProductDetail(21L, "Laptop", "d", "img", 4.5, Category.NOTEBOOK,
                Map.of("memory", "16 GB", "brand", "Lenovo", "cpu", "i7"),
                List.of(),
                new BuyBox(21L, "S2", "Seller2", 4, new BigDecimal("12000"), "BRL", Condition.NEW, true, 3));

        ProductService ps = mock(ProductService.class);
        when(ps.getById(eq(1L))).thenReturn(phone);
        when(ps.getById(eq(21L))).thenReturn(laptop);
        CompareService svc = new CompareService(ps);

        CompareResponse out = svc.compare(List.of(1L, 21L), null, Language.PT_BR);

        assertThat(out.crossCategory()).isTrue();
        assertThat(out.exclusiveAttributes()).containsOnlyKeys(1L, 21L);
        assertThat(out.exclusiveAttributes().get(1L)).containsExactly("battery");
        assertThat(out.exclusiveAttributes().get(21L)).containsExactly("cpu");
    }

    @Test
    void duplicateIdsAreDeduped_orderPreserved() {
        ProductService ps = mock(ProductService.class);
        when(ps.getById(eq(1L))).thenReturn(product(1L, new BigDecimal("4000")));
        when(ps.getById(eq(2L))).thenReturn(product(2L, new BigDecimal("5000")));
        CompareService svc = new CompareService(ps);

        CompareResponse out = svc.compare(List.of(1L, 2L, 1L), null, Language.PT_BR);
        assertThat(out.items()).extracting(i -> i.id()).containsExactly(1L, 2L);
    }

    @Test
    void singleId_after_dedup_throwsInvalid() {
        ProductService ps = mock(ProductService.class);
        CompareService svc = new CompareService(ps);
        assertThatThrownBy(() -> svc.compare(List.of(1L, 1L), null, Language.PT_BR))
                .isInstanceOf(InvalidCompareRequestException.class);
    }

    @Test
    void elevenIds_throwsInvalid() {
        ProductService ps = mock(ProductService.class);
        CompareService svc = new CompareService(ps);
        List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
        assertThatThrownBy(() -> svc.compare(ids, null, Language.PT_BR))
                .isInstanceOf(InvalidCompareRequestException.class);
    }

    @Test
    void unknownId_throwsProductsNotFound() {
        ProductService ps = mock(ProductService.class);
        when(ps.getById(eq(1L))).thenReturn(product(1L, new BigDecimal("4000")));
        when(ps.getById(eq(99L))).thenThrow(new ProductNotFoundException(99L));
        CompareService svc = new CompareService(ps);

        assertThatThrownBy(() -> svc.compare(List.of(1L, 99L), null, Language.PT_BR))
                .isInstanceOf(ProductsNotFoundException.class)
                .satisfies(ex -> assertThat(((ProductsNotFoundException) ex).getMissingIds()).containsExactly(99L));
    }

    @Test
    void sparseFields_populatesFieldsListInResponse() {
        ProductService ps = mock(ProductService.class);
        when(ps.getById(eq(1L))).thenReturn(product(1L, new BigDecimal("4000")));
        when(ps.getById(eq(2L))).thenReturn(product(2L, new BigDecimal("5000")));
        CompareService svc = new CompareService(ps);

        CompareResponse out = svc.compare(List.of(1L, 2L), "name,buyBox.price", Language.PT_BR);
        assertThat(out.fields()).containsExactly("name", "buyBox.price");
    }
}
