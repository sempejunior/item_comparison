package com.hackerrank.sample.controller;

import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.CompareItem;
import com.hackerrank.sample.model.PageResponse;
import com.hackerrank.sample.model.ProductDetail;
import com.hackerrank.sample.model.ProductSummary;
import com.hackerrank.sample.service.ProductService;
import com.hackerrank.sample.service.compare.FieldSet;
import com.hackerrank.sample.service.compare.FieldSetProjector;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public PageResponse<ProductSummary> list(
            @RequestParam(required = false) Category category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_SIZE) int size) {
        return productService.list(category, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public Object getById(
            @PathVariable long id,
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
