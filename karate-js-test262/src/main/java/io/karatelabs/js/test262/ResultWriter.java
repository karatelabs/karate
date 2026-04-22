package io.karatelabs.js.test262;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Atomic JSONL writer: sorts records alphabetically by path and writes with
 * a .tmp + rename so a crash mid-write can't corrupt the committed file.
 */
public final class ResultWriter {

    private ResultWriter() {}

    public static void write(Path file, List<ResultRecord> records) throws IOException {
        List<ResultRecord> sorted = new java.util.ArrayList<>(records);
        Collections.sort(sorted, Comparator.comparing(ResultRecord::path));

        StringBuilder sb = new StringBuilder(sorted.size() * 64);
        for (ResultRecord r : sorted) {
            sb.append(r.toJsonLine()).append('\n');
        }

        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Path parent = tmp.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
