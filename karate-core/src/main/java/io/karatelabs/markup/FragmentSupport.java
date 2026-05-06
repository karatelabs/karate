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

import org.thymeleaf.exceptions.TemplateProcessingException;

/**
 * Friendly error translation for the unsupported {@code th:fragment="name(params)"}
 * declared-signature shape.
 *
 * <p>karate-markup intentionally does not support param lists in {@code th:fragment}
 * declarations. The convention is plain {@code th:fragment="chip"} — callers pass
 * values via {@code th:with}, the fragment reads them as ordinary scope variables,
 * and unset names resolve to {@code null} (see Gotcha #21 + #22 in MARKUP_SKILL.md).
 *
 * <p>If a developer accidentally writes {@code th:fragment="chip(label, count)"},
 * Thymeleaf fires its strict signature-matching errors deep inside fragment
 * insertion processing — error messages like "fragment selection did not specify
 * any parameters" point at Thymeleaf concepts rather than the karate convention.
 * This helper detects those errors and re-throws with a karate-flavoured message
 * that tells the developer (or LLM) exactly what to change.
 */
final class FragmentSupport {

    private FragmentSupport() {
    }

    /**
     * If the given Thymeleaf exception originated from strict-signature matching,
     * throw a {@link TemplateProcessingException} whose message points at the
     * karate-markup convention. Otherwise return without throwing — caller
     * propagates the original.
     */
    static void translateSignatureError(TemplateProcessingException e) {
        String msg = e.getMessage();
        if (msg == null || !msg.contains("Cannot resolve fragment. Signature")) {
            return;
        }
        throw new TemplateProcessingException(
                "karate-markup does not support param lists in th:fragment signatures.\n"
                        + "  Change   th:fragment=\"name(p1, p2)\"   ➜   th:fragment=\"name\"\n"
                        + "  Pass values via th:with at the call site; the fragment reads them as\n"
                        + "  ordinary scope variables. Unset names resolve to null (no typeof guard\n"
                        + "  needed). See MARKUP_SKILL.md Gotcha #21.\n"
                        + "  Original Thymeleaf error: " + msg,
                e);
    }
}
