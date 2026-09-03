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
import io.karatelabs.gherkin.Scenario;
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
        if (!result.isFailed()) {
            if (mode != KarateLogReplay.ALL) {
                // FAILED mode retains nothing, so rendering this feature would only be thrown away —
                // and this is the healthy path of a load run, once per feature per virtual user
                return buffer;
            }
            return Buffer.of(buffer).with(render(featurePath, result), limit);
        }
        // one log event, not one per feature: under load several virtual users fail at once, and
        // separate events would interleave into a log where no block can be tied to its failure
        StringBuilder sb = new StringBuilder();
        if (mode == KarateLogReplay.ALL && buffer != null) {
            if (buffer.dropped > 0) {
                sb.append(buffer.dropped).append(" earlier karate feature log(s) dropped — logReplayLimit is ")
                        .append(limit).append('\n');
            }
            for (String entry : buffer.entries) {
                sb.append(entry).append('\n');
            }
        }
        String rendered = render(featurePath, result);
        if (rendered != null) {
            sb.append(rendered);
        }
        if (!sb.isEmpty()) {
            emit(sb.toString().stripTrailing());
        } else {
            // A replay that emits nothing at all looks like a broken feature. Two things cause it:
            // the feature genuinely produced no output (it failed before its first request), or the
            // report threshold filtered it — HTTP blocks and print enter the report buffer at INFO,
            // so a perf config with `report: 'warn'` empties it. We cannot tell which from here (the
            // per-scenario threshold is restored by the time this runs), so name both. Worded so it
            // does not read as "set report: 'info' to enable replay" — the default already captures.
            emit("karate log replay: " + featurePath + " captured no output to replay"
                    + " — the feature failed before its first request or print, or a"
                    + " 'configure logging = { report: ... }' above 'info' is filtering the capture"
                    + " (the default 'debug' keeps everything; replay needs report: 'info' or lower)");
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
     * so multiple features stay distinguishable in one log, with a header per scenario that produced
     * output so multiple scenarios in one feature stay distinguishable too. Called features are
     * rendered in place, under the step that called them, indented one level per call depth — the
     * HTML report descends into {@link StepResult#getCallResults()} the same way, and it is the only
     * place a callee's output lives (it is never folded into the caller's step log). ANSI colour
     * and the report-only body sentinels are stripped — this is written to a log file, not a
     * terminal. Returns null when the feature produced no output at all (no HTTP call, no
     * {@code print}, none in anything it called).
     */
    static String render(String featurePath, FeatureResult result) {
        StringBuilder sb = new StringBuilder();
        appendScenarios(sb, result, "");
        if (sb.isEmpty()) {
            return null;
        }
        String status = result.isFailed() ? "failed" : "passed";
        return ">>> karate: " + featurePath + " [" + status + "]\n"
                + sb + "<<< karate: " + featurePath;
    }

    private static void appendScenarios(StringBuilder sb, FeatureResult result, String indent) {
        for (ScenarioResult sr : result.getScenarioResults()) {
            StringBuilder steps = new StringBuilder();
            for (StepResult step : sr.getStepResults()) {
                String stepLog = step.getLog();
                if (stepLog != null && !stepLog.isBlank()) {
                    appendIndented(steps, Console.stripSentinels(Console.stripAnsi(stepLog)).stripTrailing(), indent);
                }
                List<FeatureResult> calls = step.getCallResults();
                if (calls == null) {
                    continue;
                }
                for (FeatureResult called : calls) {
                    StringBuilder nested = new StringBuilder();
                    appendScenarios(nested, called, indent + "  ");
                    if (nested.isEmpty()) {
                        continue;
                    }
                    String name = called.getDisplayName();
                    if (called.getLoopIndex() >= 0) {
                        name = name + " #" + called.getLoopIndex();
                    }
                    steps.append(indent).append(">>> call: ").append(name)
                            .append(called.isFailed() ? " [failed]" : " [passed]").append('\n')
                            .append(nested)
                            .append(indent).append("<<< call: ").append(name).append('\n');
                }
            }
            if (steps.isEmpty()) {
                continue;
            }
            Scenario scenario = sr.getScenario();
            if (scenario != null) {
                // the same [section:line] reference the HTML report and the KO message use, so an
                // outline example is told apart from its siblings and the line is one search away
                sb.append(indent).append("--- scenario ").append(scenario.getRefId()).append(' ')
                        .append(scenario.getName()).append(sr.isFailed() ? " [failed]" : " [passed]").append('\n');
            }
            sb.append(steps);
        }
    }

    private static void appendIndented(StringBuilder sb, String text, String indent) {
        if (indent.isEmpty()) {
            sb.append(text).append('\n');
            return;
        }
        for (String line : text.split("\n", -1)) {
            sb.append(indent).append(line).append('\n');
        }
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
