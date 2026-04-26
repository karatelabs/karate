package io.karatelabs.js.test262;

import org.junit.jupiter.api.Test;

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
 * Both index.html and details.html embed the same run-meta block.
 */
class Test262ReportEmbedTest {

    @Test
    void testRunMetaEmbedIsJsonParseable(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        // Test262Report writes outputs into <run-dir>/html/, and reads
        // <run-dir>/results.jsonl + <run-dir>/run-meta.json. Use tmp as the
        // run-dir directly.
        Path runDir = tmp;
        Path results = runDir.resolve("results.jsonl");
        Path runMeta = runDir.resolve("run-meta.json");

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

        Test262Report.main(new String[] { "--run-dir", runDir.toString() });

        // Both pages embed the same run-meta block — assert on both.
        for (String page : new String[] { "index.html", "details.html" }) {
            String html = Files.readString(runDir.resolve("html").resolve(page));
            // The page must not have been terminated early by the sneaky </script>.
            assertTrue(html.contains("<script id=\"run-meta\" type=\"application/json\">"),
                    page + ": missing run-meta script");
            // The literal </script> in JSON must have been neutralized (backslash-escaped).
            assertTrue(html.contains("<\\/script>"),
                    page + ": expected neutralized </script> in embed");
            // The JSON inside must not contain HTML entities.
            Matcher m = Pattern.compile(
                    "<script id=\"run-meta\"[^>]*>(.*?)</script>", Pattern.DOTALL).matcher(html);
            assertTrue(m.find(), page + ": could not locate embed");
            String embedded = m.group(1).strip();
            assertFalse(embedded.contains("&quot;"),
                    page + ": JSON must not be HTML-escaped: " + embedded);
            assertFalse(embedded.contains("&amp;"), page + ": JSON must not be HTML-escaped");

            // The embed should JSON-decode cleanly after reversing the </script> neutralization.
            String decoded = embedded.replace("<\\/", "</");
            assertTrue(decoded.trim().startsWith("{"), page + ": " + decoded);
            assertTrue(decoded.contains("\"test262_sha\""), page + ": " + decoded);
            assertTrue(decoded.contains("\"abc123\""), page + ": " + decoded);
        }
    }
}
