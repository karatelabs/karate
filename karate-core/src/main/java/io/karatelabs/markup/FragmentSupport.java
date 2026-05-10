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
 * <p>Two paths surface the convention:
 * <ul>
 *   <li>{@link KaFragmentProcessor} is registered in place of Thymeleaf's
 *       {@code StandardFragmentTagProcessor} via the instance-of patching loop in
 *       {@link MarkupStandardDialect#getProcessors}. It validates the
 *       {@code th:fragment} attribute value at evaluation time and throws via
 *       {@link #signatureMessage} when it contains a {@code (}.</li>
 *   <li>{@link #translateSignatureError} stays as a backstop for any Thymeleaf
 *       strict-matching error that slips through (e.g. third-party fragments
 *       loaded outside the Karate dialect path).</li>
 * </ul>
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
        throw new TemplateProcessingException(signatureMessage(null, null, msg), e);
    }

    /**
     * Build the karate-flavoured "param lists not supported" message. Used by
     * both the proactive {@link KaFragmentProcessor} (which passes the offending
     * attribute value) and {@link #translateSignatureError} (which only has the
     * Thymeleaf message). Either argument may be {@code null}.
     */
    static String signatureMessage(String offendingValue, String resourceDescription, String thymeleafMsg) {
        StringBuilder sb = new StringBuilder();
        sb.append("karate-markup does not support param lists in th:fragment signatures.\n");
        if (offendingValue != null) {
            sb.append("  Found    th:fragment=\"").append(offendingValue).append("\"");
            if (resourceDescription != null) {
                sb.append("   in ").append(resourceDescription);
            }
            sb.append("\n");
        }
        sb.append("  Change   th:fragment=\"name(p1, p2)\"   ➜   th:fragment=\"name\"\n");
        sb.append("  Pass values via th:with at the call site; the fragment reads them as\n");
        sb.append("  ordinary scope variables. Unset names resolve to null (no typeof guard\n");
        sb.append("  needed). See MARKUP_SKILL.md Gotcha #21.");
        if (thymeleafMsg != null) {
            sb.append("\n  Original Thymeleaf error: ").append(thymeleafMsg);
        }
        return sb.toString();
    }
}
