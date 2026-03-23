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

import io.karatelabs.js.Engine;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.context.IEngineContext;
import org.thymeleaf.context.IdentifierSequences;
import org.thymeleaf.engine.IterationStatusVar;
import org.thymeleaf.engine.TemplateData;
import org.thymeleaf.expression.IExpressionObjects;
import org.thymeleaf.inline.IInliner;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.*;

public class MarkupTemplateContext implements IEngineContext {

    final IEngineContext wrapped;
    private final Engine engine;
    private final Map<String, Object> vars = new HashMap<>();

    MarkupTemplateContext(IEngineContext wrapped, MarkupConfig config) {
        this.wrapped = wrapped;
        this.engine = config.getEngineSupplier().get();
        this.engine.put("_", vars);
        // Use existing MarkupContext from template variables if present (e.g., ServerMarkupContext in server mode)
        // Otherwise create a SimpleMarkupContext for plain templating mode
        // Note: In server mode, the engine is shared with ServerRequestCycle (via ThreadLocal supplier)
        // and session binding is already managed by ServerRequestCycle.bindSession()
        Object existingContext = wrapped.getVariable("context");
        if (existingContext instanceof MarkupContext) {
            this.engine.put("context", existingContext);
        } else {
            this.engine.put("context", new SimpleMarkupContext(this, config.getResolver()));
        }
    }

    void evalGlobal(String src) {
        getVariableNames().forEach(name -> engine.put(name, getVariable(name)));
        // Always sync session from template vars (may be null for new requests)
        // This ensures the engine doesn't carry stale session from previous requests
        Object sessionFromVars = wrapped.getVariable("session");
        engine.put("session", sessionFromVars);
        engine.eval(src);
        // After script execution, sync session if context.init() was called
        syncSessionVariable();
    }

    /**
     * Sync the 'session' variable if context.init() created a new session.
     * This allows templates to use 'session' directly after calling context.init().
     */
    private void syncSessionVariable() {
        Object contextObj = engine.get("context");
        if (contextObj instanceof MarkupContext mc) {
            Object session = mc.getContextSession();
            if (session != null && engine.get("session") == null) {
                engine.put("session", session);
                wrapped.setVariable("session", session);
            }
        }
    }

    public Object evalLocalAsObject(String src) {
        String temp;
        if (src.startsWith("${")) {
            temp = "`" + src + "`";
        } else {
            temp = "({" + src + "})";
        }
        return evalLocal(temp);
    }

    public Object evalLocal(String src) {
        Map<String, Object> localVars = new HashMap<>();
        for (String name : getVariableNames()) {
            localVars.put(name, getVariable(name));
        }
        localVars.put("_", vars);
        return engine.evalWith(src, localVars);
    }

    void setLocal(String name, Object value) {
        vars.put(name, value);
    }

    @Override
    public void increaseLevel() {
        if (!vars.isEmpty()) {
            wrapped.setVariables(vars);
            vars.clear();
        }
        wrapped.increaseLevel();
    }

    @Override
    public void setVariable(String name, Object value) {
        wrapped.setVariable(name, value);
    }

    @Override
    public void setVariables(Map<String, Object> variables) {
        wrapped.setVariables(variables);
    }

    @Override
    public void removeVariable(String name) {
        wrapped.removeVariable(name);
    }

    @Override
    public void setTemplateData(TemplateData template) {
        wrapped.setTemplateData(template);
    }

    @Override
    public void decreaseLevel() {
        wrapped.decreaseLevel();
    }

    @Override
    public boolean containsVariable(String name) {
        return wrapped.containsVariable(name);
    }

    @Override
    public Set<String> getVariableNames() {
        return wrapped.getVariableNames();
    }

    @Override
    public Object getVariable(String name) {
        Object value = wrapped.getVariable(name);
        // Convert Thymeleaf's IterationStatusVar to a JS-friendly Map
        // This enables iteration status properties like iter.first, iter.last, iter.index
        if (value instanceof IterationStatusVar status) {
            Map<String, Object> statusMap = new LinkedHashMap<>();
            statusMap.put("index", status.getIndex());
            statusMap.put("count", status.getCount());
            statusMap.put("size", status.getSize());
            statusMap.put("current", status.getCurrent());
            statusMap.put("even", status.isEven());
            statusMap.put("odd", status.isOdd());
            statusMap.put("first", status.isFirst());
            statusMap.put("last", status.isLast());
            return statusMap;
        }
        return value;
    }

    @Override
    public boolean isVariableLocal(String name) {
        return wrapped.isVariableLocal(name);
    }

    @Override
    public void setSelectionTarget(Object selectionTarget) {
        wrapped.setSelectionTarget(selectionTarget);
    }

    @Override
    public void setInliner(IInliner inliner) {
        wrapped.setInliner(inliner);
    }

    @Override
    public void setElementTag(IProcessableElementTag elementTag) {
        wrapped.setElementTag(elementTag);
    }

    @Override
    public List<IProcessableElementTag> getElementStackAbove(int contextLevel) {
        return wrapped.getElementStackAbove(contextLevel);
    }

    @Override
    public int level() {
        return wrapped.level();
    }

    @Override
    public TemplateData getTemplateData() {
        return wrapped.getTemplateData();
    }

    @Override
    public TemplateMode getTemplateMode() {
        return wrapped.getTemplateMode();
    }

    @Override
    public List<TemplateData> getTemplateStack() {
        return wrapped.getTemplateStack();
    }

    @Override
    public List<IProcessableElementTag> getElementStack() {
        return wrapped.getElementStack();
    }

    @Override
    public Map<String, Object> getTemplateResolutionAttributes() {
        return wrapped.getTemplateResolutionAttributes();
    }

    @Override
    public IModelFactory getModelFactory() {
        return wrapped.getModelFactory();
    }

    @Override
    public boolean hasSelectionTarget() {
        return wrapped.hasSelectionTarget();
    }

    @Override
    public Object getSelectionTarget() {
        return wrapped.getSelectionTarget();
    }

    @Override
    public IInliner getInliner() {
        return wrapped.getInliner();
    }

    @Override
    public String getMessage(Class<?> origin, String key, Object[] messageParameters, boolean useAbsent) {
        return wrapped.getMessage(origin, key, messageParameters, useAbsent);
    }

    @Override
    public String buildLink(String base, Map<String, Object> parameters) {
        return wrapped.buildLink(base, parameters);
    }

    @Override
    public IdentifierSequences getIdentifierSequences() {
        return wrapped.getIdentifierSequences();
    }

    @Override
    public IEngineConfiguration getConfiguration() {
        return wrapped.getConfiguration();
    }

    @Override
    public IExpressionObjects getExpressionObjects() {
        return wrapped.getExpressionObjects();
    }

    @Override
    public Locale getLocale() {
        return wrapped.getLocale();
    }

}
