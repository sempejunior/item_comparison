package com.hackerrank.sample.controller.api;

import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.insights.CategoryInsightsResponse;
import com.hackerrank.sample.model.insights.InsightsFiltersRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Category Insights")
public interface CategoryInsightsApi {

    @GetMapping("/category-insights")
    @Operation(
            summary = "Per-category landscape insights",
            description = """
                    For a given category, returns deterministic per-attribute rankings
                    (winner / runner-up / spread + coverage) plus a top-K representative
                    sample picked by rating then price. An optional natural-language
                    `summary` is produced by the LLM when available; the endpoint always
                    returns 200 even if the LLM falls back.

                    Categories with fewer than 2 products yield an empty `rankings[]` and
                    no `summary`.

                    Optional structured filters (`minPrice`, `maxPrice`, `minRating`)
                    narrow the analyzed slice. When any filter is supplied, the response
                    echoes the bounds via `appliedFilters` and `productCount` reflects the
                    filtered subset (SPEC-005 v5 §5.6).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category insights (with optional `summary`)"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid `category`, `topK` (1..20), or `language`",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = {
                                    @ExampleObject(name = "invalidCategory",     value = CategoryInsightsApiExamples.INVALID_CATEGORY),
                                    @ExampleObject(name = "topKOutOfRange",      value = CategoryInsightsApiExamples.TOPK_OUT_OF_RANGE),
                                    @ExampleObject(name = "invalidLanguage",     value = CategoryInsightsApiExamples.INVALID_LANGUAGE),
                                    @ExampleObject(name = "minPriceNegative",    value = CategoryInsightsApiExamples.MIN_PRICE_NEGATIVE),
                                    @ExampleObject(name = "boundsInconsistent",  value = CategoryInsightsApiExamples.BOUNDS_INCONSISTENT),
                                    @ExampleObject(name = "minRatingOutOfRange", value = CategoryInsightsApiExamples.MIN_RATING_OUT_OF_RANGE)
                            }))
    })
    CategoryInsightsResponse categoryInsights(
            @Parameter(description = "Category whose landscape will be summarised.", example = "SMARTPHONE", required = true)
            @RequestParam("category") Category category,
            @Parameter(description = "Number of representative items returned in `topItems[]` (1..20).", example = "5")
            @RequestParam(value = "topK", required = false, defaultValue = "5") @Min(1) @Max(20) int topK,
            @Parameter(description = "BCP-47 language tag for the LLM summary. Supported: `pt-BR`, `en`.", example = "pt-BR")
            @RequestParam(value = "language", required = false, defaultValue = "pt-BR") String language,
            @ParameterObject @Valid InsightsFiltersRequest filters);
}
