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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import org.thymeleaf.templateresource.ITemplateResource;

/**
 *
 * @author pthomas3
 */
public class StringTemplateResource implements ITemplateResource {    
    
    private final String text;
    
    public StringTemplateResource(String text) {
        this.text = text;
    }

    @Override
    public String getDescription() {
        return getBaseName();
    }

    @Override
    public String getBaseName() {
        return getClass().getName();
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public Reader reader() throws IOException {
        return new StringReader(text);
    }

    @Override
    public ITemplateResource relative(String relativeLocation) {
        throw new UnsupportedOperationException("relative: " + relativeLocation + " - not implemented");
    }
    
}
