/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.template;

import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.graal.JsValue;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.context.IEngineContext;
import org.thymeleaf.context.IdentifierSequences;
import org.thymeleaf.engine.TemplateData;
import org.thymeleaf.expression.IExpressionObjects;
import org.thymeleaf.inline.IInliner;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.templatemode.TemplateMode;

/**
 *
 * @author pthomas3
 */
public class KarateEngineContext implements IEngineContext {
    
    private static final Logger logger = LoggerFactory.getLogger(KarateEngineContext.class);
    
    private static final ThreadLocal<KarateEngineContext> THREAD_LOCAL = new ThreadLocal();
    
    private final IEngineContext wrapped;
    private final JsEngine jsEngine;
    private final Map<String, Object> context = new HashMap();    
    private boolean redirect;    
    
    public static KarateEngineContext initThreadLocal(IEngineContext wrapped, JsEngine engine) {
        KarateEngineContext tec = new KarateEngineContext(wrapped, engine);
        THREAD_LOCAL.set(tec);
        return tec;
    }
    
    private KarateEngineContext(IEngineContext wrapped, JsEngine jsEngine) {
        this.wrapped = wrapped;
        this.jsEngine = jsEngine;
        jsEngine.put("_", context);
    }
    
    public static KarateEngineContext get() {
        return THREAD_LOCAL.get();
    }
    
    public static void set(KarateEngineContext kec) {
        THREAD_LOCAL.set(kec);
    }
    
    public void setRedirect(boolean redirect) {
        this.redirect = redirect;
    }
    
    public boolean isRedirect() {
        return redirect;
    }    
    
    public JsValue evalGlobal(String src) {
        getVariableNames().forEach(name -> jsEngine.put(name, getVariable(name)));
        try {
            return jsEngine.eval(src);
        } catch (Exception e) {
            throw JsEngine.fromJsEvalException(src, e, null);
        }
    }
    
    public JsValue evalLocal(String src, boolean returnValue) {
        try {
            Value value = jsEngine.evalWith(getVariableNames(), this::getVariable, src, returnValue);
            return new JsValue(value);
        } catch (Exception e) {
            throw JsEngine.fromJsEvalException(src, e, null);
        }
    }
    
    public void setLocal(String name, Object value) {
        context.put(name, value);
    }
    
    @Override
    public void increaseLevel() {
        if (!context.isEmpty()) {
            setVariables(context);
            context.clear();
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
        return wrapped.getVariable(name);
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
