package com.hackerrank.sample.controller;

import com.hackerrank.sample.controller.api.CompareApi;
import com.hackerrank.sample.model.CompareResponse;
import com.hackerrank.sample.model.Language;
import com.hackerrank.sample.service.compare.CompareService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class CompareController implements CompareApi {

    private final CompareService compareService;

    public CompareController(CompareService compareService) {
        this.compareService = compareService;
    }

    @Override
    public CompareResponse compare(List<Long> ids, String fields, String language) {
        Language parsedLanguage = Language.fromTag(language);
        return compareService.compare(ids, fields, parsedLanguage);
    }
}
