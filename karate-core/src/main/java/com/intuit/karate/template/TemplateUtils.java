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
import com.intuit.karate.http.RequestCycle;
import com.intuit.karate.http.ServerConfig;
import com.intuit.karate.http.ServerContext;
import com.intuit.karate.resource.ResourceResolver;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IProcessableElementTag;

/**
 *
 * @author pthomas3
 */
public class TemplateUtils {

    private TemplateUtils() {
        // only static methods
    }

    private static KarateTemplateEngine initEngine(JsEngine je, ResourceResolver resolver, boolean server) {
        ServerConfig config = new ServerConfig(resolver);
        ServerContext sc = new ServerContext(config, null);
        je.put(RequestCycle.CONTEXT, sc); // TODO improve
        return new KarateTemplateEngine(() -> je, server ? new KarateServerDialect(config) : new KarateScriptDialect(config));
    }

    public static KarateTemplateEngine forServer(ServerConfig config) {
        KarateTemplateEngine engine = new KarateTemplateEngine(() -> RequestCycle.get().getEngine(), new KarateServerDialect(config));
        engine.setTemplateResolver(new ServerHtmlTemplateResolver(config.getResourceResolver(), config.isDevMode()));
        return engine;
    }

    public static KarateTemplateEngine forStrings(JsEngine je, ResourceResolver resourceResolver) {
        KarateTemplateEngine engine = initEngine(je, resourceResolver, false);
        engine.setTemplateResolver(StringHtmlTemplateResolver.INSTANCE);
        engine.addTemplateResolver(new ResourceHtmlTemplateResolver(resourceResolver));
        return engine;
    }

    public static KarateTemplateEngine forResourceResolver(JsEngine je, ResourceResolver resourceResolver) {
        KarateTemplateEngine engine = initEngine(je, resourceResolver, false);
        engine.setTemplateResolver(new ResourceHtmlTemplateResolver(resourceResolver));
        return engine;
    }

    public static KarateTemplateEngine forServerResolver(JsEngine je, ResourceResolver resourceResolver, boolean devMode) {
        KarateTemplateEngine engine = initEngine(je, resourceResolver, true);
        engine.setTemplateResolver(new ServerHtmlTemplateResolver(resourceResolver, devMode));
        return engine;
    }

    public static KarateTemplateEngine forResourceRoot(JsEngine je, String root) {
        return forResourceResolver(je, new ResourceResolver(root));
    }

    public static String renderServerPath(String path, JsEngine je, ResourceResolver resourceResolver, boolean devMode) {
        KarateEngineContext old = KarateEngineContext.get();
        try {
            KarateTemplateEngine kte = forServerResolver(je, resourceResolver, devMode);
            return kte.process(path);
        } finally {
            KarateEngineContext.set(old);
        }
    }

    public static String renderHtmlString(String html, JsEngine je, ResourceResolver resourceResolver) {
        KarateEngineContext old = KarateEngineContext.get();
        try {
            KarateTemplateEngine kte = forStrings(je, resourceResolver);
            return kte.process(html);
        } finally {
            KarateEngineContext.set(old);
        }
    }

}
