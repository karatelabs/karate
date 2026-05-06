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
package io.karatelabs.markup;

import java.util.Map;

/**
 * Implemented by request-scoped contexts that participate in K5 — the
 * {@code context.actions} POST-handler registry. The markup template engine
 * checks for this capability after each {@code ka:scope="global"} block to
 * decide whether to dispatch a handler for the inbound POST.
 *
 * <p>Plain templating contexts (no HTTP request) don't implement this
 * interface, so action dispatch is a silent no-op there.
 */
public interface ActionDispatchHost {

    /**
     * The (mutable) registry: action name → JS function. JS code populates
     * this via {@code context.actions['name'] = function(form){...}}.
     */
    Map<String, Object> getActions();

    /**
     * Whether this request's action handler has already been dispatched.
     * The framework calls this before each potential dispatch attempt so
     * the handler runs at most once per request.
     */
    boolean isActionDispatched();

    /**
     * Mark this request's action handler as dispatched. Called by the
     * template engine after a successful dispatch.
     */
    void markActionDispatched();

}
