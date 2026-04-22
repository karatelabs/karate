package io.karatelabs.js.test262;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A single test262 test file, with its source text and parsed frontmatter.
 *
 * @param relativePath  path relative to the test262 root (e.g. "test/language/...js")
 * @param absolutePath  absolute path on disk
 * @param source        full file contents (UTF-8)
 * @param metadata      parsed YAML frontmatter (empty if not present)
 */
public record Test262Case(
        String relativePath,
        Path absolutePath,
        String source,
        Test262Metadata metadata) {

    public static Test262Case load(Path test262Root, Path absolute) {
        try {
            String src = Files.readString(absolute);
            Test262Metadata meta = Test262Metadata.parse(src);
            String rel = test262Root.relativize(absolute).toString();
            // Normalize Windows separators to forward slashes for stable paths in results.
            rel = rel.replace('\\', '/');
            return new Test262Case(rel, absolute, src, meta);
        } catch (Exception e) {
            throw new RuntimeException("failed to load test262 case: " + absolute, e);
        }
    }
}
