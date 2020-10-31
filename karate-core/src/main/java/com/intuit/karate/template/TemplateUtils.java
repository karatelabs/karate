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

import com.intuit.karate.server.ServerConfig;
import com.intuit.karate.server.RequestCycle;
import java.util.Map;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;
import org.thymeleaf.context.IEngineContext;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.context.StandardEngineContextFactory;
import org.thymeleaf.engine.TemplateData;
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
    
    private static final String SCRIPT_TAGS = "<script src=\"https://unpkg.com/htmx.org@0.0.8\"></script>\n"
            + "<script>\n"
            + "document.addEventListener(\"DOMContentLoaded\", function (evt) {\n"
            + "  document.body.addEventListener('redirect', function (e) {\n"
            + "    var url = e.detail.url;\n"
            + "    alert('session expired, will redirect to: ' + url);\n"
            + "    window.location = url;\n"
            + "  });\n"
            + "});\n"
            + "htmx.on(\"htmx:afterSettle\", function (evt) {\n"
            + "  var el = document.getElementById('kjs_afterSettle');\n"
            + "  if (el) {\n"
            + "    Function('\"use strict\";' + el.innerHTML)();\n"
            + "    el.removeAttribute('id');\n"
            + "  }\n"
            + "});"
            + "</script>";
    
    public static IModel generateScriptTags(ITemplateContext ctx) {
        IModelFactory modelFactory = ctx.getModelFactory();
        return modelFactory.parse(ctx.getTemplateData(), SCRIPT_TAGS);
    }
    
    public static boolean hasAncestorElement(ITemplateContext ctx, String name) {
        for (IProcessableElementTag tag : ctx.getElementStack()) {
            if (tag.getElementCompleteName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
    
    public static ITemplateEngine createEngine(ServerConfig config) {
        TemplateEngine engine = new TemplateEngine();
        StandardEngineContextFactory standardFactory = new StandardEngineContextFactory();
        engine.setEngineContextFactory((IEngineConfiguration ec, TemplateData data, Map<String, Object> attrs, IContext context) -> {
            IEngineContext engineContext = standardFactory.createEngineContext(ec, data, attrs, context);
            RequestCycle rc = RequestCycle.get();
            TemplateEngineContext tec = new TemplateEngineContext(engineContext, rc);
            rc.setEngineContext(tec);
            return tec;
        });
        engine.setTemplateResolver(new TemplateResolver(config));
        // the next line is a set which clears and replaces all existing / default
        engine.setDialect(new KarateStandardDialect());
        engine.addDialect(new KarateDialect(config));
        return new KarateTemplateEngine(engine);
    }
    
}
