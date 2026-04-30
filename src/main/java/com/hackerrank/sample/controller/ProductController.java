package com.hackerrank.sample.controller;

import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.CompareItem;
import com.hackerrank.sample.model.PageResponse;
import com.hackerrank.sample.model.ProductDetail;
import com.hackerrank.sample.model.ProductSummary;
import com.hackerrank.sample.service.ProductService;
import com.hackerrank.sample.service.compare.FieldSet;
import com.hackerrank.sample.service.compare.FieldSetProjector;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/products")
@Tag(name = "Products")
public class ProductController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

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
                            examples = @ExampleObject(
                                    name = "invalidCategory",
                                    value = """
                                            {
                                              "type": "https://api.example.com/errors/bad-request",
                                              "title": "Malformed parameter",
                                              "status": 400,
                                              "detail": "parameter 'category' has invalid value: BOGUS",
                                              "instance": "/api/v1/products"
                                            }"""
                            )))
    })
    public PageResponse<ProductSummary> list(
            @Parameter(description = "Filter by category. Omit for all categories.", example = "SMARTPHONE")
            @RequestParam(required = false) Category category,
            @Parameter(description = "Zero-based page index.", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1..100).", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_SIZE) int size) {
        return productService.list(category, PageRequest.of(page, size));
    }

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
                            examples = @ExampleObject(
                                    name = "invalidFields",
                                    value = """
                                            {
                                              "type": "https://api.example.com/errors/bad-request",
                                              "title": "Invalid fields parameter",
                                              "status": 400,
                                              "detail": "path does not accept sub-paths: name.bogus",
                                              "instance": "/api/v1/products/1"
                                            }"""
                            ))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product id does not exist",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(
                                    name = "notFound",
                                    value = """
                                            {
                                              "type": "https://api.example.com/errors/not-found",
                                              "title": "Product not found",
                                              "status": 404,
                                              "detail": "product not found: 9999",
                                              "instance": "/api/v1/products/9999"
                                            }"""
                            )))
    })
    public Object getById(
            @Parameter(description = "Product id (positive long).", example = "1")
            @PathVariable long id,
            @Parameter(
                    description = "Comma-separated field paths to project. Supports `attributes.<key>` and `buyBox.<sub>`.",
                    example = "name,buyBox.price,attributes.battery"
            )
            @RequestParam(name = "fields", required = false) String fieldsCsv) {
        ProductDetail detail = productService.getById(id);
        if (fieldsCsv == null || fieldsCsv.isBlank()) {
            return detail;
        }
        FieldSet fields = FieldSetProjector.parse(fieldsCsv);
        CompareItem projected = FieldSetProjector.project(detail, fields);
        return projected;
    }
}
