package com.hackerrank.sample.controller.api;

import com.hackerrank.sample.model.CompareResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Compare")
public interface CompareApi {

    @GetMapping("/compare")
    @Operation(
            summary = "Compare 2 to 10 products side by side",
            description = """
                    Returns the requested items, the deterministic `differences[]` (one entry per
                    attribute path that varies across the items), and an optional natural-language
                    `summary` produced by the LLM.

                    The summary is best-effort: if the model is unavailable, slow, or unauthenticated,
                    the endpoint still returns 200 with `summary` omitted. Fallback reasons are
                    observable via Micrometer (`ai_fallback_total{reason}`).

                    For cross-category requests (e.g. a phone vs a laptop), `differences[]` operates
                    on the intersection of attribute keys; the response carries `crossCategory: true`
                    and `exclusiveAttributes` mapping each id to its non-shared keys.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comparison response (with optional `summary`)"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid `ids` (blank/null, duplicates, non-positive, < 2 or > 10), invalid `fields`, or unsupported `language`",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = {
                                    @ExampleObject(name = "duplicateIds",   value = CompareApiExamples.DUPLICATE_IDS),
                                    @ExampleObject(name = "nonPositiveId",  value = CompareApiExamples.NON_POSITIVE_ID),
                                    @ExampleObject(name = "trailingComma",  value = CompareApiExamples.TRAILING_COMMA)
                            })),
            @ApiResponse(
                    responseCode = "404",
                    description = "One or more product ids do not exist",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "missingIds", value = CompareApiExamples.MISSING_IDS)))
    })
    CompareResponse compare(
            @Parameter(
                    description = "Comma-separated product ids (2..10 unique positive longs).",
                    example = "1,2,3",
                    required = true
            )
            @RequestParam("ids") List<Long> ids,
            @Parameter(
                    description = "Comma-separated field paths to project per item (sparse fields). Supports `attributes.<key>` and `buyBox.<sub>`.",
                    example = "name,buyBox.price,attributes.battery"
            )
            @RequestParam(value = "fields", required = false) String fields,
            @Parameter(
                    description = "BCP-47 language tag for the LLM summary. Supported: `pt-BR`, `en`, `es`.",
                    example = "pt-BR"
            )
            @RequestParam(value = "language", required = false, defaultValue = "pt-BR") String language);
}
