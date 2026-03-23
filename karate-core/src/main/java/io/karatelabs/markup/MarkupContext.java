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

import io.karatelabs.js.JavaInvokable;
import io.karatelabs.js.SimpleObject;

/**
 * Context interface exposed to templates and server-side JavaScript.
 * <p>
 * This interface defines the core APIs available in both plain templating mode
 * and server mode. Server-specific methods (like redirect, init, close) are
 * added by ServerMarkupContext which extends this interface.
 * <p>
 * In templates, access via the 'context' variable:
 * <pre>
 * &lt;script ka:scope="global"&gt;
 *     var data = context.read('data.json');
 *     var json = context.toJson({ name: 'test' });
 *     console.log('Template: ' + context.template);
 * &lt;/script&gt;
 * </pre>
 */
public interface MarkupContext extends SimpleObject {

    /**
     * Returns the current template name (without leading slash).
     */
    String getTemplateName();

    /**
     * Returns the caller template name (for fragments/includes), or null if top-level.
     */
    String getCallerTemplateName();

    /**
     * Read a resource as text.
     *
     * @param path resource path (relative to template root or absolute)
     * @return text content of the resource
     */
    String read(String path);

    /**
     * Read a resource as bytes.
     *
     * @param path resource path (relative to template root or absolute)
     * @return byte array content of the resource
     */
    byte[] readBytes(String path);

    /**
     * Serialize an object to JSON string.
     *
     * @param obj object to serialize
     * @return JSON string
     */
    String toJson(Object obj);

    /**
     * Parse a JSON string into an object (Map or List).
     *
     * @param json JSON string
     * @return parsed object
     */
    Object fromJson(String json);

    /**
     * Returns the session object if one exists, null otherwise.
     * Used by template engine to sync session variable after context.init() is called.
     * Default returns null (no session in plain templating mode).
     */
    default Object getContextSession() {
        return null;
    }

    /**
     * Default implementation of jsGet that exposes context methods to JavaScript.
     * Implementations can override to add more properties/methods.
     */
    @Override
    default Object jsGet(String key) {
        return switch (key) {
            case "template" -> getTemplateName();
            case "caller" -> getCallerTemplateName();
            case "read" -> (JavaInvokable) args -> {
                if (args.length == 0) throw new RuntimeException("read() requires a path argument");
                return read(args[0].toString());
            };
            case "readBytes" -> (JavaInvokable) args -> {
                if (args.length == 0) throw new RuntimeException("readBytes() requires a path argument");
                return readBytes(args[0].toString());
            };
            case "toJson" -> (JavaInvokable) args -> {
                if (args.length == 0) throw new RuntimeException("toJson() requires an object argument");
                return toJson(args[0]);
            };
            case "fromJson" -> (JavaInvokable) args -> {
                if (args.length == 0) throw new RuntimeException("fromJson() requires a JSON string argument");
                return fromJson(args[0].toString());
            };
            default -> null;
        };
    }

}
