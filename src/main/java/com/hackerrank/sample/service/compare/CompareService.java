package com.hackerrank.sample.service.compare;

import com.hackerrank.sample.exception.InvalidCompareRequestException;
import com.hackerrank.sample.exception.ProductNotFoundException;
import com.hackerrank.sample.exception.ProductsNotFoundException;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.CompareItem;
import com.hackerrank.sample.model.CompareResponse;
import com.hackerrank.sample.model.Language;
import com.hackerrank.sample.model.ProductDetail;
import com.hackerrank.sample.service.ProductService;
import com.hackerrank.sample.service.ai.SummaryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class CompareService {

    private static final int MIN_IDS = 2;
    private static final int MAX_IDS = 10;

    private final ProductService productService;
    private final SummaryService summaryService;

    public CompareService(ProductService productService, SummaryService summaryService) {
        this.productService = productService;
        this.summaryService = summaryService;
    }

    public CompareResponse compare(List<Long> ids, String fieldsCsv, Language language) {
        if (ids == null || ids.isEmpty()) {
            throw new InvalidCompareRequestException("ids parameter is required");
        }
        List<Long> deduped = new ArrayList<>(new LinkedHashSet<>(ids));
        if (deduped.size() < MIN_IDS || deduped.size() > MAX_IDS) {
            throw new InvalidCompareRequestException(
                    "ids must contain between " + MIN_IDS + " and " + MAX_IDS + " unique values (got " + deduped.size() + ")");
        }
        FieldSet fields = FieldSetProjector.parse(fieldsCsv);
        List<ProductDetail> products = resolve(deduped);
        List<CompareItem> items = new ArrayList<>(products.size());
        for (ProductDetail p : products) {
            items.add(FieldSetProjector.project(p, fields));
        }
        DifferencesCalculator.DiffResult diff = DifferencesCalculator.compute(products, fields);
        boolean crossCategory = isCrossCategory(products);
        Map<Long, List<String>> exclusives = diff.exclusiveAttributes().isEmpty() ? null : diff.exclusiveAttributes();
        Optional<String> summary = summaryService.summarise(items, diff.differences(), language);
        return new CompareResponse(
                fields.explicit() ? fields.rawPaths() : null,
                language.tag(),
                crossCategory,
                items,
                diff.differences(),
                exclusives,
                summary.orElse(null));
    }

    private static boolean isCrossCategory(List<ProductDetail> products) {
        Set<Category> categories = new LinkedHashSet<>();
        for (ProductDetail p : products) {
            categories.add(p.category());
        }
        return categories.size() > 1;
    }

    private List<ProductDetail> resolve(List<Long> ids) {
        List<ProductDetail> resolved = new ArrayList<>(ids.size());
        List<Long> missing = new ArrayList<>();
        for (Long id : ids) {
            try {
                resolved.add(productService.getById(id));
            } catch (ProductNotFoundException ex) {
                missing.add(id);
            }
        }
        if (!missing.isEmpty()) {
            throw new ProductsNotFoundException(missing);
        }
        return resolved;
    }
}
