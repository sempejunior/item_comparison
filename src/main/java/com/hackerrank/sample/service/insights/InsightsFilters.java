package com.hackerrank.sample.service.insights;

import com.hackerrank.sample.model.Language;
import com.hackerrank.sample.model.ProductDetail;
import com.hackerrank.sample.model.insights.InsightsFiltersRequest;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Internal, immutable view of the structured filters supplied by the
 * client to {@code /category-insights} (SPEC-005 v5 §5.6).
 *
 * <p>Created via {@link #from(InsightsFiltersRequest)} which returns
 * {@code null} when the request is itself null or carries no filter, so
 * the v4 hot path stays allocation-free.</p>
 */
public record InsightsFilters(BigDecimal minPrice, BigDecimal maxPrice, Double minRating) {

    public static InsightsFilters from(InsightsFiltersRequest request) {
        if (request == null || request.isEmpty()) {
            return null;
        }
        return new InsightsFilters(request.getMinPrice(), request.getMaxPrice(), request.getMinRating());
    }

    boolean hasPriceBound() {
        return minPrice != null || maxPrice != null;
    }

    Predicate<ProductDetail> asPredicate() {
        return product -> {
            if (hasPriceBound()) {
                if (product.buyBox() == null || product.buyBox().price() == null) {
                    return false;
                }
                BigDecimal price = product.buyBox().price();
                if (minPrice != null && price.compareTo(minPrice) < 0) {
                    return false;
                }
                if (maxPrice != null && price.compareTo(maxPrice) > 0) {
                    return false;
                }
            }
            if (minRating != null) {
                if (product.rating() == null || product.rating() < minRating) {
                    return false;
                }
            }
            return true;
        };
    }

    public String digest() {
        StringBuilder sb = new StringBuilder();
        sb.append(minPrice == null ? "" : minPrice.stripTrailingZeros().toPlainString());
        sb.append('|');
        sb.append(maxPrice == null ? "" : maxPrice.stripTrailingZeros().toPlainString());
        sb.append('|');
        sb.append(minRating == null ? "" : minRating);
        return sb.toString();
    }

    public String describe(Language language) {
        boolean ptBr = language == Language.PT_BR;
        List<String> parts = new ArrayList<>(3);
        if (minPrice != null) {
            parts.add(ptBr
                    ? "preco minimo " + formatPrice(minPrice, language)
                    : "minimum price " + formatPrice(minPrice, language));
        }
        if (maxPrice != null) {
            parts.add(ptBr
                    ? "preco maximo " + formatPrice(maxPrice, language)
                    : "maximum price " + formatPrice(maxPrice, language));
        }
        if (minRating != null) {
            parts.add(ptBr
                    ? "nota minima " + formatRating(minRating, language)
                    : "minimum rating " + formatRating(minRating, language));
        }
        return String.join(ptBr ? "; " : "; ", parts);
    }

    private static String formatPrice(BigDecimal value, Language language) {
        if (language == Language.PT_BR) {
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("pt", "BR"));
            DecimalFormat fmt = new DecimalFormat("#,##0.##", symbols);
            return "R$ " + fmt.format(value);
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ENGLISH);
        DecimalFormat fmt = new DecimalFormat("#,##0.##", symbols);
        return "BRL " + fmt.format(value);
    }

    private static String formatRating(Double value, Language language) {
        if (language == Language.PT_BR) {
            return String.format(new Locale("pt", "BR"), "%.1f", value);
        }
        return String.format(Locale.ENGLISH, "%.1f", value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InsightsFilters other)) return false;
        return Objects.equals(digest(), other.digest());
    }

    @Override
    public int hashCode() {
        return digest().hashCode();
    }
}
