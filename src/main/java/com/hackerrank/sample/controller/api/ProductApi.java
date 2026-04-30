package com.hackerrank.sample.controller.api;

import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.PageResponse;
import com.hackerrank.sample.model.ProductDetail;
import com.hackerrank.sample.model.ProductSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Products")
public interface ProductApi {

    int MAX_PAGE_SIZE = 100;

    @GetMapping
    @Operation(
            summary = "List catalog products",
            description = "Returns a page of product summaries. Optional category filter narrows the results."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of product summaries"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid query parameter (e.g. unknown category, page < 0, size > 100)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "invalidCategory", value = ProductApiExamples.INVALID_CATEGORY)))
    })
    PageResponse<ProductSummary> list(
            @Parameter(description = "Filter by category. Omit for all categories.", example = "SMARTPHONE")
            @RequestParam(required = false) Category category,
            @Parameter(description = "Zero-based page index.", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1..100).", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size);

    @GetMapping("/{id}")
    @Operation(
            summary = "Get product detail",
            description = """
                    Returns the full `ProductDetail` for the given id, or a sparse `CompareItem`
                    projection when `fields` is provided. Field paths support dot notation
                    (`buyBox.price`) and `attributes.<key>` for individual attribute keys.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Full product detail (or sparse projection if `fields` is provided)",
                    content = @Content(schema = @Schema(implementation = ProductDetail.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid `fields` parameter",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "invalidFields", value = ProductApiExamples.INVALID_FIELDS))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product id does not exist",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "notFound", value = ProductApiExamples.NOT_FOUND)))
    })
    Object getById(
            @Parameter(description = "Product id (positive long).", example = "1")
            @PathVariable long id,
            @Parameter(
                    description = "Comma-separated field paths to project. Supports `attributes.<key>` and `buyBox.<sub>`.",
                    example = "name,buyBox.price,attributes.battery"
            )
            @RequestParam(name = "fields", required = false) String fieldsCsv);
}
