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
package io.karatelabs.output;

import io.karatelabs.common.Json;
import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.ScenarioResult;
import io.karatelabs.core.StepResult;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.Scenario;
import io.karatelabs.gherkin.Step;
import io.karatelabs.gherkin.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates Cucumber JSON reports compatible with third-party tools (Allure, ReportPortal, etc.).
 * <p>
 * Output follows the standard Cucumber JSON schema:
 * <pre>
 * [
 *   {
 *     "id": "feature-id",
 *     "uri": "path/to/feature.feature",
 *     "name": "Feature Name",
 *     "keyword": "Feature",
 *     "elements": [
 *       {
 *         "id": "feature-id;scenario-id",
 *         "name": "Scenario Name",
 *         "keyword": "Scenario",
 *         "type": "scenario",
 *         "steps": [
 *           {
 *             "keyword": "Given ",
 *             "name": "step text",
 *             "result": { "status": "passed", "duration": 12345678 }
 *           }
 *         ]
 *       }
 *     ]
 *   }
 * ]
 * </pre>
 */
public final class CucumberJsonWriter {

    private static final Logger logger = LoggerFactory.getLogger("karate.runtime");

    private CucumberJsonWriter() {
    }

    /**
     * Write Cucumber JSON report for a single feature.
     * <p>
     * Output file is named {@code {packageQualifiedName}.json} and contains
     * an array with a single feature object (Cucumber JSON format).
     *
     * @param result    the feature result to convert
     * @param outputDir the directory to write the report
     */
    public static void writeFeature(FeatureResult result, Path outputDir) {
        try {
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            String fileName = result.getFeature().getResource().getPackageQualifiedName() + ".json";
            Path jsonPath = outputDir.resolve(fileName);
            String json = featureToJson(result);
            Files.writeString(jsonPath, json);
            logger.debug("Cucumber JSON written: {}", jsonPath);
        } catch (Exception e) {
            logger.warn("Failed to write Cucumber JSON for {}: {}", result.getDisplayName(), e.getMessage());
        }
    }

    /**
     * Convert a single feature result to Cucumber JSON string.
     * <p>
     * Returns a JSON array containing a single feature object.
     *
     * @param result the feature result
     * @return JSON string
     */
    public static String featureToJson(FeatureResult result) {
        List<Map<String, Object>> features = Collections.singletonList(featureToMap(result));
        return Json.of(features).toStringPretty();
    }

    private static Map<String, Object> featureToMap(FeatureResult fr) {
        Map<String, Object> map = new LinkedHashMap<>();
        Feature feature = fr.getFeature();

        // Feature ID (uri-based, lowercase, hyphens)
        String id = toId(feature.getName());
        String uri = feature.getResource().getRelativePath();

        map.put("id", id);
        map.put("uri", uri);
        map.put("name", feature.getName() != null ? feature.getName() : "");
        map.put("description", feature.getDescription() != null ? feature.getDescription() : "");
        map.put("keyword", "Feature");
        map.put("line", feature.getLine());

        // Feature tags
        List<Tag> tags = feature.getTags();
        if (tags != null && !tags.isEmpty()) {
            map.put("tags", tagsToList(tags, feature.getLine()));
        }

        // Elements (scenarios)
        List<Map<String, Object>> elements = new ArrayList<>();
        for (ScenarioResult sr : fr.getScenarioResults()) {
            elements.add(scenarioToMap(sr, id));
        }
        map.put("elements", elements);

        return map;
    }

    private static Map<String, Object> scenarioToMap(ScenarioResult sr, String featureId) {
        Map<String, Object> map = new LinkedHashMap<>();
        Scenario scenario = sr.getScenario();

        String scenarioId = toId(scenario.getName());
        String fullId = featureId + ";" + scenarioId;

        map.put("id", fullId);
        map.put("name", scenario.getName() != null ? scenario.getName() : "");
        map.put("description", scenario.getDescription() != null ? scenario.getDescription() : "");
        map.put("keyword", scenario.isOutlineExample() ? "Scenario Outline" : "Scenario");
        map.put("line", scenario.getLine());
        map.put("type", "scenario");

        // Scenario tags
        List<Tag> tags = scenario.getTags();
        if (tags != null && !tags.isEmpty()) {
            map.put("tags", tagsToList(tags, scenario.getLine()));
        }

        // Steps
        List<Map<String, Object>> steps = new ArrayList<>();
        for (StepResult stepResult : sr.getStepResults()) {
            steps.add(stepToMap(stepResult));
        }
        map.put("steps", steps);

        return map;
    }

