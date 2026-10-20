package io.karatelabs.core;

import io.karatelabs.common.Resource;
import io.karatelabs.js.DebugPointFactory;
import io.karatelabs.js.RunInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JS in a def or eval docstring is evaluated with the feature file as its source, so its statements
 * carry the feature path and their own line (what a debugger needs to stop on a breakpoint there),
 * also past a blank line, which the parser drops from the docstring text.
 */
class DocStringDebugSourceTest {

    static final String FEATURE = """
            Feature: docstring debug source

            Scenario: def and eval docstrings
            * def fn =
              \"""
              function(x) {
                var a = x * 2;

                var b = a + 1;
                return b;
              }
              \"""
            * def r = fn(5)
            * match r == 11
            * eval
              \"""
              var z = 1;

              karate.set('zz', z + 1);
              \"""
            * match zz == 2
            * def xmlv =
              \"""
              <root><a>#(r)</a></root>
              \"""
            * match xmlv == <root><a>11</a></root>
            * def jsonv =
              \"""
              { "a": "#(r)", "b": [1, 2] }
              \"""
            * match jsonv == { a: 11, b: [1, 2] }
            * def multi =
              \"""
              `one

              two`
              \"""
            * match multi == 'one\\n\\ntwo'
            """;

    @Test
    void docStringStatementsCarryTheFeatureAndTheirLine(@TempDir Path dir) throws Exception {
        Path feature = dir.resolve("test.feature");
        Files.writeString(feature, FEATURE);
        List<String> points = new CopyOnWriteArrayList<>();
        Set<Integer> jsLines = new TreeSet<>();
        RunInterceptor<Object> interceptor = point -> RunInterceptor.Action.PROCEED;
        DebugPointFactory<Object> factory = (type, line, sourcePath, source, jsContext) -> {
            points.add(type + ":" + sourcePath + ":" + line);
            if (type == DebugPointFactory.JS_STATEMENT && sourcePath != null && sourcePath.endsWith("test.feature")) {
                jsLines.add(line);
            }
            return new Object();
        };
        SuiteResult result = Runner.builder()
                .path(feature.toString())
                .workingDir(dir)
                .debugSupport(interceptor, factory)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .outputDir(dir.resolve("out"))
                .parallel(1);
        assertTrue(result.isPassed(), () -> result.getErrors().toString());
        List<String> lines = List.of(FEATURE.split("\n", -1));
        // JS debug-point lines are 0-indexed
        for (String statement : List.of("var a = x * 2;", "var b = a + 1;", "return b;", "var z = 1;", "karate.set('zz', z + 1);")) {
            int index = indexOf(lines, statement);
            assertTrue(jsLines.contains(index), statement + " at line index " + index + ", JS lines seen: " + jsLines + "\n" + points);
        }
    }

    static final String OUTLINE_FEATURE = """
            Feature: docstring debug source in outlines

            Scenario Outline: row <v>
            * def fn =
              \"""
              function(x) {

                var t = x * <v>;
                return t;
              }
              \"""
            * match fn(2) == 2 * <v>
            Examples:
            | v |
            | 1 |
            | 2 |

            Scenario Outline: dynamic <w>
            * def fn2 =
              \"""
              function(x) {

                var u = x + <w>;
                return u;
              }
              \"""
            * match fn2(1) == 1 + <w>
            Examples:
            | [{ w: 1 }, { w: 2 }] |
            """;

    @Test
    void outlineCopiesKeepTheDocStringSource(@TempDir Path dir) throws Exception {
        Path feature = dir.resolve("outline.feature");
        Files.writeString(feature, OUTLINE_FEATURE);
        List<String> points = new CopyOnWriteArrayList<>();
        Set<Integer> jsLines = new TreeSet<>();
        RunInterceptor<Object> interceptor = point -> RunInterceptor.Action.PROCEED;
        DebugPointFactory<Object> factory = (type, line, sourcePath, source, jsContext) -> {
            points.add(type + ":" + sourcePath + ":" + line);
            if (type == DebugPointFactory.JS_STATEMENT && sourcePath != null && sourcePath.endsWith("outline.feature")) {
                jsLines.add(line);
            }
            return new Object();
        };
        SuiteResult result = Runner.builder()
                .path(feature.toString())
                .workingDir(dir)
                .debugSupport(interceptor, factory)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .outputDir(dir.resolve("out"))
                .parallel(1);
        assertTrue(result.isPassed(), () -> result.getErrors().toString());
        assertEquals(4, result.getScenarioCount(), "two static rows, two dynamic");
        List<String> lines = List.of(OUTLINE_FEATURE.split("\n", -1));
        for (String statement : List.of("var t = x * <v>;", "return t;", "var u = x + <w>;", "return u;")) {
            int index = indexOf(lines, statement);
            assertTrue(jsLines.contains(index), statement + " at line index " + index + ", JS lines seen: " + jsLines + "\n" + points);
        }
    }

    @Test
    void alignPutsBackTheBlankLines() {
        String source = "Feature:\nScenario:\n* def f =\n  \"\"\"\n  a\n\n  b\n   \n  c\n  \"\"\"\n";
        Resource resource = Resource.text(source);
        // what the parser makes of it: empty lines dropped, the whitespace-only line kept
        assertEquals("(a\n\nb\n \nc)", ScenarioRuntime.alignToSourceLines("(a\nb\n \nc)", resource, 4));
        assertEquals("a", ScenarioRuntime.alignToSourceLines("a", resource, 4), "single line: as is");
        assertEquals("a\nb", ScenarioRuntime.alignToSourceLines("a\nb", resource, -1), "no line: as is");
        assertEquals("a\nb\nc\nd\ne\nf", ScenarioRuntime.alignToSourceLines("a\nb\nc\nd\ne\nf", resource, 8),
                "more lines than the source has left: as is");
    }

    private static int indexOf(List<String> lines, String text) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals(text)) {
                return i;
            }
        }
        throw new IllegalArgumentException(text);
    }

}
