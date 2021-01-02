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

import com.intuit.karate.http.RedirectException;
import com.intuit.karate.http.RequestCycle;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.IThrottledTemplateProcessor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.IContext;
import org.thymeleaf.context.IEngineContext;
import org.thymeleaf.context.StandardEngineContextFactory;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.engine.TemplateData;
import org.thymeleaf.engine.TemplateManager;
import org.thymeleaf.exceptions.TemplateOutputException;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.util.FastStringWriter;

/**
 *
 * @author pthomas3
 */
public class KarateTemplateEngine implements ITemplateEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(KarateTemplateEngine.class);
    
    private final StandardEngineContextFactory standardFactory;
    private final TemplateEngine wrapped;
    
    public KarateTemplateEngine(Supplier<RequestCycle> requestCycleFactory, IDialect... dialects) {
        standardFactory = new StandardEngineContextFactory();
        wrapped = new TemplateEngine();
        wrapped.setEngineContextFactory((IEngineConfiguration ec, TemplateData data, Map<String, Object> attrs, IContext context) -> {
            IEngineContext engineContext = standardFactory.createEngineContext(ec, data, attrs, context);
            return new TemplateEngineContext(engineContext, requestCycleFactory.get());
        });
        // the next line is a set which clears and replaces all existing / default
        wrapped.setDialect(new KarateStandardDialect());        
        for (IDialect dialect : dialects) {
            wrapped.addDialect(dialect);
        }
    }
    
    public void setTemplateResolver(ITemplateResolver templateResolver) {
        wrapped.setTemplateResolver(templateResolver);
    }
    
    @Override
    public IEngineConfiguration getConfiguration() {
        return wrapped.getConfiguration();
    }
    
    public String process(String template) {
        return process(template, TemplateContext.LOCALE_US);
    }
    
    @Override
    public String process(String template, IContext context) {
        TemplateSpec templateSpec = new TemplateSpec(template, TemplateMode.HTML);
        Writer stringWriter = new FastStringWriter(100);
        process(templateSpec, context, stringWriter);
        return stringWriter.toString();
    }
    
    @Override
    public String process(String template, Set<String> templateSelectors, IContext context) {
        return wrapped.process(template, templateSelectors, context);
    }
    
    @Override
    public String process(TemplateSpec templateSpec, IContext context) {
        return wrapped.process(templateSpec, context);
    }
    
    @Override
    public void process(String template, IContext context, Writer writer) {
        wrapped.process(template, context, writer);
    }
    
    @Override
    public void process(String template, Set<String> templateSelectors, IContext context, Writer writer) {
        wrapped.process(template, templateSelectors, context, writer);
    }
    
    @Override
    public void process(TemplateSpec templateSpec, IContext context, Writer writer) {
        try {
            TemplateManager templateManager = getConfiguration().getTemplateManager();
            templateManager.parseAndProcess(templateSpec, context, writer);
            try {
                writer.flush();
            } catch (IOException e) {
                throw new TemplateOutputException("error flushing output writer", templateSpec.getTemplate(), -1, -1, e);
            }
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            while (cause != null && !(cause instanceof RedirectException)) {
                cause = cause.getCause();
            }
            if (cause instanceof RedirectException) {
                throw (RedirectException) cause;
            }
            logger.error("{}", e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    public IThrottledTemplateProcessor processThrottled(String template, IContext context) {
        return wrapped.processThrottled(template, context);
    }
    
    @Override
    public IThrottledTemplateProcessor processThrottled(String template, Set<String> templateSelectors, IContext context) {
        return wrapped.processThrottled(template, templateSelectors, context);
    }
    
    @Override
    public IThrottledTemplateProcessor processThrottled(TemplateSpec templateSpec, IContext context) {
        return wrapped.processThrottled(templateSpec, context);
    }
    
}
