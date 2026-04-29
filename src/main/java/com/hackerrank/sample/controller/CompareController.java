package com.hackerrank.sample.controller;

import com.hackerrank.sample.model.CompareResponse;
import com.hackerrank.sample.model.Language;
import com.hackerrank.sample.service.compare.CompareService;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/products")
public class CompareController {

    private final CompareService compareService;

    public CompareController(CompareService compareService) {
        this.compareService = compareService;
    }

    @GetMapping("/compare")
    public CompareResponse compare(
            @RequestParam("ids") @NotEmpty List<Long> ids,
            @RequestParam(value = "fields", required = false) String fields,
            @RequestParam(value = "language", required = false, defaultValue = "pt-BR") String language) {
        Language parsedLanguage = Language.fromTag(language);
        return compareService.compare(ids, fields, parsedLanguage);
    }
}
