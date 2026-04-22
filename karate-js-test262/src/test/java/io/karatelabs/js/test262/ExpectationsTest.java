package io.karatelabs.js.test262;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class ExpectationsTest {

    @Test
    void testLoadsBundledExpectations() {
        Path yaml = Paths.get("config/expectations.yaml");
        assumeExists(yaml);
        Expectations exp = Expectations.load(yaml);

        // A test under intl402 should be skipped due to path rule.
        Test262Case intl = syntheticCase("test/intl402/Date/prototype/toLocaleString.js", Test262Metadata.empty());
        assertNotNull(exp.matchSkip(intl));

        // A test under language/expressions/addition should NOT be skipped by path.
        Test262Case ok = syntheticCase("test/language/expressions/addition/S11.6.1_A1.js", Test262Metadata.empty());
        assertNull(exp.matchSkip(ok));
    }

    @Test
    void testFlagsSkip() {
        Path yaml = Paths.get("config/expectations.yaml");
        assumeExists(yaml);
        Expectations exp = Expectations.load(yaml);

        Test262Metadata m = new Test262Metadata(
                java.util.List.of("module"), java.util.List.of(), java.util.List.of(),
                null, false, null);
        Test262Case c = syntheticCase("test/language/module-code/foo.js", m);
        String reason = exp.matchSkip(c);
        assertNotNull(reason);
        assertTrue(reason.toLowerCase().contains("module"), reason);
    }

    @Test
    void testFeaturesSkip() {
        Path yaml = Paths.get("config/expectations.yaml");
        assumeExists(yaml);
        Expectations exp = Expectations.load(yaml);

        Test262Metadata m = new Test262Metadata(
                java.util.List.of(), java.util.List.of("BigInt"), java.util.List.of(),
                null, false, null);
        Test262Case c = syntheticCase("test/language/bigint.js", m);
        String reason = exp.matchSkip(c);
        assertNotNull(reason);
        assertTrue(reason.toLowerCase().contains("bigint"), reason);
    }

    @Test
    void testIncludesSkip() {
        Path yaml = Paths.get("config/expectations.yaml");
        assumeExists(yaml);
        Expectations exp = Expectations.load(yaml);

        Test262Metadata m = new Test262Metadata(
                java.util.List.of(), java.util.List.of(),
                java.util.List.of("assert.js", "propertyHelper.js"),
                null, false, null);
        Test262Case c = syntheticCase("test/built-ins/Object/proto.js", m);
        String reason = exp.matchSkip(c);
        assertNotNull(reason);
        // propertyHelper.js is in the starter skip list
        assertTrue(reason.toLowerCase().contains("internals")
                || reason.toLowerCase().contains("property"), reason);
    }

    private static Test262Case syntheticCase(String relPath, Test262Metadata m) {
        return new Test262Case(relPath, Path.of(relPath), "", m);
    }

    private static void assumeExists(Path p) {
        if (!Files.isRegularFile(p)) {
            throw new org.opentest4j.TestAbortedException("expectations.yaml not found: " + p.toAbsolutePath());
        }
    }
}
