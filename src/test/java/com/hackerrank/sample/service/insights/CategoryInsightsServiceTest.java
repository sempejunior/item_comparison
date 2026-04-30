package com.hackerrank.sample.service.insights;

import com.hackerrank.sample.model.BuyBox;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.Condition;
import com.hackerrank.sample.model.Language;
import com.hackerrank.sample.model.ProductDetail;
import com.hackerrank.sample.model.insights.CategoryInsightsResponse;
import com.hackerrank.sample.model.insights.RankingEntry;
import com.hackerrank.sample.service.ProductService;
import com.hackerrank.sample.service.ai.SummaryService;
import com.hackerrank.sample.service.compare.AttributeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryInsightsServiceTest {

    private ProductService productService;
    private SummaryService summaryService;
    private CategoryInsightsService service;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        summaryService = mock(SummaryService.class);
        when(summaryService.summariseCategoryInsights(any(), anyInt(), anyList(), anyList(), any(), any(), any()))
                .thenReturn(Optional.empty());
        service = new CategoryInsightsService(productService, summaryService, AttributeMetadata.defaultRegistry());
    }

    @Test
    void happyPath_returnsRankingsAndTopItemsForSmartphone() {
        when(productService.getAllByCategory(Category.SMARTPHONE)).thenReturn(List.of(
                product(1L, "Phone A", new BigDecimal("1500"), 4.6, Map.of("battery", "4000 mAh", "memory", "8 GB")),
                product(2L, "Phone B", new BigDecimal("1200"), 4.4, Map.of("battery", "5000 mAh", "memory", "6 GB")),
                product(3L, "Phone C", new BigDecimal("1800"), 4.8, Map.of("battery", "4500 mAh", "memory", "12 GB"))
        ));

        CategoryInsightsResponse response = service.insights(Category.SMARTPHONE, 5, Language.PT_BR);

        assertThat(response.category()).isEqualTo(Category.SMARTPHONE);
        assertThat(response.productCount()).isEqualTo(3);
        assertThat(response.language()).isEqualTo("pt-BR");
        assertThat(response.summary()).isNull();

        assertThat(response.rankings()).extracting(RankingEntry::path)
                .contains("buyBox.price", "rating", "attributes.battery", "attributes.memory");

        RankingEntry priceRanking = response.rankings().stream()
                .filter(r -> r.path().equals("buyBox.price")).findFirst().orElseThrow();
        assertThat(priceRanking.isComparable()).isTrue();
        assertThat(priceRanking.winner().id()).isEqualTo(2L);
        assertThat(priceRanking.coverage().withValue()).isEqualTo(3);

        RankingEntry batteryRanking = response.rankings().stream()
                .filter(r -> r.path().equals("attributes.battery")).findFirst().orElseThrow();
        assertThat(batteryRanking.winner().id()).isEqualTo(2L);

        assertThat(response.topItems()).hasSize(3);
        assertThat(response.topItems().get(0).id()).isEqualTo(3L);
    }

    @Test
    void categoryWithSingleProduct_yieldsEmptyRankingsAndNoSummary() {
        when(productService.getAllByCategory(Category.HEADPHONES)).thenReturn(List.of(
                product(50L, "Lone", new BigDecimal("100"), 4.0, Map.of("battery", "20 h"))
        ));

        CategoryInsightsResponse response = service.insights(Category.HEADPHONES, 5, Language.PT_BR);

        assertThat(response.productCount()).isEqualTo(1);
        assertThat(response.rankings()).isEmpty();
        assertThat(response.topItems()).hasSize(1);
        assertThat(response.summary()).isNull();
    }

    @Test
    void emptyCategory_yieldsEmptyRankingsAndTopItems() {
        when(productService.getAllByCategory(Category.NOTEBOOK)).thenReturn(List.of());

        CategoryInsightsResponse response = service.insights(Category.NOTEBOOK, 5, Language.PT_BR);

        assertThat(response.productCount()).isZero();
        assertThat(response.rankings()).isEmpty();
        assertThat(response.topItems()).isEmpty();
    }

    @Test
    void partialCoverage_reportsCoverageButStillRanksWhenTwoOrMoreHaveValue() {
        when(productService.getAllByCategory(Category.SMARTPHONE)).thenReturn(List.of(
                product(1L, "A", new BigDecimal("100"), 4.0, Map.of("battery", "4000 mAh")),
                product(2L, "B", new BigDecimal("200"), 4.0, Map.of("battery", "5000 mAh")),
                product(3L, "C", new BigDecimal("300"), 4.0, Map.of())
        ));

        CategoryInsightsResponse response = service.insights(Category.SMARTPHONE, 3, Language.PT_BR);

        RankingEntry battery = response.rankings().stream()
                .filter(r -> r.path().equals("attributes.battery")).findFirst().orElseThrow();
        assertThat(battery.coverage().withValue()).isEqualTo(2);
        assertThat(battery.coverage().total()).isEqualTo(3);
        assertThat(battery.isComparable()).isTrue();
        assertThat(battery.winner().id()).isEqualTo(2L);
    }

    @Test
    void picksAreComputedAndForwardedToSummaryService() {
        when(productService.getAllByCategory(Category.SMARTPHONE)).thenReturn(List.of(
                product(1L, "Cheap Phone", new BigDecimal("500"), 4.0, Map.of("battery", "3000 mAh")),
                product(2L, "Mid Phone", new BigDecimal("1000"), 4.5, Map.of("battery", "4000 mAh")),
                product(3L, "Premium Phone", new BigDecimal("3000"), 4.9, Map.of("battery", "5000 mAh"))
        ));

        service.insights(Category.SMARTPHONE, 5, Language.PT_BR);

        ArgumentCaptor<Picks> picksCaptor = ArgumentCaptor.forClass(Picks.class);
        verify(summaryService).summariseCategoryInsights(
                any(), anyInt(), anyList(), anyList(), picksCaptor.capture(), any(), any());

        Picks picks = picksCaptor.getValue();
        assertThat(picks).isNotNull();
        assertThat(picks.bestOverall().id()).isEqualTo(3L);
        assertThat(picks.bestOverall().rating()).isEqualTo(4.9);
        assertThat(picks.cheapest().id()).isEqualTo(1L);
        assertThat(picks.cheapest().price()).isEqualByComparingTo("500");
        assertThat(picks.bestValue().id()).isEqualTo(2L);
        assertThat(picks.bestOverall().reason()).contains("4.9");
        assertThat(picks.cheapest().reason()).containsIgnoringCase("lowest price");
        assertThat(picks.bestOverall().highlights())
                .contains("rating: 4.9", "battery: 5000 mAh");
        assertThat(picks.cheapest().highlights())
                .contains("price: 500");
    }

    @Test
    void picksAreNullWhenAllRatingsAndPricesMissing() {
        when(productService.getAllByCategory(Category.SMARTPHONE)).thenReturn(List.of(
                productNoRatingNoBuyBox(10L, "Bare A"),
                productNoRatingNoBuyBox(11L, "Bare B")
        ));

        service.insights(Category.SMARTPHONE, 5, Language.PT_BR);

        ArgumentCaptor<Picks> picksCaptor = ArgumentCaptor.forClass(Picks.class);
        verify(summaryService).summariseCategoryInsights(
                any(), anyInt(), anyList(), anyList(), picksCaptor.capture(), any(), any());

        assertThat(picksCaptor.getValue()).isNull();
    }

    @Test
    void picksTieBreakOnLowerIdWhenRatingAndPriceMatch() {
        when(productService.getAllByCategory(Category.SMARTPHONE)).thenReturn(List.of(
                product(7L, "Tie B", new BigDecimal("1000"), 4.5, Map.of()),
                product(3L, "Tie A", new BigDecimal("1000"), 4.5, Map.of())
        ));

        service.insights(Category.SMARTPHONE, 5, Language.PT_BR);

        ArgumentCaptor<Picks> picksCaptor = ArgumentCaptor.forClass(Picks.class);
        verify(summaryService).summariseCategoryInsights(
                any(), anyInt(), anyList(), anyList(), picksCaptor.capture(), any(), any());

        Picks picks = picksCaptor.getValue();
        assertThat(picks.bestOverall().id()).isEqualTo(3L);
        assertThat(picks.cheapest().id()).isEqualTo(3L);
        assertThat(picks.bestValue().id()).isEqualTo(3L);
    }

    @Test
    void bestValueFavorsHigherRatingPerPriceRatio() {
        when(productService.getAllByCategory(Category.SMARTPHONE)).thenReturn(List.of(
                product(1L, "High end", new BigDecimal("4000"), 4.9, Map.of()),
                product(2L, "Sweet spot", new BigDecimal("1000"), 4.6, Map.of()),
                product(3L, "Budget", new BigDecimal("500"), 4.0, Map.of())
        ));

        service.insights(Category.SMARTPHONE, 5, Language.PT_BR);

        ArgumentCaptor<Picks> picksCaptor = ArgumentCaptor.forClass(Picks.class);
        verify(summaryService).summariseCategoryInsights(
                any(), anyInt(), anyList(), anyList(), picksCaptor.capture(), any(), any());

        Picks picks = picksCaptor.getValue();
        assertThat(picks.bestValue().id()).isEqualTo(2L);
        assertThat(picks.bestOverall().id()).isEqualTo(1L);
        assertThat(picks.cheapest().id()).isEqualTo(3L);
    }

    @Test
    void filterByMinRating_shrinksProductCountAndRespectsFloor() {
        when(productService.getAllByCategory(Category.SMARTPHONE)).thenReturn(List.of(
                product(1L, "Low",  new BigDecimal("500"),  3.5, Map.of()),
                product(2L, "Mid",  new BigDecimal("1000"), 4.6, Map.of()),
                product(3L, "High", new BigDecimal("2000"), 4.9, Map.of())
        ));
        InsightsFilters filters = new InsightsFilters(null, null, 4.5);

        CategoryInsightsResponse response = service.insights(Category.SMARTPHONE, 5, Language.PT_BR, filters);

        assertThat(response.productCount()).isEqualTo(2);
        assertThat(response.appliedFilters()).isNotNull();
        assertThat(response.appliedFilters().minRating()).isEqualTo(4.5);
        assertThat(response.topItems()).extracting(t -> t.id()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void filterByPriceRange_excludesProductsWithoutBuyBox() {
        when(productService.getAllByCategory(Category.SMARTPHONE)).thenReturn(List.of(
                product(1L, "Cheap", new BigDecimal("500"), 4.0, Map.of()),
                product(2L, "Mid",   new BigDecimal("1500"), 4.0, Map.of()),
                product(3L, "Pricy", new BigDecimal("3000"), 4.0, Map.of()),
                productNoRatingNoBuyBox(4L, "NoBuyBox")
        ));
        InsightsFilters filters = new InsightsFilters(new BigDecimal("1000"), new BigDecimal("2000"), null);

        CategoryInsightsResponse response = service.insights(Category.SMARTPHONE, 5, Language.PT_BR, filters);

        assertThat(response.productCount()).isEqualTo(1);
        assertThat(response.topItems()).extracting(t -> t.id()).containsExactly(2L);
        assertThat(response.appliedFilters().minPrice()).isEqualByComparingTo("1000");
        assertThat(response.appliedFilters().maxPrice()).isEqualByComparingTo("2000");
    }

    @Test
    void filterReducesPoolBelowTwo_emptyRankingsAndNoSummary() {
        when(productService.getAllByCategory(Category.SMARTPHONE)).thenReturn(List.of(
                product(1L, "A", new BigDecimal("500"), 4.0, Map.of()),
                product(2L, "B", new BigDecimal("1000"), 4.0, Map.of())
        ));
        InsightsFilters filters = new InsightsFilters(new BigDecimal("999999"), null, null);

        CategoryInsightsResponse response = service.insights(Category.SMARTPHONE, 5, Language.PT_BR, filters);

        assertThat(response.productCount()).isZero();
        assertThat(response.rankings()).isEmpty();
        assertThat(response.topItems()).isEmpty();
        assertThat(response.summary()).isNull();
        assertThat(response.appliedFilters()).isNotNull();
    }

    @Test
    void unfilteredCall_omitsAppliedFiltersField() {
        when(productService.getAllByCategory(Category.SMARTPHONE)).thenReturn(List.of(
                product(1L, "A", new BigDecimal("500"), 4.0, Map.of()),
                product(2L, "B", new BigDecimal("1000"), 4.5, Map.of())
        ));

        CategoryInsightsResponse response = service.insights(Category.SMARTPHONE, 5, Language.PT_BR);

        assertThat(response.appliedFilters()).isNull();
    }

    @Test
    void insightsFilters_digestIsStableAcrossEqualInputs() {
        InsightsFilters a = new InsightsFilters(new BigDecimal("100.0"), new BigDecimal("200"), 4.5);
        InsightsFilters b = new InsightsFilters(new BigDecimal("100"), new BigDecimal("200.00"), 4.5);
        assertThat(a.digest()).isEqualTo(b.digest());
        assertThat(new InsightsFilters(null, null, null).digest()).isEqualTo("||");
    }

    @Test
    void insightsFilters_describePtBrIncludesAllBounds() {
        InsightsFilters f = new InsightsFilters(new BigDecimal("1500"), new BigDecimal("4000"), 4.5);
        String describePt = f.describe(Language.PT_BR);
        assertThat(describePt).contains("preco minimo").contains("R$").contains("nota minima");
        String describeEn = f.describe(Language.EN);
        assertThat(describeEn).contains("minimum price").contains("BRL").contains("minimum rating");
    }

    private static ProductDetail product(long id, String name, BigDecimal price, double rating, Map<String, Object> attrs) {
        BuyBox bb = new BuyBox(id, "s" + id, "Seller", 90, price, "BRL", Condition.NEW, true, 10);
        return new ProductDetail(id, name, "desc", "img", rating, Category.SMARTPHONE,
                new LinkedHashMap<>(attrs), List.of(), bb);
    }

    private static ProductDetail productNoRatingNoBuyBox(long id, String name) {
        return new ProductDetail(id, name, "desc", "img", null, Category.SMARTPHONE,
                new LinkedHashMap<>(), List.of(), null);
    }
}
