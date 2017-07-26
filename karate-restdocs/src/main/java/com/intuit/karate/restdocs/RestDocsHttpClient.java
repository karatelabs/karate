/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.restdocs;

import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.MultiPartItem;
import com.intuit.karate.http.apache.ApacheHttpClient;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.restdocs.ManualRestDocumentation;
import org.springframework.restdocs.RestDocumentationContext;
import org.springframework.restdocs.generate.RestDocumentationGenerator;

/**
 *
 * @author pthomas3
 */
public class RestDocsHttpClient extends ApacheHttpClient {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Override
    public HttpResponse invoke(HttpRequest request, ScriptContext context) {
        COUNTER.incrementAndGet();
        if (request.getMultiPartItems() != null) {
            for (MultiPartItem item : request.getMultiPartItems()) {
                if (item.getValue().isStream()) {
                    String s = item.getValue().getAsString();
                    if (item.getContentType() == null) {
                        item.setContentType(APPLICATION_OCTET_STREAM);
                    }
                    item.setValue(new ScriptValue(s));
                }
            }
        }
        HttpResponse response = super.invoke(request, context);
        ManualRestDocumentation restDocumentation = new ManualRestDocumentation();
        restDocumentation.beforeTest(this.getClass(), "invoke");
        KarateRestDocumentationConfigurer configurer = new KarateRestDocumentationConfigurer(restDocumentation);
        configurer.apply();        
        HashMap<String, Object> configuration = configurer.getConfiguration();
        RestDocumentationContext restDocumentationContext = configurer.getContext();
        configuration.put(RestDocumentationContext.class.getName(), restDocumentationContext);        
        getDelegate().handle(request, response, configuration);
        restDocumentation.afterTest();
        return response;

    }

    private RestDocumentationGenerator<HttpRequest, HttpResponse> getDelegate() {
        RestDocumentationGenerator<HttpRequest, HttpResponse> delegate
                = new RestDocumentationGenerator<>("restdocs" + COUNTER,
                        new KarateRequestConverter(),
                        new KarateResponseConverter());
        return delegate;
    }

}
