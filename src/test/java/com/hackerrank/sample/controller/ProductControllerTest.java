package com.hackerrank.sample.controller;

import com.hackerrank.sample.exception.ProductNotFoundException;
import com.hackerrank.sample.model.BuyBox;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.Condition;
import com.hackerrank.sample.model.PageResponse;
import com.hackerrank.sample.model.ProductDetail;
import com.hackerrank.sample.model.ProductSummary;
import com.hackerrank.sample.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Test
    void list_returns200WithItems() throws Exception {
        ProductSummary item = new ProductSummary(1L, "Galaxy S24", "img.jpg", 4.6, Category.SMARTPHONE);
        PageResponse<ProductSummary> page = new PageResponse<>(List.of(item), 0, 20, 1L, 1);
        when(productService.list(eq(null), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Galaxy S24"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_filtersByCategory() throws Exception {
        PageResponse<ProductSummary> empty = new PageResponse<>(List.of(), 0, 20, 0L, 0);
        when(productService.list(eq(Category.SMART_TV), any(Pageable.class))).thenReturn(empty);

        mockMvc.perform(get("/api/v1/products").param("category", "SMART_TV"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getById_returns200WithDetail() throws Exception {
        BuyBox bb = new BuyBox(101L, "MELI-A1", "TechMarket", 5,
                new BigDecimal("4899.00"), "BRL", Condition.NEW, true, 12);
        ProductDetail detail = new ProductDetail(
                1L, "Galaxy S24", "Flagship", "img.jpg", 4.6,
                Category.SMARTPHONE, Map.of("memory", "8 GB"),
                List.of(), bb);
        when(productService.getById(1L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.buyBox.price").value(4899.00))
                .andExpect(jsonPath("$.buyBox.currency").value("BRL"));
    }

    @Test
    void getById_returns404RFC7807WhenMissing() throws Exception {
        when(productService.getById(99L)).thenThrow(new ProductNotFoundException(99L));

        mockMvc.perform(get("/api/v1/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.example.com/errors/not-found"))
                .andExpect(jsonPath("$.title").value("Product not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("product not found: 99"))
                .andExpect(jsonPath("$.instance").value("/api/v1/products/99"));
    }
}
