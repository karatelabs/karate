/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.core;

import io.karatelabs.common.Resource;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.Scenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the 'doc' keyword which renders HTML templates.
 * Uses the templating infrastructure from TEMPLATING.md.
 */
class StepDocTest {

    @TempDir
    Path tempDir;

    @Test
    void testDocWithStringPath() throws Exception {
        // Create a simple HTML template
        Path template = tempDir.resolve("simple.html");
        Files.writeString(template, "<html><body>Hello World</body></html>");

        Path featurePath = tempDir.resolve("test.feature");
        Files.writeString(featurePath, """
            Feature:
            Scenario:
            * doc 'simple.html'
            """);

        // Run the feature
        Feature feature = Feature.read(Resource.from(featurePath));
        Scenario scenario = feature.getSections().get(0).getScenario();
        KarateJs karate = new KarateJs(Resource.from(featurePath.getParent()), new InMemoryHttpClient());
        ScenarioRuntime sr = new ScenarioRuntime(karate, scenario);
        ScenarioResult result = sr.call();

        assertTrue(result.isPassed(), "Scenario should pass");

        // Find the doc step result
        List<StepResult> stepResults = result.getStepResults();
        StepResult docStepResult = stepResults.stream()
                .filter(s -> "doc".equals(s.getStep().getKeyword()))
                .findFirst()
                .orElse(null);

        assertNotNull(docStepResult, "Should have doc step result");
        assertNotNull(docStepResult.getEmbeds(), "doc step should create embed");
        assertEquals(1, docStepResult.getEmbeds().size());
        assertEquals("text/html", docStepResult.getEmbeds().get(0).getMimeType());
        String html = new String(docStepResult.getEmbeds().get(0).getData());
        assertTrue(html.contains("Hello World"));
    }

    @Test
    void testDocWithMapSyntax() throws Exception {
        // Create a simple HTML template
        Path template = tempDir.resolve("template.html");
        Files.writeString(template, "<html><body><span th:text=\"'placeholder'\">placeholder</span></body></html>");

        Path featurePath = tempDir.resolve("test.feature");
        Files.writeString(featurePath, """
            Feature:
            Scenario:
            * doc { read: 'template.html' }
            """);

        Feature feature = Feature.read(Resource.from(featurePath));
        Scenario scenario = feature.getSections().get(0).getScenario();
        KarateJs karate = new KarateJs(Resource.from(featurePath.getParent()), new InMemoryHttpClient());
        ScenarioRuntime sr = new ScenarioRuntime(karate, scenario);
        ScenarioResult result = sr.call();

        assertTrue(result.isPassed(), "Scenario should pass");

        // Verify embed was created
        StepResult docStepResult = result.getStepResults().get(0);
        assertNotNull(docStepResult.getEmbeds());
        assertEquals(1, docStepResult.getEmbeds().size());
    }

    @Test
    void testDocWithVariableSubstitution() throws Exception {
        // Create template with Thymeleaf expression
        Path template = tempDir.resolve("greeting.html");
        Files.writeString(template, "<html><body><h1 th:text=\"title\">Title</h1></body></html>");

        Path featurePath = tempDir.resolve("test.feature");
        Files.writeString(featurePath, """
            Feature:
            Scenario:
            * def title = 'Hello from Karate'
            * doc 'greeting.html'
            """);

        AtomicReference<String> docOutput = new AtomicReference<>();

        Feature feature = Feature.read(Resource.from(featurePath));
        Scenario scenario = feature.getSections().get(0).getScenario();
        KarateJs karate = new KarateJs(Resource.from(featurePath.getParent()), new InMemoryHttpClient());
        karate.setOnDoc(docOutput::set);
        ScenarioRuntime sr = new ScenarioRuntime(karate, scenario);
        ScenarioResult result = sr.call();

        assertTrue(result.isPassed(), "Scenario should pass");

        // Verify template was rendered with variable substitution
        String html = docOutput.get();
        assertNotNull(html, "doc should have been called");
        assertTrue(html.contains("Hello from Karate"), "Expected variable substitution in output");
    }

