package com.hackerrank.sample.service.insights;

import com.hackerrank.sample.model.BuyBox;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.Language;
import com.hackerrank.sample.model.ProductDetail;
import com.hackerrank.sample.model.insights.CategoryInsightsResponse;
import com.hackerrank.sample.model.insights.Coverage;
import com.hackerrank.sample.model.insights.RankedItem;
import com.hackerrank.sample.model.insights.RankingEntry;
import com.hackerrank.sample.model.insights.Spread;
import com.hackerrank.sample.model.insights.TopItem;
import com.hackerrank.sample.service.ProductService;
import com.hackerrank.sample.service.ai.SummaryService;
import com.hackerrank.sample.service.compare.AttributeMetadata;
import com.hackerrank.sample.service.compare.NumericValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Computes deterministic per-attribute rankings and a top-K picker for
 * an entire category, then optionally enriches the response with a
 * natural-language summary produced by {@link SummaryService}.
 *
 * <p>The deterministic stage never depends on the LLM. The summary is
 * best-effort and absent when the LLM is unavailable (SPEC-005 FR-11).</p>
 */
@Service
public class CategoryInsightsService {

    private static final Logger log = LoggerFactory.getLogger(CategoryInsightsService.class);

    private final ProductService productService;
    private final AttributeMetadata metadata;
    private final SummaryService summaryService;

    @Autowired
    public CategoryInsightsService(ProductService productService, SummaryService summaryService) {
        this(productService, summaryService, AttributeMetadata.defaultRegistry());
    }

    CategoryInsightsService(ProductService productService, SummaryService summaryService, AttributeMetadata metadata) {
        this.productService = productService;
        this.summaryService = summaryService;
        this.metadata = metadata;
    }

    public CategoryInsightsResponse insights(Category category, int topK, Language language) {
        List<ProductDetail> products = productService.getAllByCategory(category);
        int productCount = products.size();

        List<RankingEntry> rankings = productCount < 2
                ? List.of()
                : computeRankings(category, products);
        List<TopItem> topItems = pickTopItems(products, topK);

        Optional<String> summary = productCount < 2
                ? Optional.empty()
                : summaryService.summariseCategoryInsights(category, productCount, rankings, topItems, language);

        return new CategoryInsightsResponse(
                category,
                productCount,
                rankings,
                topItems,
                language.tag(),
                summary.orElse(null));
    }

    private List<RankingEntry> computeRankings(Category category, List<ProductDetail> products) {
        List<String> paths = metadata.rankingPathsFor(category);
        List<RankingEntry> result = new ArrayList<>(paths.size());
        for (String path : paths) {
            result.add(rankPath(path, products));
        }
        return result;
    }

    private RankingEntry rankPath(String path, List<ProductDetail> products) {
        List<ValuedProduct> withValue = new ArrayList<>();
        for (ProductDetail p : products) {
            Object value = readPath(path, p);
            if (value != null) {
                withValue.add(new ValuedProduct(p, value));
            }
        }
        Coverage coverage = new Coverage(withValue.size(), products.size());

        Direction direction = directionFor(path);
        if (direction == Direction.NONE) {
            log.warn("Skipping ranking for path '{}' — no direction in metadata", path);
            return new RankingEntry(path, false, coverage, null, null, null);
        }
        if (withValue.size() < 2) {
            return new RankingEntry(path, false, coverage, null, null, null);
        }

        List<NumericValuedProduct> numeric = new ArrayList<>(withValue.size());
        String unit = null;
        for (ValuedProduct vp : withValue) {
            NumericValue nv = NumericValue.parse(vp.value);
            if (nv == null) {
                return new RankingEntry(path, false, coverage, null, null, null);
            }
            if (unit == null) {
                unit = nv.unit();
            } else if (!unit.equalsIgnoreCase(nv.unit())) {
                return new RankingEntry(path, false, coverage, null, null, null);
            }
            numeric.add(new NumericValuedProduct(vp.product, vp.value, nv));
        }
        numeric.sort(rankComparator(direction));

        NumericValuedProduct top = numeric.get(0);
        NumericValuedProduct second = numeric.get(1);
        Spread spread = computeSpread(numeric);
        return new RankingEntry(
                path,
                true,
                coverage,
                new RankedItem(top.product.id(), top.rawValue, top.product.name()),
                new RankedItem(second.product.id(), second.rawValue, second.product.name()),
                spread);
    }

