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
import io.karatelabs.common.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.cache.AlwaysValidCacheEntryValidity;
import org.thymeleaf.cache.NonCacheableCacheEntryValidity;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.templateresolver.TemplateResolution;

import java.util.Map;

class HtmlTemplateResolver implements ITemplateResolver {

    private static final Logger logger = LoggerFactory.getLogger(HtmlTemplateResolver.class);

    private final ResourceResolver resolver;
    private final boolean devMode;
    private boolean doneWithStringTemplate;
    private Resource prevCaller;

    HtmlTemplateResolver(MarkupConfig config) {
        resolver = config.getResolver();
        devMode = config.isDevMode();
    }

    @Override
    public String getName() {
        return getClass().getName();
    }

    @Override
    public Integer getOrder() {
        return 1;
    }

    @Override
    public TemplateResolution resolveTemplate(IEngineConfiguration ec, String ownerTemplate, String content, Map<String, Object> attributes) {
        if (attributes != null && !doneWithStringTemplate) { // string
            doneWithStringTemplate = true;
            prevCaller = null;  // Reset caller for string templates
            HtmlStringTemplateResource templateResource = new HtmlStringTemplateResource(content, resolver);
            return new TemplateResolution(templateResource, TemplateMode.HTML, NonCacheableCacheEntryValidity.INSTANCE);
        } else { // html file name
            if (!content.endsWith(".html")) {
                content = content + ".html";
            }
            Resource caller;
            // Only resolve ownerTemplate as caller if it's a valid template name (not HTML content or markers)
            if (ownerTemplate != null && !ownerTemplate.startsWith(Resource.THIS_COLON) && prevCaller != null) {
                // ownerTemplate needs .html extension too
                String ownerPath = ownerTemplate.endsWith(".html") ? ownerTemplate : ownerTemplate + ".html";
                caller = resolver.resolve(ownerPath, null);
            } else {
                caller = prevCaller;
            }
            Resource resource = resolver.resolve(content, caller);
            if (resource == null || !resource.exists()) {
                throw new ResourceNotFoundException(content);
            }
            prevCaller = resource;
            HtmlTemplateResource templateResource = new HtmlTemplateResource(ownerTemplate, resource);
            return new TemplateResolution(templateResource, TemplateMode.HTML,
                    devMode ? NonCacheableCacheEntryValidity.INSTANCE : AlwaysValidCacheEntryValidity.INSTANCE);
        }
    }

}
