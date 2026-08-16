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

import io.karatelabs.parser.Node;
import io.karatelabs.parser.Token;

import java.util.LinkedHashMap;
import java.util.List;
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

    private static final int MAX_FRAMES = 10;
    private static final List<String> INTRINSIC_NAMES = List.of("stack");

    // Deepest-first source positions captured where this error was created,
    // already formatted as "\n    at <source>:<line>:<col>" lines. Held apart
    // from `stack` because `message` is installed after construction, so the
    // "<name>: <message>" header can only be rendered on first read.
    private String frames;
    private Object stack;
    private boolean stackResolved;

    /**
     * {@code error.stack} is not in ECMA-262 but every engine has it and LLM
     * code logs it in every catch block, so an {@code undefined} here is a
     * real-world break. Shape follows the de-facto V8 one: the
     * {@code Error.prototype.toString} header followed by one
     * {@code "    at …"} frame per enclosing JS context, innermost first.
     * <p>
     * Called at construction (that is the only moment the call chain still
     * exists); a second call is a no-op so the outermost — earliest — capture
     * wins. Rendering is deferred to the first read: the property is free
     * unless someone actually looks at it.
     */
    void captureStack(CoreContext context) {
        if (frames != null || stackResolved) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (CoreContext c = context; c != null && count < MAX_FRAMES; c = c.parent) {
            Node node = c.node;
            if (node == null) {
                continue;
            }
            Token token = node.getFirstToken();
            if (token == null || token == Token.EMPTY) {
                continue;
            }
            String source = token.getResource().isFile()
                    ? token.getResource().getRelativePath()
                    : "<eval>";
            sb.append("\n    at ").append(source).append(':').append(token.getPositionDisplay());
            count++;
        }
        frames = sb.toString();
    }

    // `stack` is an own data property per the de-facto contract — writable and
    // configurable, but NOT enumerable, so JSON.stringify(err) stays "{}".
    // Modelled as an intrinsic rather than a real slot so it also stays out of
    // the host-facing toMap() view.
    @Override
    protected Object resolveOwnIntrinsic(String name) {
        if (!"stack".equals(name)) {
            return null;
        }
        if (!stackResolved) {
            stack = toString() + (frames == null ? "" : frames);
            stackResolved = true;
        }
        return stack;
    }

    @Override
    protected Iterable<String> ownIntrinsicNames() {
        return INTRINSIC_NAMES;
    }

    @Override
    public byte getOwnAttrs(String name) {
        return "stack".equals(name) && !hasExplicitAttrs(name)
                ? (byte) (WRITABLE | CONFIGURABLE)
                : super.getOwnAttrs(name);
    }

    @Override
    public void putMember(String name, Object value, CoreContext ctx, boolean strict) {
        if ("stack".equals(name)) {
            putMember(name, value);
            return;
        }
        super.putMember(name, value, ctx, strict);
    }

    @Override
    public void putMember(String name, Object value) {
        if ("stack".equals(name)) {
            stack = value; // writable, and not coerced — engines allow any value here
            stackResolved = true;
            return;
        }
        super.putMember(name, value);
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
