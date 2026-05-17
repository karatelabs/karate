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
package io.karatelabs.js;

public class JsJava implements SimpleObject {

    final ExternalBridge bridge;

    JsJava(ExternalBridge bridge) {
        if (bridge == null) {
            throw new RuntimeException("java bridge not enabled");
        }
        this.bridge = bridge;
    }

    @Override
    public Object jsGet(String name) {
        return switch (name) {
            case "type" -> (JsInvokable) args -> {
                String className = (String) args[0];
                // forType() returns null on ClassNotFoundException — that
                // null-as-sentinel contract is needed by PropertyAccess for
                // the dotted-FQN probe, so we keep it. But here the script
                // explicitly asked for this class; a null result must surface
                // as a real error rather than silently propagating and
                // failing later as "cannot read properties of null".
                ExternalAccess type = bridge.forType(className);
                if (type == null) {
                    throw JsErrorException.typeError("Java.type: class not found: " + className);
                }
                return type;
            };
            case "to" -> (JsInvokable) args -> {
                if (args[0] instanceof ExternalAccess ja) {
                    return ja.getJavaValue();
                }
                // TODO regex, functions, lambdas
                return null;
            };
            default -> throw JsErrorException.typeError("no such api on Java: " + name);
        };
    }

}
