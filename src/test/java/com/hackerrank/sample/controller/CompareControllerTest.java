package com.hackerrank.sample.controller;

import com.hackerrank.sample.controller.advice.GlobalExceptionHandler;
import com.hackerrank.sample.exception.ProductNotFoundException;
import com.hackerrank.sample.model.BuyBox;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.Condition;
import com.hackerrank.sample.model.ProductDetail;
import com.hackerrank.sample.service.ProductService;
import com.hackerrank.sample.service.ai.SummaryService;
import com.hackerrank.sample.service.compare.CompareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompareController.class)
@Import({CompareService.class, GlobalExceptionHandler.class})
class CompareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private SummaryService summaryService;

    @BeforeEach
    void summaryReturnsEmptyByDefault() {
        when(summaryService.summarise(anyList(), anyList(), any())).thenReturn(Optional.empty());
    }

    private static ProductDetail product(long id, BigDecimal price, String battery) {
        return new ProductDetail(id, "P" + id, "d", "img", 4.0, Category.SMARTPHONE,
                Map.of("battery", battery, "brand", "Samsung"),
                List.of(),
                new BuyBox(id, "S", "Seller", 4, price, "BRL", Condition.NEW, true, 5));
    }

    @Test
    void happyPath_returns200WithItemsAndDifferences() throws Exception {
        when(productService.getById(eq(1L))).thenReturn(product(1L, new BigDecimal("4000"), "4000 mAh"));
        when(productService.getById(eq(2L))).thenReturn(product(2L, new BigDecimal("5000"), "3000 mAh"));

        mockMvc.perform(get("/api/v1/products/compare?ids=1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("pt-BR"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[1].id").value(2))
                .andExpect(jsonPath("$.differences[?(@.path=='buyBox.price')].winnerId").value(1));
    }

    @Test
    void singleId_returns400Validation() throws Exception {
        mockMvc.perform(get("/api/v1/products/compare?ids=1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.example.com/errors/validation"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unknownId_returns404ProductsNotFound() throws Exception {
        when(productService.getById(eq(1L))).thenReturn(product(1L, new BigDecimal("4000"), "4000 mAh"));
        when(productService.getById(eq(99L))).thenThrow(new ProductNotFoundException(99L));

        mockMvc.perform(get("/api/v1/products/compare?ids=1,99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.example.com/errors/products-not-found"))
                .andExpect(jsonPath("$.missingIds[0]").value(99));
    }

    @Test
    void sparseFields_returnsProjection() throws Exception {
        when(productService.getById(eq(1L))).thenReturn(product(1L, new BigDecimal("4000"), "4000 mAh"));
        when(productService.getById(eq(2L))).thenReturn(product(2L, new BigDecimal("5000"), "3000 mAh"));

        mockMvc.perform(get("/api/v1/products/compare?ids=1,2&fields=name,buyBox.price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0]").value("name"))
                .andExpect(jsonPath("$.fields[1]").value("buyBox.price"))
                .andExpect(jsonPath("$.items[0].name").value("P1"))
                .andExpect(jsonPath("$.items[0].buyBox.price").value(4000))
                .andExpect(jsonPath("$.items[0].category").doesNotExist())
                .andExpect(jsonPath("$.items[0].attributes").doesNotExist());
    }

    @Test
    void invalidLanguage_returns400() throws Exception {
        when(productService.getById(eq(1L))).thenReturn(product(1L, new BigDecimal("4000"), "4000 mAh"));
        when(productService.getById(eq(2L))).thenReturn(product(2L, new BigDecimal("5000"), "3000 mAh"));

        mockMvc.perform(get("/api/v1/products/compare?ids=1,2&language=fr-FR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.example.com/errors/bad-request"));
    }

    @Test
    void crossCategory_exposesFlagAndExclusiveAttributes() throws Exception {
        ProductDetail phone = new ProductDetail(1L, "Phone", "d", "img", 4.0, Category.SMARTPHONE,
                Map.of("battery", "4000 mAh", "memory", "8 GB", "brand", "Samsung"),
                List.of(),
                new BuyBox(1L, "S", "Seller", 4, new BigDecimal("4000"), "BRL", Condition.NEW, true, 5));
        ProductDetail laptop = new ProductDetail(21L, "Laptop", "d", "img", 4.5, Category.NOTEBOOK,
                Map.of("memory", "16 GB", "brand", "Lenovo", "cpu", "i7"),
                List.of(),
                new BuyBox(21L, "S2", "Seller2", 4, new BigDecimal("12000"), "BRL", Condition.NEW, true, 3));
        when(productService.getById(eq(1L))).thenReturn(phone);
        when(productService.getById(eq(21L))).thenReturn(laptop);

        mockMvc.perform(get("/api/v1/products/compare?ids=1,21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crossCategory").value(true))
                .andExpect(jsonPath("$.exclusiveAttributes['1'][0]").value("battery"))
                .andExpect(jsonPath("$.exclusiveAttributes['21'][0]").value("cpu"));
    }

    @Test
    void invalidFields_returns400() throws Exception {
        when(productService.getById(eq(1L))).thenReturn(product(1L, new BigDecimal("4000"), "4000 mAh"));
        when(productService.getById(eq(2L))).thenReturn(product(2L, new BigDecimal("5000"), "3000 mAh"));

        mockMvc.perform(get("/api/v1/products/compare?ids=1,2&fields=bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.example.com/errors/bad-request"));
    }
}
