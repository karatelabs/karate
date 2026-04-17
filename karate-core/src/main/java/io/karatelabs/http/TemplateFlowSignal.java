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
package io.karatelabs.http;

import io.karatelabs.js.FlowControlSignal;

/**
 * Signal thrown by {@link ServerMarkupContext#redirect(String)} and
 * {@link ServerMarkupContext#switchTemplate(String)} to cleanly abort
 * template rendering or API handler evaluation when a redirect or a
 * template switch is requested.
 * <p>
 * Treated as control flow (not an error) by the JS engine, the markup
 * engine's error logger, and the server request cycle. The actual
 * redirect/switch behavior is driven by state on {@link ServerMarkupContext}
 * ({@code hasRedirect}, {@code isSwitched}), which remains set when this
 * signal is caught upstream.
 */
public class TemplateFlowSignal extends RuntimeException implements FlowControlSignal {

    public enum Kind {REDIRECT, SWITCH}

    private final Kind kind;

    public TemplateFlowSignal(Kind kind) {
        super(kind.name().toLowerCase());
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        // control-flow signal — no stack trace needed
        return this;
    }
}
