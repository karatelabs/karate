package io.karatelabs.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RunawayRecursionTest {

    @Test
    void aDeepCatchThatFirstUsesTheKarateObjectStillEndsAsARangeError(@TempDir Path dir) throws Exception {
        // The catch runs on the stack the overflow left. A class initializer that overflows
        // there is a NoClassDefFoundError for the life of the JVM, so only a fresh JVM shows
        // it: the run must still end as a RangeError failure, write its report, and leave
        // the JVM able to raise an ordinary error after.
        assertFreshRuns(dir, "direct");
    }

    @Test
    void aDeepCatchBelowAKarateCallbackGetsTheSameHeadroom(@TempDir Path dir) throws Exception {
        // karate.map calls the JS function from Java: that is not a host boundary, so the
        // overflow must pass it to the next JS seam out instead of converting on the spot.
        assertFreshRuns(dir, "map");
    }

    private static void assertFreshRuns(Path dir, String shape) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<Process> runs = new ArrayList<>();
        for (int depth = 0; depth < 6; depth++) {
            runs.add(new ProcessBuilder(java, "-cp", classPath, DeepKarateCatch.class.getName(),
                    shape, String.valueOf(depth), dir.resolve("run" + depth).toString())
                    .redirectErrorStream(true).start());
        }
        for (Process run : runs) {
            String out = new String(run.getInputStream().readAllBytes());
            assertTrue(out.contains("result: RangeError|report|TypeError"), out);
        }
    }

    static class DeepKarateCatch {

        static SuiteResult runAtDepth(int depth, Path dir, String feature) {
            if (depth > 0) {
                return runAtDepth(depth - 1, dir, feature);
            }
            return Runner.path(dir.resolve(feature).toString()).workingDir(dir).outputDir(dir.resolve("reports"))
                    .outputHtmlReport(true).outputConsoleSummary(false).parallel(1);
        }

        static String verdict(SuiteResult result) {
            ScenarioResult sr = result.getFeatureResults().getFirst().getScenarioResults().getFirst();
            String message = String.valueOf(sr.getFailureMessage());
            if (message.contains("RangeError") && message.contains("Maximum call stack")) {
                return "RangeError";
            }
            return message.contains("TypeError") ? "TypeError" : message.replace('\n', ' ');
        }

        public static void main(String[] args) throws Exception {
            Path dir = Files.createDirectories(Path.of(args[2]));
            String feature = args[0].equals("direct") ? """
                    Feature: runaway recursion
                    Scenario: a catch at every level, the first use of karate.set and karate.toString
                    * def f = function(){ try { return f() } catch (e) { karate.set('x', karate.toString(e.message) + karate.os.type); throw e } }
                    * f()
                    """ : """
                    Feature: runaway recursion through a karate callback
                    Scenario: a catch at every level below karate.map, the first use of karate.prettyXml
                    * def f = function(){ try { return karate.map([1], f) } catch (e) { e.x = karate.prettyXml('<a><b>1</b></a>'); throw e } }
                    * f()
                    """;
            Files.writeString(dir.resolve("rec.feature"), feature);
            Files.writeString(dir.resolve("later.feature"), """
                    Feature: later
                    Scenario: an ordinary error
                    * eval null.x
                    """);
            String first;
            try {
                first = verdict(runAtDepth(Integer.parseInt(args[1]), dir, "rec.feature"));
            } catch (Throwable t) {
                first = "T:" + t;
            }
            String report = Files.exists(dir.resolve("reports").resolve("karate-summary.html")) ? "report" : "no report";
            String later = verdict(runAtDepth(0, dir, "later.feature"));
            System.out.println("result: " + first + "|" + report + "|" + later);
        }
    }

}
