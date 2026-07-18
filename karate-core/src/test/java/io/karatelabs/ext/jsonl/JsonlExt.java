/*
 * Test-only fixture: a minimal ext that declares Ext.requiresJsonlEvents() — the
 * SPI seam an event-stream-consuming ext (e.g. a coverage analyzer) uses to have
 * the Suite auto-enable the karate-events.jsonl writer without -f karate:jsonl.
 * Resolved by name convention (io.karatelabs.ext.jsonl.JsonlExt) from
 * `boot.ext('jsonl')`.
 */
package io.karatelabs.ext.jsonl;

import io.karatelabs.core.Ext;

public class JsonlExt implements Ext {

    @Override
    public boolean requiresJsonlEvents() {
        return true;
    }
}
