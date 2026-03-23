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

import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.processor.IProcessor;

import java.util.HashSet;
import java.util.Set;

/**
 * Thymeleaf dialect for HTMX support.
 * Provides processors for ka:get, ka:post, ka:put, ka:patch, ka:delete, ka:vals, ka:target, ka:swap, etc.
 * These are converted to their hx-* equivalents during template processing.
 */
public class HxDialect extends AbstractProcessorDialect {

    private final String contextPath;

    /**
     * Create a new HxDialect with default configuration (no context path).
     */
    public HxDialect() {
        this((String) null);
    }

    /**
     * Create a new HxDialect with the given markup configuration.
     * @param config the markup configuration (uses contextPath from it)
     */
    public HxDialect(MarkupConfig config) {
        this(config != null ? config.getContextPath() : null);
    }

    /**
     * Create a new HxDialect with the given context path.
     * @param contextPath the context path to prepend to HTMX URLs
     */
    public HxDialect(String contextPath) {
        // Priority 3000 - processed after KaDialect (2000) and standard dialect (1000)
        super("Htmx", "ka", 3000);
        this.contextPath = contextPath;
    }

    /**
     * Get the context path for this dialect.
     * @return the context path (may be null)
     */
    public String getContextPath() {
        return contextPath;
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        Set<IProcessor> processors = new HashSet<>();

        // HTTP method processors (ka:get, ka:post, ka:put, ka:patch, ka:delete)
        processors.add(new HxMethodProcessor(dialectPrefix, "get", contextPath));
        processors.add(new HxMethodProcessor(dialectPrefix, "post", contextPath));
        processors.add(new HxMethodProcessor(dialectPrefix, "put", contextPath));
        processors.add(new HxMethodProcessor(dialectPrefix, "patch", contextPath));
        processors.add(new HxMethodProcessor(dialectPrefix, "delete", contextPath));

        // ka:vals processor
        processors.add(new HxValsProcessor(dialectPrefix));

        // Generic pass-through processors (ka:target -> hx-target, ka:swap -> hx-swap, etc.)
        String[] genericAttributes = {
            "target",       // hx-target: CSS selector for element to update
            "swap",         // hx-swap: how content should be swapped
            "trigger",      // hx-trigger: what triggers the request
            "push-url",     // hx-push-url: update browser URL
            "select",       // hx-select: select content from response
            "confirm",      // hx-confirm: confirmation dialog
            "indicator",    // hx-indicator: loading indicator selector
            "boost",        // hx-boost: enhance regular links
            "headers",      // hx-headers: additional headers
            "include",      // hx-include: include additional inputs
            "sync",         // hx-sync: synchronization strategy
            "disabled-elt", // hx-disabled-elt: disable elements during request
            "encoding",     // hx-encoding: form encoding
            "ext",          // hx-ext: extensions
            "history",      // hx-history: history behavior
            "history-elt",  // hx-history-elt: history element
            "preserve",     // hx-preserve: preserve element
            "prompt",       // hx-prompt: prompt dialog
            "replace-url",  // hx-replace-url: replace URL in history
            "request",      // hx-request: request configuration
            "validate"      // hx-validate: form validation
        };
        for (String attr : genericAttributes) {
            processors.add(new HxGenericProcessor(dialectPrefix, attr));
        }

        // ka:data processor for Alpine.js data binding
        processors.add(new KaDataProcessor(dialectPrefix));

        return processors;
    }

}
