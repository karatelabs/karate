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

import com.intuit.karate.FileUtils;
import com.intuit.karate.http.ServerConfig;
import com.intuit.karate.resource.Resource;
import com.intuit.karate.resource.ResourceResolver;
import java.io.File;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

/**
 *
 * @author pthomas3
 */
public class KaScriptAttrProcessor extends AbstractAttributeTagProcessor {

    private static final Logger logger = LoggerFactory.getLogger(KaScriptAttrProcessor.class);

    private static final String SRC = "src";

    private final String hostContextPath;
    private final ResourceResolver resourceResolver;

    public KaScriptAttrProcessor(String dialectPrefix, ServerConfig config) {
        super(TemplateMode.HTML, dialectPrefix, null, false, SRC, false, 1000, false);
        resourceResolver = config.getResourceResolver();
        hostContextPath = config.getHostContextPath();
    }

    @Override
    protected void doProcess(ITemplateContext ctx, IProcessableElementTag tag, AttributeName an, String src, IElementTagStructureHandler sh) {
        String scope = tag.getAttributeValue(getDialectPrefix(), KaScriptElemProcessor.SCOPE);
        if (scope == null) {
            if (hostContextPath != null) {
                src = hostContextPath + src;
            }
            String noCache = tag.getAttributeValue(getDialectPrefix(), KaScriptElemProcessor.NOCACHE);
            if (noCache != null) {
                Resource resource = resourceResolver.resolve(src);
                if (resource.isFile()) {
                    File file = resource.getFile();
                    src = src + "?ts=" + file.lastModified();
                }
                sh.removeAttribute(getDialectPrefix(), KaScriptElemProcessor.NOCACHE);
            }
            sh.setAttribute(SRC, src);
            return;
        }
        InputStream is = resourceResolver.resolve(src).getStream();
        String js = FileUtils.toString(is);
        if (KaScriptElemProcessor.LOCAL.equals(scope)) {
            KarateEngineContext.get().evalLocal(js, false);
        } else {
            KarateEngineContext.get().evalGlobal(js);
        }
        sh.removeElement();
    }

}
