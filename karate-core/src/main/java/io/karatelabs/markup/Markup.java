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

import io.karatelabs.common.ResourceNotFoundException;
import io.karatelabs.common.StringUtils;
import io.karatelabs.js.Engine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.IContext;
import org.thymeleaf.context.IEngineContext;
import org.thymeleaf.context.StandardEngineContextFactory;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.engine.TemplateData;
import org.thymeleaf.engine.TemplateManager;
import org.thymeleaf.exceptions.TemplateOutputException;
import org.thymeleaf.util.FastStringWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Markup {

    private static final Logger logger = LoggerFactory.getLogger(Markup.class);

    private static final Map<String, Object> IS_STRING = Collections.singletonMap("isString", true);

    private final StandardEngineContextFactory standardFactory;
    private final TemplateEngine wrapped;

    Markup(MarkupConfig config, IDialect... dialects) {
        standardFactory = new StandardEngineContextFactory();
        wrapped = new TemplateEngine();
        wrapped.setEngineContextFactory((IEngineConfiguration ec, TemplateData data, Map<String, Object> attrs, IContext context) -> {
            IEngineContext engineContext = standardFactory.createEngineContext(ec, data, attrs, context);
            return new MarkupTemplateContext(engineContext, config);
        });
        // the next line is a set which clears and replaces all existing / default
        wrapped.setDialect(new MarkupStandardDialect());
        for (IDialect dialect : dialects) {
            wrapped.addDialect(dialect);
        }
    }

    public String processPath(String path, Map<String, Object> vars) {
        return process(true, path, vars);
    }

    public String processString(String input, Map<String, Object> vars) {
        return process(false, input, vars);
    }

    private String process(boolean isPath, String content, Map<String, Object> vars) {
        return process(isPath, content, new MarkupEngineContext(vars));
    }

    private String process(boolean isPath, String content, IContext context) {
        Writer stringWriter = new FastStringWriter(100);
        process(isPath, content, context, stringWriter);
        return stringWriter.toString();
    }

    private void process(boolean isPath, String content, IContext context, Writer writer) {
        try {
            // the empty map (which becomes null) is used to signal an inline string template for HtmlTemplateResolver to handle
            TemplateSpec templateSpec = new TemplateSpec(content, isPath ? Collections.emptyMap() : IS_STRING);
            TemplateManager templateManager = wrapped.getConfiguration().getTemplateManager();
            templateManager.parseAndProcess(templateSpec, context, writer);
            try {
                writer.flush();
            } catch (IOException e) {
                throw new TemplateOutputException("error flushing output writer", content, -1, -1, e);
            }
        } catch (ResourceNotFoundException e) {
            throw e; // Let 404s bubble up without logging
        } catch (Exception e) {
            logTemplateError(isPath, content, e);
            throw new RuntimeException(e);
        }
    }

    private static final int CONTEXT_LINES = 2;

    private void logTemplateError(boolean isPath, String template, Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== TEMPLATE ERROR ==========\n");

        // Extract useful info from Thymeleaf exceptions
        Throwable current = e;
        String templateName = null;
        int line = -1;
        int col = -1;

        while (current != null) {
            // Check for Thymeleaf exceptions with position info
            if (current instanceof org.thymeleaf.exceptions.TemplateProcessingException tpe) {
                if (tpe.getTemplateName() != null) templateName = tpe.getTemplateName();
                if (tpe.getLine() != null) line = tpe.getLine();
                if (tpe.getCol() != null) col = tpe.getCol();
            }
            current = current.getCause();
        }

        // Show resource path for path-based templates
        if (isPath) {
            sb.append("Resource: ").append(template).append("\n");
        } else if (templateName != null && !templateName.equals(template)) {
            // For string templates, show file if different from content
            sb.append("File: ").append(templateName).append("\n");
        }

        // Show line/col and context lines around the error
        if (line > 0) {
            sb.append("Line: ").append(line);
            if (col > 0) sb.append(", Col: ").append(col);
            sb.append("\n");
            appendContextLines(sb, template, line);
        } else {
            // No line info available, show truncated template
            if (template.length() > 200) {
                sb.append("Template: ").append(template.substring(0, 200)).append("...\n");
            } else {
                sb.append("Template: ").append(template).append("\n");
            }
        }

        // Find the root cause message
        Throwable rootCause = e;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        sb.append("Error: ").append(rootCause.getMessage()).append("\n");
        sb.append("====================================\n");

        logger.error(sb.toString());

        // Also log full stack trace at trace level for troubleshooting
        if (logger.isTraceEnabled()) {
            logger.trace("Full stack trace: {}", StringUtils.throwableToString(e));
        }
    }

    private void appendContextLines(StringBuilder sb, String template, int errorLine) {
        String[] lines = template.split("\n");
        int start = Math.max(0, errorLine - CONTEXT_LINES - 1);
        int end = Math.min(lines.length, errorLine + CONTEXT_LINES);
        int lineNumWidth = String.valueOf(end).length();

        for (int i = start; i < end; i++) {
            int lineNum = i + 1;
            String marker = (lineNum == errorLine) ? ">" : " ";
            sb.append(String.format("%s %"+lineNumWidth+"d | %s%n", marker, lineNum, lines[i]));
        }
    }

    public static Markup init(Engine engine, String root) {
        return init(engine, new RootResourceResolver(root));
    }

    public static Markup init(Engine engine, ResourceResolver resolver) {
        MarkupConfig config = new MarkupConfig();
        config.setResolver(resolver);
        config.setEngineSupplier(() -> engine);
        return init(config);
    }

    public static Markup init(MarkupConfig config, IDialect... additionalDialects) {
        IDialect[] dialects = new IDialect[1 + additionalDialects.length];
        dialects[0] = new KaDialect(config);
        System.arraycopy(additionalDialects, 0, dialects, 1, additionalDialects.length);
        Markup markup = new Markup(config, dialects);
        markup.wrapped.setTemplateResolver(new HtmlTemplateResolver(config));
        return markup;
    }

    static class MarkupEngineContext implements IContext {

        private final Map<String, Object> vars;

        public MarkupEngineContext(Map<String, Object> vars) {
            if (vars == null) {
                vars = Collections.emptyMap();
            }
            this.vars = vars;
        }

        @Override
        public Locale getLocale() {
            return Locale.US;
        }

        @Override
        public boolean containsVariable(String name) {
            return vars.containsKey(name);
        }

        @Override
        public Set<String> getVariableNames() {
            return vars.keySet();
        }

        @Override
        public Object getVariable(String name) {
            return vars.get(name);
        }

    }

}
