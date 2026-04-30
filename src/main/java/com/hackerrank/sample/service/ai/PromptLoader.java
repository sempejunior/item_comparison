package com.hackerrank.sample.service.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads versioned prompt templates from {@code classpath:prompts/} and
 * renders them with {@code {{placeholder}}} substitution.
 *
 * <p>Spring AI's native {@link org.springframework.ai.chat.prompt.PromptTemplate}
 * uses single-brace StringTemplate syntax, which conflicts with our payloads
 * (JSON-rendered product lists and difference entries containing literal
 * braces). A small custom renderer keeps placeholder semantics unambiguous
 * and avoids accidental interpolation of JSON content.</p>
 *
 * <p>Templates are read once on first access and cached for the lifetime of
 * the application context.</p>
 */
@Component
public class PromptLoader {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*}}");
    private static final String PROMPTS_ROOT = "prompts/";

    private final Map<String, String> templates = new ConcurrentHashMap<>();

    public String load(String name) {
        return templates.computeIfAbsent(name, this::readResource);
    }

    public String render(String name, Map<String, String> bindings) {
        String template = load(name);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder(template.length() + 256);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!bindings.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Missing binding for placeholder '" + key + "' in prompt '" + name + "'");
            }
            String value = bindings.get(key);
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String readResource(String name) {
        ClassPathResource resource = new ClassPathResource(PROMPTS_ROOT + name);
        try (var stream = resource.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to load prompt template: " + name, ex);
        }
    }
}