    @Test
    void testDocEmbedInStepResult() throws Exception {
        // Test that doc step properly embeds HTML in step result
        Path template = tempDir.resolve("embed-test.html");
        Files.writeString(template, "<html><body>Embedded Content</body></html>");

        Path featurePath = tempDir.resolve("test.feature");
        Files.writeString(featurePath, """
            Feature:
            Scenario:
            * doc 'embed-test.html'
            """);

        Feature feature = Feature.read(Resource.from(featurePath));
        Scenario scenario = feature.getSections().get(0).getScenario();
        KarateJs karate = new KarateJs(Resource.from(featurePath.getParent()), new InMemoryHttpClient());
        ScenarioRuntime sr = new ScenarioRuntime(karate, scenario);
        ScenarioResult result = sr.call();

        assertTrue(result.isPassed());

        StepResult stepResult = result.getStepResults().get(0);
        assertNotNull(stepResult.getEmbeds(), "doc step should have embeds");
        assertFalse(stepResult.getEmbeds().isEmpty(), "embeds should not be empty");

        var embed = stepResult.getEmbeds().get(0);
        assertEquals("text/html", embed.getMimeType());
        String content = new String(embed.getData());
        assertTrue(content.contains("Embedded Content"));
    }

    // ========== karate.render() tests ==========

    @Test
    void testRenderWithStringPath() throws Exception {
        Path template = tempDir.resolve("render-test.html");
        Files.writeString(template, "<html><body>Rendered Content</body></html>");

        Path featurePath = tempDir.resolve("test.feature");
        Files.writeString(featurePath, """
            Feature:
            Scenario:
            * def result = karate.render('render-test.html')
            * match result contains 'Rendered Content'
            """);

        Feature feature = Feature.read(Resource.from(featurePath));
        Scenario scenario = feature.getSections().get(0).getScenario();
        KarateJs karate = new KarateJs(Resource.from(featurePath.getParent()), new InMemoryHttpClient());
        ScenarioRuntime sr = new ScenarioRuntime(karate, scenario);
        ScenarioResult result = sr.call();

        assertTrue(result.isPassed(), "Scenario should pass: " + result.getFailureMessage());
    }

    @Test
    void testRenderWithMapSyntax() throws Exception {
        Path template = tempDir.resolve("render-map.html");
        Files.writeString(template, "<html><body>Map Syntax Test</body></html>");

        Path featurePath = tempDir.resolve("test.feature");
        Files.writeString(featurePath, """
            Feature:
            Scenario:
            * def result = karate.render({ read: 'render-map.html' })
            * match result contains 'Map Syntax Test'
            """);

        Feature feature = Feature.read(Resource.from(featurePath));
        Scenario scenario = feature.getSections().get(0).getScenario();
        KarateJs karate = new KarateJs(Resource.from(featurePath.getParent()), new InMemoryHttpClient());
        ScenarioRuntime sr = new ScenarioRuntime(karate, scenario);
        ScenarioResult result = sr.call();

        assertTrue(result.isPassed(), "Scenario should pass: " + result.getFailureMessage());
    }

    @Test
    void testRenderWithVariableSubstitution() throws Exception {
        Path template = tempDir.resolve("render-vars.html");
        Files.writeString(template, "<html><body><h1 th:text=\"name\">Name</h1></body></html>");

        Path featurePath = tempDir.resolve("test.feature");
        Files.writeString(featurePath, """
            Feature:
            Scenario:
            * def name = 'John Doe'
            * def result = karate.render('render-vars.html')
            * match result contains 'John Doe'
            """);

        Feature feature = Feature.read(Resource.from(featurePath));
        Scenario scenario = feature.getSections().get(0).getScenario();
        KarateJs karate = new KarateJs(Resource.from(featurePath.getParent()), new InMemoryHttpClient());
        ScenarioRuntime sr = new ScenarioRuntime(karate, scenario);
        ScenarioResult result = sr.call();

        assertTrue(result.isPassed(), "Scenario should pass: " + result.getFailureMessage());
    }

}
