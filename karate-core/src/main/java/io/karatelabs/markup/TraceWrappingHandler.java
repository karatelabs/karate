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

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.inline.IInliner;
import org.thymeleaf.model.AttributeValueQuotes;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.engine.TemplateData;

/**
 * Devmode-only wrapper around Thymeleaf's {@link IElementTagStructureHandler}.
 * Used by {@link KaInsertProcessor} / {@link KaReplaceProcessor} when
 * {@code devTrace} is enabled to inject a synthetic {@code <ka-trace>}
 * wrapper element around the resolved fragment IModel.
 *
 * <p>The synthetic element survives the structure-handler pipeline (no
 * attribute reset can clobber it because it's part of the model passed to
 * {@code setBody} / {@code replaceWith}, not a structure-handler op). The
 * companion {@link FragmentTraceHandler} post-processor sees the wrapper
 * downstream, emits {@code <!-- ka:fragment-begin/end -->} comments, and
 * suppresses the wrapper tags so the rendered HTML stays clean.
 *
 * <p>Why a wrapper element instead of a marker attribute on the host: the
 * earlier attribute approach failed for {@code th:replace} because the host
 * disappears with the marker. Wrapping the fragment IModel itself works
 * uniformly for both insert (host kept, body wrapped) and replace (host
 * gone, fragment wrapped).
 *
 * <p>All non-{@code setBody}/{@code replaceWith} methods delegate verbatim.
 */
class TraceWrappingHandler implements IElementTagStructureHandler {

    private final IElementTagStructureHandler delegate;
    private final ITemplateContext context;
    private final String payload;

    TraceWrappingHandler(IElementTagStructureHandler delegate, ITemplateContext context, String payload) {
        this.delegate = delegate;
        this.context = context;
        this.payload = payload;
    }

    private IModel wrap(IModel inner) {
        IModelFactory f = context.getModelFactory();
        IModel out = f.createModel();
        out.add(f.createOpenElementTag(FragmentTrace.WRAPPER_TAG,
                FragmentTrace.MARKER_ATTR, payload));
        out.addModel(inner);
        out.add(f.createCloseElementTag(FragmentTrace.WRAPPER_TAG));
        return out;
    }

    // -------- intercepted --------

    @Override
    public void setBody(IModel model, boolean processable) {
        delegate.setBody(wrap(model), processable);
    }

    @Override
    public void replaceWith(IModel model, boolean processable) {
        delegate.replaceWith(wrap(model), processable);
    }

    // -------- pure delegation --------

    @Override public void reset() { delegate.reset(); }
    @Override public void setLocalVariable(String name, Object value) { delegate.setLocalVariable(name, value); }
    @Override public void removeLocalVariable(String name) { delegate.removeLocalVariable(name); }
    @Override public void setAttribute(String name, String value) { delegate.setAttribute(name, value); }
    @Override public void setAttribute(String name, String value, AttributeValueQuotes quotes) { delegate.setAttribute(name, value, quotes); }
    @Override public void replaceAttribute(AttributeName old, String name, String value) { delegate.replaceAttribute(old, name, value); }
    @Override public void replaceAttribute(AttributeName old, String name, String value, AttributeValueQuotes quotes) { delegate.replaceAttribute(old, name, value, quotes); }
    @Override public void removeAttribute(String name) { delegate.removeAttribute(name); }
    @Override public void removeAttribute(String prefix, String name) { delegate.removeAttribute(prefix, name); }
    @Override public void removeAttribute(AttributeName name) { delegate.removeAttribute(name); }
    @Override public void setSelectionTarget(Object target) { delegate.setSelectionTarget(target); }
    @Override public void setInliner(IInliner inliner) { delegate.setInliner(inliner); }
    @Override public void setTemplateData(TemplateData data) { delegate.setTemplateData(data); }
    @Override public void setBody(CharSequence text, boolean processable) { delegate.setBody(text, processable); }
    @Override public void insertBefore(IModel model) { delegate.insertBefore(model); }
    @Override public void insertImmediatelyAfter(IModel model, boolean processable) { delegate.insertImmediatelyAfter(model, processable); }
    @Override public void replaceWith(CharSequence text, boolean processable) { delegate.replaceWith(text, processable); }
    @Override public void removeElement() { delegate.removeElement(); }
    @Override public void removeTags() { delegate.removeTags(); }
    @Override public void removeBody() { delegate.removeBody(); }
    @Override public void removeAllButFirstChild() { delegate.removeAllButFirstChild(); }
    @Override public void iterateElement(String iterVar, String iterStatusVar, Object iteratedObject) { delegate.iterateElement(iterVar, iterStatusVar, iteratedObject); }

}
