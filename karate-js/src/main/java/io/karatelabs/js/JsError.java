/*
 * The MIT License
 *
 * Copyright 2024 Karate Labs Inc.
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
package io.karatelabs.js;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JavaScript Error instance. Slim wrapper: {@code name} / {@code constructor}
 * live on the bound {@link JsErrorPrototype}; {@code message} / {@code cause} /
 * {@code errors} are installed as own data properties only when explicitly
 * passed to the constructor (per spec §20.5.1.1 / §20.5.7.1).
 * <p>
 * The Java {@code cause} field is preserved separately so the host's
 * {@link JsErrorException} can chain it through {@link Throwable#getCause()}
 * for IDE-hyperlinkable stack traces — distinct from the JS-visible
 * {@code .cause} own property (which carries whatever the user passed,
 * possibly nothing).
 */
class JsError extends JsObject {

    private final Throwable javaCause;

    JsError(JsErrorPrototype prototype) {
        super(null, prototype);
        this.javaCause = null;
    }

    JsError(JsErrorPrototype prototype, Throwable javaCause) {
        super(null, prototype);
        this.javaCause = javaCause;
    }

    Throwable getJavaCause() {
        return javaCause;
    }

    @Override
    public String toString() {
        // Spec Error.prototype.toString shape — mirrored for Java/IntelliJ logging.
        Object nameVal = getMember("name");
        String name = (nameVal == null || nameVal == Terms.UNDEFINED) ? "Error" : nameVal.toString();
        Object msgVal = getMember("message");
        String msg = (msgVal == null || msgVal == Terms.UNDEFINED) ? "" : msgVal.toString();
        if (msg.isEmpty()) return name;
        return name + ": " + msg;
    }

    @Override
    public Map<String, Object> toMap() {
        // Host inspection: surface the spec-visible identity (name from proto, message
        // own when set) plus any user-added own properties.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", getMember("name"));
        Object msg = getMember("message");
        if (msg != null && msg != Terms.UNDEFINED) {
            result.put("message", msg);
        }
        Map<String, Object> own = super.toMap();
        if (own != null && !own.isEmpty()) {
            for (Map.Entry<String, Object> e : own.entrySet()) {
                if (!result.containsKey(e.getKey())) {
                    result.put(e.getKey(), e.getValue());
                }
            }
        }
        return result;
    }

}
