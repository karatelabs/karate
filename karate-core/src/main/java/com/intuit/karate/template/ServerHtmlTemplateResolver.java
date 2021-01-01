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

import com.intuit.karate.resource.Resource;
import com.intuit.karate.resource.ResourceResolver;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.cache.NonCacheableCacheEntryValidity;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.templateresolver.TemplateResolution;

/**
 *
 * @author pthomas3
 */
public class ServerHtmlTemplateResolver implements ITemplateResolver {

    private static final Logger logger = LoggerFactory.getLogger(ServerHtmlTemplateResolver.class);

    private final ResourceResolver resourceResolver;

    public ServerHtmlTemplateResolver(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    @Override
    public String getName() {
        return getClass().getName();
    }

    @Override
    public Integer getOrder() {
        return 0;
    }

    @Override
    public TemplateResolution resolveTemplate(IEngineConfiguration ec, String ownerTemplate, String name, Map<String, Object> templateResolutionAttributes) {
        Resource resource = resourceResolver.resolve(name + ".html");
        KarateTemplateResource templateResource = new KarateTemplateResource(resource);
        return new TemplateResolution(templateResource, TemplateMode.HTML, NonCacheableCacheEntryValidity.INSTANCE); // TODO cache switch
    }

}
