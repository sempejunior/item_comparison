package com.hackerrank.sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * HackerRank-compatible dynamic test runner.
 *
 * <p>Loads each JSON file under {@code src/test/resources/testcases/}, replays
 * the declared HTTP exchange against the application via {@link MockMvc}, and
 * writes the platform-expected reports under {@code target/customReports/}
 * ({@code result.txt} for display, {@code result.xml} for grading).
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
class HttpJsonDynamicUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path TESTCASES_DIR = Paths.get("src/test/resources/testcases");
    private static final Path REPORT_DIR = Paths.get("target/customReports");
    private static final String DASHES =
            "------------------------------------------------------------------------";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dynamicTests() throws Exception {
        Files.createDirectories(REPORT_DIR);

        List<String> testFiles = listTestcaseFiles();
        Map<String, String> namesByFile = loadDescriptions();

        if (!testFiles.isEmpty()) {
            Assertions.assertEquals(testFiles.size(), namesByFile.size(),
                    "description.txt must list one entry per testcase JSON file");
        }

        Map<String, TestFailure> failuresByFile = new LinkedHashMap<>();
        Map<String, Long> elapsedByFile = new LinkedHashMap<>();
        long suiteStart = System.currentTimeMillis();

        for (String filename : testFiles) {
            long caseStart = System.currentTimeMillis();
            try {
                runCase(filename, failuresByFile);
            } finally {
                elapsedByFile.put(filename, System.currentTimeMillis() - caseStart);
            }
        }

        long suiteElapsedMillis = System.currentTimeMillis() - suiteStart;

        writeReports(testFiles, namesByFile, elapsedByFile, failuresByFile, suiteElapsedMillis);

        if (!failuresByFile.isEmpty()) {
            String summary = failuresByFile.entrySet().stream()
                    .map(entry -> entry.getKey() + " — " + entry.getValue().reason
                            + " (expected=" + entry.getValue().expected
                            + ", found=" + entry.getValue().found + ")")
                    .collect(Collectors.joining("; "));
            Assertions.fail(summary);
        }
    }

    private List<String> listTestcaseFiles() throws IOException {
        if (!Files.isDirectory(TESTCASES_DIR)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(TESTCASES_DIR)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    private Map<String, String> loadDescriptions() throws IOException {
        Map<String, String> names = new LinkedHashMap<>();
        Path descriptions = TESTCASES_DIR.resolve("description.txt");
        if (!Files.exists(descriptions)) {
            return names;
        }
        for (String line : Files.readAllLines(descriptions, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String filename = line.substring(0, colon).trim();
            String testName = line.substring(colon + 1).trim();
            names.put(filename, testName);
        }
        return names;
    }

    private void runCase(final String filename,
                         final Map<String, TestFailure> failures) throws Exception {
        Path file = TESTCASES_DIR.resolve(filename);
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            JsonNode root = MAPPER.readTree(line);
            JsonNode request = root.get("request");
            JsonNode response = root.get("response");

            String method = request.get("method").asText();
            String url = request.get("url").asText();
            String testStep = method + " " + url;

            MockHttpServletResponse mockResponse = perform(method, url,
                    request.get("body"), request.get("headers"));

            int expectedStatus = response.get("status_code").asInt();
            if (mockResponse.getStatus() != expectedStatus) {
                failures.put(filename, new TestFailure(testStep, "Status code",
                        String.valueOf(expectedStatus),
                        String.valueOf(mockResponse.getStatus())));
                return;
            }

            JsonNode expectedHeaders = response.get("headers");
            JsonNode expectedContentType = expectedHeaders == null
                    ? null
                    : expectedHeaders.get("Content-Type");
            if (expectedContentType != null) {
                String actualContentType = mockResponse.getContentType();
                if (actualContentType == null
                        || !actualContentType.startsWith(expectedContentType.asText())) {
                    failures.put(filename, new TestFailure(testStep, "Content type",
                            expectedContentType.asText(),
                            String.valueOf(actualContentType)));
                    return;
                }
            }

            JsonNode expectedBody = response.get("body");
            if (expectedBody != null && !expectedBody.isMissingNode() && !isEmptyJson(expectedBody)) {
                String body = mockResponse.getContentAsString();
                JsonNode actualBody = body.isEmpty() ? MAPPER.nullNode() : MAPPER.readTree(body);
                if (!matchesPartial(expectedBody, actualBody)) {
                    failures.put(filename, new TestFailure(testStep, "Response body",
                            expectedBody.toString(), actualBody.toString()));
                    return;
                }
            }
        }
    }

    private MockHttpServletResponse perform(final String method,
                                            final String url,
                                            final JsonNode body,
                                            final JsonNode headers) throws Exception {
        MockHttpServletRequestBuilder builder = switch (method) {
            case "GET" -> get(url);
            case "POST" -> post(url);
            case "PUT" -> put(url);
            case "DELETE" -> delete(url);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };

        boolean methodSendsBody = "POST".equals(method) || "PUT".equals(method);
        if (methodSendsBody && body != null && !body.isMissingNode() && !isEmptyJson(body)) {
            builder.content(body.toString());
        }

        String contentType = "application/json";
        if (headers != null && headers.has("Content-Type")) {
            contentType = headers.get("Content-Type").asText();
        }
        builder.contentType(contentType);

        return mockMvc.perform(builder).andReturn().getResponse();
    }

    private static boolean isEmptyJson(final JsonNode node) {
        if (node.isObject() || node.isArray()) {
            return node.isEmpty();
        }
        return false;
    }

    private static boolean matchesPartial(final JsonNode expected, final JsonNode actual) {
        if (expected.isObject()) {
            if (!actual.isObject()) {
                return false;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode actualValue = actual.get(entry.getKey());
                if (actualValue == null) {
                    return false;
                }
                if (!matchesPartial(entry.getValue(), actualValue)) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isArray()) {
            if (!actual.isArray() || expected.size() != actual.size()) {
                return false;
            }
            for (int i = 0; i < expected.size(); i++) {
                if (!matchesPartial(expected.get(i), actual.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return expected.equals(actual);
    }

    private void writeReports(final List<String> testFiles,
                              final Map<String, String> namesByFile,
                              final Map<String, Long> elapsedByFile,
                              final Map<String, TestFailure> failuresByFile,
                              final long suiteElapsedMillis) throws IOException {
        long success = testFiles.size() - failuresByFile.size();
        writeTextReport(testFiles, namesByFile, elapsedByFile, failuresByFile,
                suiteElapsedMillis, success);
        writeXmlReport(testFiles, namesByFile, elapsedByFile, failuresByFile,
                suiteElapsedMillis);
    }

    private void writeTextReport(final List<String> testFiles,
                                 final Map<String, String> namesByFile,
                                 final Map<String, Long> elapsedByFile,
                                 final Map<String, TestFailure> failuresByFile,
                                 final long suiteElapsedMillis,
                                 final long success) throws IOException {
        Path target = REPORT_DIR.resolve("result.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write(DASHES);
            writer.newLine();
            writer.write("TEST SUMMARY");
            writer.newLine();
            writer.write(DASHES);
            writer.newLine();
            writer.write(String.format(Locale.ROOT,
                    "Tests: %d, Success: %d, Failure: %d, Total time: %.2fs",
                    testFiles.size(), success, failuresByFile.size(),
                    suiteElapsedMillis / 1000.0));
            writer.newLine();
            writer.newLine();

            writer.write(DASHES);
            writer.newLine();
            writer.write("TEST RESULT");
            writer.newLine();
            writer.write(DASHES);
            writer.newLine();
            for (String filename : testFiles) {
                String state = failuresByFile.containsKey(filename) ? "Failure" : "Success";
                long elapsed = elapsedByFile.getOrDefault(filename, 0L);
                String description = namesByFile.getOrDefault(filename, filename);
                writer.write(String.format(Locale.ROOT,
                        "%s (%s): %s (%.2fs)",
                        filename, description, state, elapsed / 1000.0));
                writer.newLine();
            }

            if (!failuresByFile.isEmpty()) {
                writer.newLine();
                for (Map.Entry<String, TestFailure> entry : failuresByFile.entrySet()) {
                    writer.write(DASHES);
                    writer.newLine();
                    writer.write("FAILURE REPORT " + entry.getKey());
                    writer.newLine();
                    writer.write(DASHES);
                    writer.newLine();
                    TestFailure failure = entry.getValue();
                    writer.write("[Test Case] " + failure.testStep);
                    writer.newLine();
                    writer.write("[   Reason] " + failure.reason);
                    writer.newLine();
                    writer.write("[ Expected] " + failure.expected);
                    writer.newLine();
                    writer.write("[    Found] " + failure.found);
                    writer.newLine();
                    writer.newLine();
                }
            }
        }
    }

    private void writeXmlReport(final List<String> testFiles,
                                final Map<String, String> namesByFile,
                                final Map<String, Long> elapsedByFile,
                                final Map<String, TestFailure> failuresByFile,
                                final long suiteElapsedMillis) throws IOException {
        Path target = REPORT_DIR.resolve("result.xml");
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.newLine();
            writer.write(String.format(Locale.ROOT,
                    "<testsuite name=\"%s\" time=\"%.3f\" tests=\"%d\" errors=\"0\""
                            + " skipped=\"0\" failures=\"%d\">",
                    HttpJsonDynamicUnitTest.class.getName(),
                    suiteElapsedMillis / 1000.0,
                    testFiles.size(),
                    failuresByFile.size()));
            writer.newLine();
            for (String filename : testFiles) {
                String name = namesByFile.getOrDefault(filename, filename);
                double seconds = elapsedByFile.getOrDefault(filename, 0L) / 1000.0;
                TestFailure failure = failuresByFile.get(filename);
                if (failure == null) {
                    writer.write(String.format(Locale.ROOT,
                            "    <testcase name=\"%s\" classname=\"%s\" time=\"%.3f\"/>",
                            xmlEscape(name),
                            HttpJsonDynamicUnitTest.class.getName(),
                            seconds));
                    writer.newLine();
                } else {
                    writer.write(String.format(Locale.ROOT,
                            "    <testcase name=\"%s\" classname=\"%s\" time=\"%.3f\">",
                            xmlEscape(name),
                            HttpJsonDynamicUnitTest.class.getName(),
                            seconds));
                    writer.newLine();
                    writer.write("        <failure>");
                    writer.newLine();
                    writer.write("            Step: " + xmlEscape(failure.testStep));
                    writer.newLine();
                    writer.write("            Reason: " + xmlEscape(failure.reason));
                    writer.newLine();
                    writer.write("            Expected: " + xmlEscape(failure.expected));
                    writer.newLine();
                    writer.write("            Found: " + xmlEscape(failure.found));
                    writer.newLine();
                    writer.write("        </failure>");
                    writer.newLine();
                    writer.write("    </testcase>");
                    writer.newLine();
                }
            }
            writer.write("</testsuite>");
            writer.newLine();
        }
    }

    private static String xmlEscape(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static final class TestFailure {
        private final String testStep;
        private final String reason;
        private final String expected;
        private final String found;

        private TestFailure(final String testStep,
                            final String reason,
                            final String expected,
                            final String found) {
            this.testStep = testStep;
            this.reason = reason;
            this.expected = expected;
            this.found = found;
        }
    }
}
