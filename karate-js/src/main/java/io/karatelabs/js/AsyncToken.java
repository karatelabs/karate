/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
package io.karatelabs.js;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One unit of outstanding async work in an {@link AsyncScope}. Tokens are
 * <b>individual idempotent handles</b>, never bulk counter decrements: each
 * carries its own {@code LIVE → RELEASED} CAS, so a double release is a
 * verifiable no-op rather than a silently negative count.
 * <p>
 * Owners, one token each: a live timer, a live activation, a queued job, and an
 * armed-but-unsettled external {@code CompletionStage} subscription. Successor
 * work is always acquired and enqueued <i>before</i> the token that permitted
 * it is released — see {@link AsyncScope#publishSuccessor}.
 */
final class AsyncToken {

    private final AsyncScope scope;
    private final AtomicBoolean live = new AtomicBoolean(true);
    final String kind;

    AsyncToken(AsyncScope scope, String kind) {
        this.scope = scope;
        this.kind = kind;
    }

    /** Idempotent: only the CAS winner decrements the scope's live count. */
    boolean release() {
        if (live.compareAndSet(true, false)) {
            scope.decrement();
            return true;
        }
        return false;
    }

    boolean isLive() {
        return live.get();
    }

    @Override
    public String toString() {
        return "token[" + kind + (live.get() ? ",live]" : ",released]");
    }

}
