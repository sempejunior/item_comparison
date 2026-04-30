package com.hackerrank.sample.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI itemComparisonOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Item Comparison API V2")
                        .version("1.0.0")
                        .description("""
                                REST API for comparing up to ten Mercado Livre catalog products side by side.

                                Returns deterministic field-by-field differences plus an optional
                                LLM-generated natural-language summary. The LLM call is best-effort:
                                if the model is unavailable, slow, or unauthenticated, the endpoint
                                still returns 200 with the deterministic payload and `summary` omitted.

                                Errors follow RFC 7807 Problem Details with stable `type` slugs.
                                """)
                        .contact(new Contact()
                                .name("Carlos Sempé")
                                .url("https://github.com/sempejunior/item_comparison"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .externalDocs(new ExternalDocumentation()
                        .description("Specs (SDD)")
                        .url("https://github.com/sempejunior/item_comparison/tree/main/docs/specs"))
                .servers(List.of(new Server().url("/").description("Default server")))
                .tags(List.of(
                        new Tag().name("Products").description("List and retrieve catalog products"),
                        new Tag().name("Compare").description("Side-by-side comparison with optional AI summary")));
    }
}
