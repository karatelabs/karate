package io.karatelabs.js.test262;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gitignored per-run context. Captured at the end of a full run and used
 * by the HTML report to render a self-describing header.
 */
public record RunMeta(
        String test262Sha,
        String karateJsVersion,
        String karateJsGitSha,
        String jdk,
        String os,
        Instant startedAt,
        Instant endedAt,
        Counts counts,
        List<String> cliArgs) {

    public record Counts(int pass, int fail, int skip, int total) {}

    public void writeTo(Path file) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("test262_sha", test262Sha == null ? "" : test262Sha);
        m.put("karate_js_version", karateJsVersion == null ? "" : karateJsVersion);
        m.put("karate_js_git_sha", karateJsGitSha == null ? "" : karateJsGitSha);
        m.put("jdk", jdk);
        m.put("os", os);
        m.put("started_at", startedAt.toString());
        m.put("ended_at", endedAt.toString());
        m.put("elapsed_ms", Duration.between(startedAt, endedAt).toMillis());
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("pass", counts.pass());
        c.put("fail", counts.fail());
        c.put("skip", counts.skip());
        c.put("total", counts.total());
        m.put("counts", c);
        m.put("cli_args", cliArgs);

        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(file, JsonLite.writePretty(m), StandardCharsets.UTF_8);
    }

    public static String detectJdk() {
        return System.getProperty("java.vm.name", "?") + " " + System.getProperty("java.version", "?");
    }

    public static String detectOs() {
        return System.getProperty("os.name", "?") + " " + System.getProperty("os.version", "?")
                + " (" + System.getProperty("os.arch", "?") + ")";
    }
}
