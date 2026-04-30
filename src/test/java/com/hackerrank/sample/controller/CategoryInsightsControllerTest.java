package com.hackerrank.sample.controller;

import com.hackerrank.sample.controller.advice.GlobalExceptionHandler;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.Language;
import com.hackerrank.sample.model.insights.CategoryInsightsResponse;
import com.hackerrank.sample.service.insights.CategoryInsightsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryInsightsController.class)
@Import(GlobalExceptionHandler.class)
class CategoryInsightsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryInsightsService insightsService;

    @Test
    void happyPath_returns200WithRankings() throws Exception {
        CategoryInsightsResponse response = new CategoryInsightsResponse(
                Category.SMARTPHONE, 3, List.of(), List.of(), "pt-BR", null);
        when(insightsService.insights(eq(Category.SMARTPHONE), anyInt(), any(Language.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/products/category-insights?category=SMARTPHONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("SMARTPHONE"))
                .andExpect(jsonPath("$.productCount").value(3))
                .andExpect(jsonPath("$.language").value("pt-BR"));
    }

    @Test
    void unknownCategory_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/category-insights?category=BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.example.com/errors/bad-request"));
    }

    @Test
    void topKAboveMax_returns400Validation() throws Exception {
        mockMvc.perform(get("/api/v1/products/category-insights?category=SMARTPHONE&topK=21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.example.com/errors/validation"));
    }

    @Test
    void topKBelowMin_returns400Validation() throws Exception {
        mockMvc.perform(get("/api/v1/products/category-insights?category=SMARTPHONE&topK=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void invalidLanguage_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/category-insights?category=SMARTPHONE&language=fr"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid language"));
    }

    @Test
    void missingCategory_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/category-insights"))
                .andExpect(status().isBadRequest());
    }
}
