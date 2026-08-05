/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.gatling;

import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.ScenarioResult;
import io.karatelabs.core.StepResult;
import io.karatelabs.output.Console;
import io.karatelabs.output.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the per-step output of the Karate features a virtual user has run, and replays it when one
 * of them fails — see {@link KarateLogReplay} for why this is needed at all.
 * <p>
 * Everything replayed is text Karate had already captured into the report buffer; nothing extra is
 * recorded, and when the mode is {@link KarateLogReplay#OFF} (the default) no text is rendered and
 * no buffer is allocated.
 */
public class LogReplayer {

    /**
     * Replayed output goes to this logger — {@code io.karatelabs.gatling.LogReplayer} — so it can
     * be routed independently of the rest of the run (a separate appender / file), and so it is not
     * silenced by the level pinned on the {@code karate.*} categories that produced it.
     */
    private static final Logger log = LoggerFactory.getLogger(LogReplayer.class);

    /** Default number of already-passed feature logs retained per virtual user in ALL mode. */
    public static final int DEFAULT_LIMIT = 5;

    /** Default level the replayed output is logged at — high enough to survive a quiet load run. */
    public static final LogLevel DEFAULT_LEVEL = LogLevel.ERROR;

    private final KarateLogReplay mode;
    private final LogLevel level;
    private final int limit;

    LogReplayer(KarateLogReplay mode, LogLevel level, int limit) {
        this.mode = mode == null ? KarateLogReplay.OFF : mode;
        this.level = level == null ? DEFAULT_LEVEL : level;
        this.limit = limit;
    }

    /** The replayer configured on the protocol, or a disabled one when there is no protocol. */
    static LogReplayer forProtocol(KarateProtocol protocol) {
        if (protocol == null) {
            return new LogReplayer(KarateLogReplay.OFF, DEFAULT_LEVEL, DEFAULT_LIMIT);
        }
        return new LogReplayer(protocol.getLogReplay(), protocol.getLogReplayLevel(), protocol.getLogReplayLimit());
    }

    public boolean isEnabled() {
        return mode != KarateLogReplay.OFF;
    }

    /**
     * Fold one finished feature into the virtual user's buffer, replaying if it failed.
     *
     * @param buffer      the buffer carried on the Gatling session, may be null
     * @param featurePath the feature path as the simulation declared it
     * @param result      the finished feature
     * @return the buffer to carry forward — empty after a replay, null when disabled
     */
    Buffer after(Buffer buffer, String featurePath, FeatureResult result) {
        if (!isEnabled()) {
            return null;
        }
        String rendered = render(featurePath, result);
        if (!result.isFailed()) {
            // nothing to retain in FAILED mode: only the failing feature is ever replayed
            return mode == KarateLogReplay.ALL ? Buffer.of(buffer).with(rendered, limit) : buffer;
        }
        if (mode == KarateLogReplay.ALL && buffer != null) {
            if (buffer.dropped > 0) {
                emit(buffer.dropped + " earlier karate feature log(s) dropped — logReplayLimit is " + limit);
            }
            for (String entry : buffer.entries) {
                emit(entry);
            }
        }
        if (rendered != null) {
            emit(rendered);
        }
        // start the next iteration clean — the failure this output explains has been logged
        return Buffer.EMPTY;
    }

    private void emit(String message) {
        switch (level) {
            case TRACE -> log.trace(message);
            case DEBUG -> log.debug(message);
            case INFO -> log.info(message);
            case WARN -> log.warn(message);
            default -> log.error(message);
        }
    }

    /**
     * Render a feature's captured per-step output as it would have appeared on the console, framed
     * so multiple features stay distinguishable in one log. ANSI colour and the report-only body
     * sentinels are stripped — this is written to a log file, not a terminal. Returns null when the
     * feature produced no output at all (no HTTP call, no {@code print}).
     */
    static String render(String featurePath, FeatureResult result) {
        StringBuilder sb = new StringBuilder();
        for (ScenarioResult sr : result.getScenarioResults()) {
            for (StepResult step : sr.getStepResults()) {
                String stepLog = step.getLog();
                if (stepLog == null || stepLog.isBlank()) {
                    continue;
                }
                sb.append(Console.stripSentinels(Console.stripAnsi(stepLog)).stripTrailing()).append('\n');
            }
        }
        if (sb.isEmpty()) {
            return null;
        }
        String status = result.isFailed() ? "failed" : "passed";
        return ">>> karate: " + featurePath + " [" + status + "]\n"
                + sb + "<<< karate: " + featurePath;
    }

    /**
     * The retained feature logs for one virtual user. Immutable: {@link #with} returns a new
     * instance rather than mutating, so a Gatling session that gets copied never shares — and
     * silently appends to — another user's buffer.
     */
    public static final class Buffer {

        static final Buffer EMPTY = new Buffer(Collections.emptyList(), 0);

        private final List<String> entries;
        private final int dropped;

        private Buffer(List<String> entries, int dropped) {
            this.entries = entries;
            this.dropped = dropped;
        }

        static Buffer of(Buffer buffer) {
            return buffer == null ? EMPTY : buffer;
        }

        Buffer with(String entry, int limit) {
            if (entry == null || limit <= 0) {
                return this;
            }
            List<String> next = new ArrayList<>(entries);
            next.add(entry);
            int drop = Math.max(0, next.size() - limit);
            // oldest out first, and remember how many were lost so the replay can say so. Copied
            // out of the subList view, which would otherwise keep the dropped text reachable.
            return new Buffer(new ArrayList<>(next.subList(drop, next.size())), dropped + drop);
        }

        /** The retained entries, oldest first. */
        public List<String> getEntries() {
            return Collections.unmodifiableList(entries);
        }

        /** How many entries were discarded to stay within the limit. */
        public int getDropped() {
            return dropped;
        }
    }
}