    private static Map<String, Object> stepToMap(StepResult sr) {
        Map<String, Object> map = new LinkedHashMap<>();
        Step step = sr.getStep();

        // Cucumber keyword format includes trailing space (e.g., "Given ")
        String keyword = mapToCucumberKeyword(step.getPrefix());
        map.put("keyword", keyword);
        // Name includes both Karate keyword and text (e.g., "def a = 1" not just "a = 1")
        String name = buildStepName(step);
        map.put("name", name);
        map.put("line", step.getLine());

        // Doc string
        if (step.getDocString() != null) {
            Map<String, Object> docString = new LinkedHashMap<>();
            docString.put("value", step.getDocString());
            docString.put("content_type", "");
            docString.put("line", step.getLine() + 1);
            map.put("doc_string", docString);
        }

        // Data table
        if (step.getTable() != null) {
            map.put("rows", tableToRows(step.getTable(), step.getLine()));
        }

        // Result
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", mapStatus(sr.getStatus()));
        result.put("duration", sr.getDurationNanos());

        if (sr.isFailed() && sr.getError() != null) {
            result.put("error_message", sr.getError().getMessage());
        }

        map.put("result", result);

        // Embeddings (from logs and embeds)
        List<Map<String, Object>> embeddings = new ArrayList<>();

        // Add log as text/plain embedding if present
        if (sr.getLog() != null && !sr.getLog().isEmpty()) {
            Map<String, Object> logEmbed = new LinkedHashMap<>();
            logEmbed.put("mime_type", "text/plain");
            logEmbed.put("data", Base64.getEncoder().encodeToString(sr.getLog().getBytes()));
            embeddings.add(logEmbed);
        }

        // Add actual embeds
        if (sr.getEmbeds() != null) {
            for (StepResult.Embed embed : sr.getEmbeds()) {
                Map<String, Object> embedMap = new LinkedHashMap<>();
                embedMap.put("mime_type", embed.getMimeType());
                embedMap.put("data", Base64.getEncoder().encodeToString(embed.getData()));
                if (embed.getName() != null) {
                    embedMap.put("name", embed.getName());
                }
                embeddings.add(embedMap);
            }
        }

        if (!embeddings.isEmpty()) {
            map.put("embeddings", embeddings);
        }

        return map;
    }

    private static List<Map<String, Object>> tagsToList(List<Tag> tags, int line) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Tag tag : tags) {
            Map<String, Object> tagMap = new LinkedHashMap<>();
            tagMap.put("name", tag.toString());
            tagMap.put("line", line);
            result.add(tagMap);
        }
        return result;
    }

    private static List<Map<String, Object>> tableToRows(io.karatelabs.gherkin.Table table, int startLine) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<List<String>> data = table.getRows();
        int line = startLine + 1;

        for (List<String> row : data) {
            Map<String, Object> rowMap = new LinkedHashMap<>();
            List<Map<String, String>> cells = new ArrayList<>();
            for (String cell : row) {
                Map<String, String> cellMap = new LinkedHashMap<>();
                cellMap.put("value", cell);
                cells.add(cellMap);
            }
            rowMap.put("cells", cells);
            rowMap.put("line", line++);
            rows.add(rowMap);
        }
        return rows;
    }

    /**
     * Build the step name including the Karate keyword and text.
     * E.g., "def a = 1" for a step like "* def a = 1".
     */
    private static String buildStepName(Step step) {
        String keyword = step.getKeyword();
        String text = step.getText();
        if (keyword != null && !keyword.isEmpty()) {
            return keyword + " " + (text != null ? text : "");
        }
        return text != null ? text : "";
    }

    /**
     * Map Karate step prefix to Cucumber keyword format (with trailing space).
     * Cucumber expects keywords like "Given ", "When ", "Then ", "And ", "But ", "* ".
     */
    private static String mapToCucumberKeyword(String prefix) {
        if (prefix == null) {
            return "* ";
        }
        // Karate uses "*" prefix but stores keywords like "def", "match", etc.
        // For Cucumber compatibility, we map based on prefix
        return switch (prefix) {
            case "Given" -> "Given ";
            case "When" -> "When ";
            case "Then" -> "Then ";
            case "And" -> "And ";
            case "But" -> "But ";
            default -> "* ";
        };
    }

    /**
     * Map Karate status to Cucumber status string.
     */
    private static String mapStatus(StepResult.Status status) {
        return switch (status) {
            case PASSED -> "passed";
            case FAILED -> "failed";
            case SKIPPED -> "skipped";
        };
    }

    /**
     * Convert a name to a Cucumber-style ID (lowercase, spaces to hyphens).
     */
    private static String toId(String name) {
        if (name == null || name.isEmpty()) {
            return "unknown";
        }
        return name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

}
