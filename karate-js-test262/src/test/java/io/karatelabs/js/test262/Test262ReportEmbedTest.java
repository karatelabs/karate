package io.karatelabs.js.test262;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards against a class of bug where {@code run-meta.json} embedded inside
 * {@code <script type="application/json">} gets HTML-entity-escaped, making it
 * unparseable by {@code JSON.parse} in the browser (entities aren't decoded
 * inside that tag). Verifies the JSON survives embed→extract round-trip.
 */
class Test262ReportEmbedTest {

    @Test
    void testRunMetaEmbedIsJsonParseable(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path results = tmp.resolve("results.jsonl");
        Path runMeta = tmp.resolve("run-meta.json");
        Path out = tmp.resolve("html");

        // minimal but realistic fixture
        Files.writeString(results,
                "{\"path\":\"test/language/foo.js\",\"status\":\"PASS\"}\n" +
                "{\"path\":\"test/language/bar.js\",\"status\":\"FAIL\",\"error_type\":\"TypeError\",\"message\":\"x\"}\n");
        // Include a sneaky "</script" substring to confirm the neutralization.
        String meta = """
            {
              "test262_sha": "abc123",
              "karate_js_version": "2.0.5.RC1",
              "note": "trap: </script> should not break the page",
              "counts": { "pass": 1, "fail": 1, "skip": 0, "total": 2 }
            }
            """;
        Files.writeString(runMeta, meta);

        Test262Report.main(new String[] {
                "--results", results.toString(),
                "--run-meta", runMeta.toString(),
                "--test262", tmp.toString(),
                "--out", out.toString()
        });

        String html = Files.readString(out.resolve("index.html"));
        // The page must not have been terminated early by the sneaky </script>.
        assertTrue(html.contains("<script id=\"run-meta\" type=\"application/json\">"), html);
        // The literal </script> in JSON must have been neutralized (backslash-escaped).
        assertTrue(html.contains("<\\/script>"), "expected neutralized </script> in embed");
        // And the JSON inside must not contain HTML entities.
        Matcher m = Pattern.compile(
                "<script id=\"run-meta\"[^>]*>(.*?)</script>", Pattern.DOTALL).matcher(html);
        assertTrue(m.find(), "could not locate embed");
        String embedded = m.group(1).strip();
        assertFalse(embedded.contains("&quot;"), "JSON must not be HTML-escaped: " + embedded);
        assertFalse(embedded.contains("&amp;"), "JSON must not be HTML-escaped");

        // The embed should JSON-decode cleanly after reversing the </script> neutralization.
        // Browsers do NOT reverse HTML entities inside <script type="application/json">; they
        // DO see the raw bytes. We wrote "<\/script" which is a valid JSON escape for "</script".
        String decoded = embedded.replace("<\\/", "</");
        // Smoke-test: the decoded string starts with '{' and contains expected keys.
        assertTrue(decoded.trim().startsWith("{"), decoded);
        assertTrue(decoded.contains("\"test262_sha\""), decoded);
        assertTrue(decoded.contains("\"abc123\""), decoded);
    }
}
