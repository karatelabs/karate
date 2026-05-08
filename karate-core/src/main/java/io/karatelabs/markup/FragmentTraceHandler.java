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

import org.thymeleaf.engine.AbstractTemplateHandler;
import org.thymeleaf.model.ICloseElementTag;
import org.thymeleaf.model.IComment;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.model.IText;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Devmode post-processor that emits paired {@code <!-- ka:fragment-begin -->}
 * / {@code <!-- ka:fragment-end -->} comments around every fragment resolution
 * and suppresses the synthetic {@code <ka-trace>} wrapper element that
 * {@link TraceWrappingHandler} injected at processor time.
 *
 * <p>For each open {@code <ka-trace>} tag:
 * <ol>
 *   <li>Emit a begin comment with kind, fragment expression, depth, and the
 *       raw {@code th:with} payload from the call site.</li>
 *   <li>SUPPRESS the open tag (don't forward it).</li>
 *   <li>Push a frame onto a depth stack.</li>
 * </ol>
 * For each close {@code <ka-trace>} tag: pop the frame, suppress the close
 * tag, emit the end comment.
 *
 * <p>All other tags (host elements, fragment content) pass through unchanged.
 * Untraced templates pay only an O(1) tag-name comparison per element.
 *
 * <p>The handler is registered ONLY when {@code devTrace + devMode} is
 * enabled (see {@link MarkupStandardDialect#getPostProcessors}); zero
 * production overhead.
 */
public class FragmentTraceHandler extends AbstractTemplateHandler {

    private static final class Frame {
        final boolean traced;
        final String payload;

        Frame(boolean traced, String payload) {
            this.traced = traced;
            this.payload = payload;
        }
    }

    private final Deque<Frame> stack = new ArrayDeque<>();
    private int depth = 0;

    @Override
    public void handleOpenElement(IOpenElementTag tag) {
        if (!FragmentTrace.WRAPPER_TAG.equals(tag.getElementCompleteName())) {
            stack.push(new Frame(false, null));
            super.handleOpenElement(tag);
            return;
        }
        String payload = tag.getAttributeValue(FragmentTrace.MARKER_ATTR);
        emitComment(formatBegin(payload, depth));
        // Suppress the synthetic wrapper open tag entirely.
        stack.push(new Frame(true, payload));
        depth++;
    }

    @Override
    public void handleCloseElement(ICloseElementTag tag) {
        if (!FragmentTrace.WRAPPER_TAG.equals(tag.getElementCompleteName())) {
            super.handleCloseElement(tag);
            // Pop only if there's a matching frame — defensive against
            // un-balanced close events from auto-closing HTML5 parser quirks.
            if (!stack.isEmpty()) stack.pop();
            return;
        }
        // Suppress the synthetic wrapper close tag.
        Frame frame = stack.isEmpty() ? null : stack.pop();
        if (frame != null && frame.traced) {
            depth--;
            emitComment(formatEnd(frame.payload, depth));
        }
    }

    @Override
    public void handleStandaloneElement(IStandaloneElementTag tag) {
        // The wrapper element is always emitted as a paired open/close, so
        // there should be no standalone <ka-trace> in normal flow. Pass
        // through anything else unchanged.
        super.handleStandaloneElement(tag);
    }

    private void emitComment(String text) {
        IModelFactory factory = getContext().getModelFactory();
        IComment comment = factory.createComment(text);
        super.handleComment(comment);
        // Trailing newline so view-source doesn't smear onto one mega-line.
        IText nl = factory.createText("\n");
        super.handleText(nl);
    }

    private static String formatBegin(String payload, int depth) {
        return formatComment("ka:fragment-begin", payload, depth);
    }

    private static String formatEnd(String payload, int depth) {
        return formatComment("ka:fragment-end", payload, depth);
    }

    private static String formatComment(String label, String payload, int depth) {
        // payload format: "kind|expr" or "kind|expr|with-text"
        String[] parts = payload == null ? new String[0] : payload.split("\\|", 3);
        String kind = parts.length > 0 ? parts[0] : "";
        String expr = parts.length > 1 ? parts[1] : "";
        String with = parts.length > 2 ? parts[2] : null;
        StringBuilder sb = new StringBuilder(" ").append(label);
        sb.append(" (").append(kind).append(") ").append(expr);
        sb.append(" depth=").append(depth);
        if (with != null && !with.isEmpty()) {
            sb.append(" with={").append(with).append("}");
        }
        sb.append(' ');
        return sb.toString();
    }

}
