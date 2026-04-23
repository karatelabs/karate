package io.karatelabs.js.test262;

/**
 * One row in target/results.jsonl. Immutable. The runner writes these sorted by {@code path}.
 * <p>
 * JSON key order is fixed (path, status, error_type, message, reason) so that
 * file diffs across runs are minimal and interpretable. All optional fields are
 * omitted when null.
 */
public record ResultRecord(
        String path,
        Status status,
        String errorType,
        String message,
        String reason) {

    public enum Status { PASS, FAIL, SKIP }

    public static ResultRecord pass(String path) {
        return new ResultRecord(path, Status.PASS, null, null, null);
    }

    public static ResultRecord fail(String path, String errorType, String message) {
        return new ResultRecord(path, Status.FAIL, errorType, message, null);
    }

    public static ResultRecord skip(String path, String reason) {
        return new ResultRecord(path, Status.SKIP, null, null, reason);
    }

    /** Writes this record as a single JSONL line (no trailing newline). */
    public String toJsonLine() {
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        appendKv(sb, "path", path, true);
        appendKv(sb, "status", status.name(), false);
        if (errorType != null) appendKv(sb, "error_type", errorType, false);
        if (message != null) appendKv(sb, "message", message, false);
        if (reason != null) appendKv(sb, "reason", reason, false);
        sb.append('}');
        return sb.toString();
    }

    private static void appendKv(StringBuilder sb, String key, String value, boolean first) {
        if (!first) sb.append(',');
        sb.append('"').append(key).append("\":");
        JsonLite.writeString(sb, value);
    }
}
