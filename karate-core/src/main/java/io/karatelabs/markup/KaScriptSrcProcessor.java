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

import io.karatelabs.common.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Resolves URL attributes on elements decorated with `ka:` markup — both
 * `<script src>` (carries `ka:scope` for inline JS evaluation, plus `ka:nocache`
 * for cache-busting) and `<link href>` (carries `ka:nocache` for stylesheets).
 * Two instances are registered in {@link KaDialect}: one for the `src` attribute,
 * one for `href`. The `ka:scope` branch only ever fires on `<script>`; the
 * `ka:nocache` branch is path-attribute-agnostic.
 */
class KaScriptSrcProcessor extends AbstractAttributeTagProcessor {

    private static final Logger logger = LoggerFactory.getLogger(KaScriptSrcProcessor.class);

    private final String hostContextPath;
    private final ResourceResolver resolver;
    private final boolean serverMode;
    private final String urlAttr;

    KaScriptSrcProcessor(String dialectPrefix, ResourceResolver resolver, String hostContextPath, boolean serverMode, String urlAttr) {
        super(TemplateMode.HTML, dialectPrefix, null, false, urlAttr, false, 1000, false);
        this.resolver = resolver;
        this.hostContextPath = hostContextPath;
        this.serverMode = serverMode;
        this.urlAttr = urlAttr;
    }

    @Override
    protected void doProcess(ITemplateContext ctx, IProcessableElementTag tag, AttributeName an, String urlValue, IElementTagStructureHandler sh) {
        String scope = tag.getAttributeValue(getDialectPrefix(), KaScriptProcessor.SCOPE);
        String noCache = tag.getAttributeValue(getDialectPrefix(), KaScriptProcessor.NOCACHE);

        // For plain HTML mode (not server), skip resource resolution for static URL attributes
        if (!serverMode && scope == null) {
            if (noCache != null) {
                sh.removeAttribute(getDialectPrefix(), KaScriptProcessor.NOCACHE);
            }
            return;
        }

        // Server mode or scope evaluation needed - resolve the resource
        if (scope == null && noCache == null) {
            // Plain URL attribute - just update with context path if needed
            if (hostContextPath != null) {
                urlValue = hostContextPath + urlValue;
                sh.setAttribute(urlAttr, urlValue);
            }
            return;
        }

        Resource srcResource = resolver.resolve(urlValue, null);
        if (scope == null) { // no js evaluation, we just update the html for nocache
            if (hostContextPath != null) {
                urlValue = hostContextPath + urlValue;
            }
            if (noCache != null) {
                try {
                    urlValue = urlValue + "?ts=" + srcResource.getLastModified();
                } catch (Exception e) {
                    logger.warn("nocache failed: {}", e.getMessage());
                }
                sh.removeAttribute(getDialectPrefix(), KaScriptProcessor.NOCACHE);
            }
            sh.setAttribute(urlAttr, urlValue);
        } else { // karate js evaluation
            String js = srcResource.getText();
            MarkupTemplateContext kec = (MarkupTemplateContext) ctx;
            if (KaScriptProcessor.LOCAL.equals(scope)) {
                kec.evalLocal(js);
            } else {
                kec.evalGlobal(js);
            }
            sh.removeElement();
        }
    }

}
