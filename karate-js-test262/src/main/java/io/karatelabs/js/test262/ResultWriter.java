package io.karatelabs.js.test262;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Atomic JSONL writer: sorts a list of raw JSONL lines and writes them with a
 * {@code .tmp} + rename so a crash mid-write can't corrupt the committed file.
 * <p>
 * Each line is expected to begin with {@code {"path":"…"} — the runner writes
 * them in that canonical shape ({@link ResultRecord#toJsonLine()}) so
 * lexicographic line sort is equivalent to sorting records by path.
 */
public final class ResultWriter {

    private ResultWriter() {}

    public static void sortAndWrite(Path file, List<String> jsonlLines) throws IOException {
        List<String> sorted = new ArrayList<>(jsonlLines);
        Collections.sort(sorted);

        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Path parent = tmp.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.write(tmp, sorted, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
