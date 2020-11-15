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
import com.intuit.karate.http.ResourceResolver;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import org.thymeleaf.templateresource.ITemplateResource;

/**
 *
 * @author pthomas3
 */
public class TemplateResource implements ITemplateResource {

    private final ServerConfig config;
    private final ResourceResolver resourceResolver;
    private final String name;

    public TemplateResource(String name, ServerConfig config) {
        this.name = name;
        this.config = config;
        resourceResolver = config.getResourceResolver();
    }

    @Override
    public String getDescription() {
        return name;
    }

    @Override
    public String getBaseName() {
        return name;
    }

    @Override
    public boolean exists() {
        return true;
    }

    private static final String DOT_HTML = ".html";
    
    @Override
    public Reader reader() throws IOException {
        String mount = config.getMountPath(name);
        InputStream is;
        if (mount == null) {
            is = resourceResolver.read(name + DOT_HTML);
        } else {
            is = FileUtils.resourceAsStream(mount + DOT_HTML);
        }
        return new StringReader(FileUtils.toString(is));
    }

    @Override
    public ITemplateResource relative(String relativeLocation) {
        throw new UnsupportedOperationException("relative: " + relativeLocation + " - not implemented");
    }

}
