package com.hackerrank.sample.model.insights;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * Mutable request bean used by Spring data binding to capture the
 * three opt-in {@code /category-insights} filters from the query string
 * (SPEC-005 v5 §5.6).
 *
 * <p>Field-level constraints reject negative bounds and ratings outside
 * {@code [0, 5]}; the class-level {@link FilterBoundsConsistent}
 * constraint enforces {@code minPrice <= maxPrice} when both are
 * supplied.</p>
 */
@FilterBoundsConsistent
public class InsightsFiltersRequest {

    @Schema(description = "Lower bound (inclusive) on `buyBox.price`. Excludes products without a buy box.", example = "1500")
    @DecimalMin(value = "0.0", message = "must be greater than or equal to 0")
    private BigDecimal minPrice;

    @Schema(description = "Upper bound (inclusive) on `buyBox.price`. Excludes products without a buy box.", example = "4000")
    @DecimalMin(value = "0.0", message = "must be greater than or equal to 0")
    private BigDecimal maxPrice;

    @Schema(description = "Minimum `rating` (inclusive). Excludes products with no rating.", example = "4.5")
    @DecimalMin(value = "0.0", message = "must be greater than or equal to 0")
    @DecimalMax(value = "5.0", message = "must be less than or equal to 5")
    private Double minRating;

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public Double getMinRating() {
        return minRating;
    }

    public void setMinRating(Double minRating) {
        this.minRating = minRating;
    }

    public boolean isEmpty() {
        return minPrice == null && maxPrice == null && minRating == null;
    }
}
