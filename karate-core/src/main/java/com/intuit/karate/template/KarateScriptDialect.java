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

import com.intuit.karate.http.ServerConfig;
import com.intuit.karate.resource.ResourceResolver;
import java.util.HashSet;
import java.util.Set;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.processor.IProcessor;

/**
 *
 * @author pthomas3
 */
public class KarateScriptDialect extends AbstractProcessorDialect {

    private final ServerConfig config;

    public KarateScriptDialect(ServerConfig config) {
        super("karate", "ka", 2000); // has to be processed after standard (default) dialect which is 1000
        this.config = config;
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        Set<IProcessor> ps = new HashSet();
        ps.add(new KaScriptAttrProcessor(dialectPrefix, config));
        ps.add(new KaScriptElemProcessor(dialectPrefix));
        ps.add(new KaSetElemProcessor(dialectPrefix));
        return ps;
    }

}
