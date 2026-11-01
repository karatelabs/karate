/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Singleton prototype for {@link JsUrl}: the WHATWG accessors, {@code toString} and {@code toJSON}.
 * A setter given a value it cannot apply leaves the URL unchanged, as the spec's setters do.
 */
class JsUrlPrototype extends Prototype {

    static final JsUrlPrototype INSTANCE = new JsUrlPrototype();

    private static final byte ACCESSOR_ATTRS = (byte) (PropertySlot.ENUMERABLE | PropertySlot.CONFIGURABLE | PropertySlot.INTRINSIC);

    private JsUrlPrototype() {
        super(JsObjectPrototype.INSTANCE);
        accessor("href", JsUrl::href, (u, v) -> {
            JsUrl parsed = JsUrl.parse(v, null);
            if (parsed == null) {
                throw JsErrorException.typeError("Invalid URL");
            }
            u.assign(parsed);
        });
        accessor("origin", JsUrl::origin, null);
        accessor("protocol", u -> u.scheme + ":", JsUrlPrototype::setProtocol);
        accessor("username", u -> u.username, (u, v) -> {
            if (hasCredentials(u)) {
                u.username = JsUrl.encode(v, JsUrl.USERINFO);
            }
        });
        accessor("password", u -> u.password, (u, v) -> {
            if (hasCredentials(u)) {
                u.password = JsUrl.encode(v, JsUrl.USERINFO);
            }
        });
        accessor("host", JsUrl::hostPort, (u, v) -> {
            if (!u.opaque) {
                setHost(u, v, true);
            }
        });
        accessor("hostname", u -> u.host == null ? "" : u.host, (u, v) -> {
            if (!u.opaque) {
                setHost(u, v, false);
            }
        });
        accessor("port", u -> u.port == -1 ? "" : String.valueOf(u.port), JsUrlPrototype::setPort);
        accessor("pathname", u -> u.path, (u, v) -> {
            if (!u.opaque) {
                u.path = JsUrl.normalizePath(v, u.special(), u.host != null);
            }
        });
        accessor("search", u -> u.query == null || u.query.isEmpty() ? "" : "?" + u.query, (u, v) -> {
            String q = v.startsWith("?") ? v.substring(1) : v;
            u.query = v.isEmpty() ? null : JsUrl.encode(q, u.special() ? JsUrl.SPECIAL_QUERY : JsUrl.QUERY);
            u.searchParams.reset(u.query);
        });
        installAccessor("searchParams", new JsBuiltinMethod("get searchParams", 0,
                (ctx, args) -> asUrl(ctx, "searchParams").searchParams), null, ACCESSOR_ATTRS);
        accessor("hash", u -> u.fragment == null || u.fragment.isEmpty() ? "" : "#" + u.fragment, (u, v) -> {
            String f = v.startsWith("#") ? v.substring(1) : v;
            u.fragment = v.isEmpty() ? null : JsUrl.encode(f, JsUrl.FRAGMENT);
        });
        install("toString", 0, (ctx, args) -> asUrl(ctx, "toString").href());
        install("toJSON", 0, (ctx, args) -> asUrl(ctx, "toJSON").href());
        install("@@toStringTag", "URL");
        installConstructor("URL");
    }

    private void accessor(String name, Function<JsUrl, String> getter, BiConsumer<JsUrl, String> setter) {
        JsCallable get = new JsBuiltinMethod("get " + name, 0, (ctx, args) -> getter.apply(asUrl(ctx, name)));
        JsCallable set = setter == null ? null : new JsBuiltinMethod("set " + name, 1, (ctx, args) -> {
            Object value = args.length > 0 ? args[0] : Terms.UNDEFINED;
            setter.accept(asUrl(ctx, name), Terms.toStringCoerce(value, ctx instanceof CoreContext cc ? cc : null));
            return Terms.UNDEFINED;
        });
        installAccessor(name, get, set, ACCESSOR_ATTRS);
    }

    private static JsUrl asUrl(Context context, String member) {
        if (context.getThisObject() instanceof JsUrl u) {
            return u;
        }
        throw JsErrorException.typeError("Receiver must be an instance of class URL (reading '" + member + "')");
    }

    private static boolean hasCredentials(JsUrl u) {
        return u.host != null && !u.host.isEmpty() && !"file".equals(u.scheme);
    }

    private static void setProtocol(JsUrl u, String value) {
        int colon = value.indexOf(':');
        String scheme = (colon < 0 ? value : value.substring(0, colon)).toLowerCase(java.util.Locale.ROOT);
        if (scheme.isEmpty() || !scheme.matches("[a-z][a-z0-9+.-]*")) {
            return;
        }
        boolean special = JsUrl.SPECIAL.containsKey(scheme);
        if (special != u.special()) {
            return;
        }
        if ("file".equals(scheme) && (!u.username.isEmpty() || !u.password.isEmpty() || u.port != -1)) {
            return;
        }
        if ("file".equals(u.scheme) && u.host != null && u.host.isEmpty()) {
            return;
        }
        u.scheme = scheme;
        u.port = JsUrl.defaultPort(scheme, u.port);
    }

    private static void setHost(JsUrl u, String value, boolean withPort) {
        int end = 0;
        while (end < value.length() && "/?#".indexOf(value.charAt(end)) < 0 && !(u.special() && value.charAt(end) == '\\')) {
            end++;
        }
        String hp = value.substring(0, end);
        if (!withPort && hp.indexOf(':') >= 0 && !hp.startsWith("[")) {
            return;
        }
        if (hp.isEmpty() && u.special()) {
            return;
        }
        JsUrl copy = new JsUrl();
        copy.scheme = u.scheme;
        copy.port = u.port;
        if (JsUrl.setHostPort(copy, hp, withPort && hp.contains(":") && !hp.endsWith("]"))) {
            u.host = "file".equals(u.scheme) && "localhost".equals(copy.host) ? "" : copy.host;
            u.port = copy.port;
        }
    }

    private static void setPort(JsUrl u, String value) {
        if (u.host == null || u.host.isEmpty() || "file".equals(u.scheme)) {
            return;
        }
        if (value.isEmpty()) {
            u.port = -1;
            return;
        }
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end)) && value.charAt(end) < 0x80) {
            end++;
        }
        int port = JsUrl.parsePort(value.substring(0, end));
        if (port >= 0) {
            u.port = JsUrl.defaultPort(u.scheme, port);
        }
    }

}
