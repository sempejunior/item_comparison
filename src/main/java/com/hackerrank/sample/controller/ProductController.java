package com.hackerrank.sample.controller;

import com.hackerrank.sample.controller.api.ProductApi;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.CompareItem;
import com.hackerrank.sample.model.PageResponse;
import com.hackerrank.sample.model.ProductDetail;
import com.hackerrank.sample.model.ProductSummary;
import com.hackerrank.sample.service.ProductService;
import com.hackerrank.sample.service.compare.FieldSet;
import com.hackerrank.sample.service.compare.FieldSetProjector;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/products")
public class ProductController implements ProductApi {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public PageResponse<ProductSummary> list(Category category, int page, int size) {
        return productService.list(category, PageRequest.of(page, size));
    }

    @Override
    public Object getById(long id, String fieldsCsv) {
        ProductDetail detail = productService.getById(id);
        if (fieldsCsv == null || fieldsCsv.isBlank()) {
            return detail;
        }
        FieldSet fields = FieldSetProjector.parse(fieldsCsv);
        CompareItem projected = FieldSetProjector.project(detail, fields);
        return projected;
    }
}
