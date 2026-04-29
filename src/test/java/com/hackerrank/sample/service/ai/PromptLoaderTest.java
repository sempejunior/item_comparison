package com.hackerrank.sample.service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLoaderTest {

    private static final String TEMPLATE = "compare-summary.v1.md";

    private static final String PRODUCTS_JSON = """
            [{"id":1,"name":"Phone A","category":"SMARTPHONE","buyBox":{"price":1999.00,"currency":"BRL"},"attributes":{"battery_mah":4500,"weight_g":190}},
            {"id":2,"name":"Phone B","category":"SMARTPHONE","buyBox":{"price":2199.00,"currency":"BRL"},"attributes":{"battery_mah":5151,"weight_g":194}}]""";

    private static final String DIFFERENCES_JSON = """
            [{"attribute":"battery_mah","values":{"1":4500,"2":5151},"winner":2,"delta":651},
            {"attribute":"weight_g","values":{"1":190,"2":194},"winner":1,"delta":4}]""";

    private final PromptLoader loader = new PromptLoader();

    @Test
    void loadsTemplateOnce() {
        String first = loader.load(TEMPLATE);
        String second = loader.load(TEMPLATE);
        assertThat(first).isSameAs(second);
        assertThat(first).contains("{{language}}", "{{products}}", "{{differences}}");
    }

    @Test
    void rendersAgainstGolden() throws IOException {
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put("language", "pt-BR");
        bindings.put("products", PRODUCTS_JSON);
        bindings.put("differences", DIFFERENCES_JSON);

        String rendered = loader.render(TEMPLATE, bindings);
        String golden = readGolden();

        assertThat(rendered).isEqualTo(golden);
    }

    @Test
    void rejectsMissingBinding() {
        Map<String, String> bindings = Map.of(
                "language", "pt-BR",
                "products", "[]"
        );

        assertThatThrownBy(() -> loader.render(TEMPLATE, bindings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("differences");
    }

    @Test
    void unknownTemplateRaises() {
        assertThatThrownBy(() -> loader.load("does-not-exist.md"))
                .hasMessageContaining("does-not-exist.md");
    }

    private static String readGolden() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompts/golden/compare-summary.v1.txt");
        try (var stream = resource.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