    private Comparator<NumericValuedProduct> rankComparator(Direction direction) {
        Comparator<NumericValuedProduct> byMagnitude = Comparator.comparing(n -> n.numeric.magnitude());
        if (direction == Direction.HIGHER_BETTER) {
            byMagnitude = byMagnitude.reversed();
        }
        return byMagnitude.thenComparing(n -> n.product.id());
    }

    private Spread computeSpread(List<NumericValuedProduct> numeric) {
        List<BigDecimal> magnitudes = new ArrayList<>(numeric.size());
        for (NumericValuedProduct n : numeric) {
            magnitudes.add(n.numeric.magnitude());
        }
        magnitudes.sort(Comparator.naturalOrder());
        BigDecimal min = magnitudes.get(0);
        BigDecimal max = magnitudes.get(magnitudes.size() - 1);
        BigDecimal median;
        int size = magnitudes.size();
        if (size % 2 == 1) {
            median = magnitudes.get(size / 2);
        } else {
            BigDecimal a = magnitudes.get(size / 2 - 1);
            BigDecimal b = magnitudes.get(size / 2);
            median = a.add(b).divide(BigDecimal.valueOf(2));
        }
        return new Spread(min, max, median);
    }

    private Direction directionFor(String path) {
        if ("buyBox.price".equals(path)) {
            return Direction.LOWER_BETTER;
        }
        if ("rating".equals(path)) {
            return Direction.HIGHER_BETTER;
        }
        if (path.startsWith("attributes.")) {
            String key = path.substring("attributes.".length());
            return switch (metadata.directionFor(key)) {
                case HIGHER_BETTER -> Direction.HIGHER_BETTER;
                case LOWER_BETTER -> Direction.LOWER_BETTER;
                case NONE -> Direction.NONE;
            };
        }
        return Direction.NONE;
    }

    private Object readPath(String path, ProductDetail p) {
        if ("rating".equals(path)) {
            return p.rating();
        }
        if ("buyBox.price".equals(path)) {
            BuyBox b = p.buyBox();
            return b == null ? null : b.price();
        }
        if (path.startsWith("attributes.")) {
            String key = path.substring("attributes.".length());
            return p.attributes() == null ? null : p.attributes().get(key);
        }
        return null;
    }

    private List<TopItem> pickTopItems(List<ProductDetail> products, int topK) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<ProductDetail> sorted = new ArrayList<>(products);
        sorted.sort((a, b) -> {
            int byBuyBox = Boolean.compare(a.buyBox() == null, b.buyBox() == null);
            if (byBuyBox != 0) {
                return byBuyBox;
            }
            int byRating = Double.compare(
                    b.rating() == null ? -1 : b.rating(),
                    a.rating() == null ? -1 : a.rating());
            if (byRating != 0) {
                return byRating;
            }
            BigDecimal pa = a.buyBox() == null ? null : a.buyBox().price();
            BigDecimal pb = b.buyBox() == null ? null : b.buyBox().price();
            int byPrice;
            if (pa == null && pb == null) {
                byPrice = 0;
            } else if (pa == null) {
                byPrice = 1;
            } else if (pb == null) {
                byPrice = -1;
            } else {
                byPrice = pa.compareTo(pb);
            }
            if (byPrice != 0) {
                return byPrice;
            }
            return Long.compare(a.id(), b.id());
        });
        int limit = Math.min(topK, sorted.size());
        List<TopItem> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            ProductDetail p = sorted.get(i);
            BuyBox b = p.buyBox();
            out.add(new TopItem(
                    p.id(), p.name(), p.category(), p.imageUrl(),
                    b == null ? null : b.price(),
                    b == null ? null : b.currency(),
                    p.rating()));
        }
        return out;
    }

    private enum Direction { HIGHER_BETTER, LOWER_BETTER, NONE }

    private record ValuedProduct(ProductDetail product, Object value) {
    }

    private record NumericValuedProduct(ProductDetail product, Object rawValue, NumericValue numeric) {
    }
}
