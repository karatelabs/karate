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

class JsConsole implements SimpleObject {

    final ContextRoot root;

    JsConsole(ContextRoot root) {
        this.root = root;
    }

    @Override
    public Object jsGet(String name) {
        if ("log".equals(name)) {
            return (JsCallable) (context, args) -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];
                    if (i > 0) {
                        sb.append(' ');
                    }
                    Object callable = null;
                    if (arg instanceof ObjectLike objectLike) {
                        callable = objectLike.getMember(SimpleObject.TO_STRING);
                    }
                    if (callable instanceof JsCallable jsc) {
                        // ES6: call toString with 'this' set to the object being stringified
                        CoreContext callContext = new CoreContext((CoreContext) context, null, null);
                        callContext.thisObject = arg;
                        sb.append(jsc.call(callContext, new Object[0]));
                    } else {
                        sb.append(Terms.TO_STRING(arg));
                    }
                }
                if (root.onConsoleLog != null) {
                    root.onConsoleLog.accept(sb.toString());
                } else {
                    System.out.println(sb);
                }
                return null;
            };
        }
        return null;
    }

}
