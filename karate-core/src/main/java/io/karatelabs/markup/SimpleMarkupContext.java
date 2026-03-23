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

import io.karatelabs.common.FileUtils;
import io.karatelabs.common.Json;
import io.karatelabs.common.Resource;
import org.thymeleaf.engine.TemplateData;
import org.thymeleaf.templateresource.ITemplateResource;

/**
 * Implementation of MarkupContext for plain templating mode (without server).
 * Provides access to template metadata, resource reading, and JSON utilities.
 *
 * @see io.karatelabs.http.ServerMarkupContext for server mode with HTTP request/response handling
 */
class SimpleMarkupContext implements MarkupContext {

    final MarkupTemplateContext markup;
    final ResourceResolver resolver;

    SimpleMarkupContext(MarkupTemplateContext markup, ResourceResolver resolver) {
        this.markup = markup;
        this.resolver = resolver;
    }

    @Override
    public String getTemplateName() {
        String name = markup.wrapped.getTemplateData().getTemplate();
        return name.startsWith("/") ? name.substring(1) : name;
    }

    @Override
    public String getCallerTemplateName() {
        TemplateData td = markup.wrapped.getTemplateData();
        ITemplateResource tr = td.getTemplateResource();
        if (tr instanceof HtmlTemplateResource ktr) {
            return ktr.getCaller();
        }
        return null;
    }

    @Override
    public String read(String path) {
        Resource resource = resolver.resolve(path, null);
        return resource.getText();
    }

    @Override
    public byte[] readBytes(String path) {
        Resource resource = resolver.resolve(path, null);
        return FileUtils.toBytes(resource.getStream());
    }

    @Override
    public String toJson(Object obj) {
        return Json.stringifyStrict(obj);
    }

    @Override
    public Object fromJson(String json) {
        return Json.of(json).value();
    }

}
