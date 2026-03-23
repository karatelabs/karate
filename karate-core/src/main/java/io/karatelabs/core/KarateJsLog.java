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
package io.karatelabs.core;

import io.karatelabs.common.StringUtils;
import io.karatelabs.common.Xml;
import io.karatelabs.js.JavaInvokable;
import io.karatelabs.js.SimpleObject;
import io.karatelabs.output.LogContext;
import io.karatelabs.output.LogLevel;
import org.w3c.dom.Node;

import java.util.List;
import java.util.Map;

/**
 * KarateJsLog - Provides karate.logger.trace/debug/info/warn/error methods.
 * Implements SimpleObject for clean JS interop: karate.logger.debug('msg')
 * Logs go to LogContext with level filtering for report capture,
 * and cascade to SLF4J via karate.scenario category.
 */
class KarateJsLog implements SimpleObject {

    private static final LogContext.LogWriter log = LogContext.with(LogContext.SCENARIO_LOGGER);

    @Override
    public Object jsGet(String key) {
        return switch (key) {
            case "trace" -> logMethod(LogLevel.TRACE);
            case "debug" -> logMethod(LogLevel.DEBUG);
            case "info" -> logMethod(LogLevel.INFO);
            case "warn" -> logMethod(LogLevel.WARN);
            case "error" -> logMethod(LogLevel.ERROR);
            default -> null;
        };
    }

    private JavaInvokable logMethod(LogLevel level) {
        return args -> {
            // Check if level is enabled before formatting
            if (!level.isEnabled(LogContext.getLogLevel())) {
                return null; // Filtered
            }
            StringBuilder sb = new StringBuilder();
            // Add level prefix for TRACE/DEBUG/WARN/ERROR (distinguish from INFO)
            if (level != LogLevel.INFO) {
                sb.append("[").append(level).append("] ");
            }
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(" ");
                Object val = args[i];
                if (val instanceof Node) {
                    sb.append(Xml.toString((Node) val, true));
                } else if (val instanceof Map || val instanceof List) {
                    sb.append(StringUtils.formatJson(val));
                } else {
                    sb.append(val);
                }
            }
            // Log via LogWriter (captures to buffer AND cascades to SLF4J)
            switch (level) {
                case TRACE -> log.trace(sb.toString());
                case DEBUG -> log.debug(sb.toString());
                case INFO -> log.info(sb.toString());
                case WARN -> log.warn(sb.toString());
                case ERROR -> log.error(sb.toString());
            }
            return null;
        };
    }
}
