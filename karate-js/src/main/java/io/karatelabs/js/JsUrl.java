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

import java.io.ByteArrayOutputStream;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * WHATWG URL: the parser and serializer for the common cases. Not modelled: Windows
 * drive letters in {@code file:} URLs, {@code blob:} origins and IDNA's UTS #46 mapping
 * beyond {@link IDN#toASCII}.
 */
class JsUrl extends JsObject {

    static final Map<String, Integer> SPECIAL = Map.of("http", 80, "https", 443, "ws", 80, "wss", 443, "ftp", 21, "file", -1);

    static final IntPredicate C0 = c -> c < 0x20 || c == 0x7F;
    static final IntPredicate FRAGMENT = c -> C0.test(c) || " \"<>`".indexOf(c) >= 0;
    static final IntPredicate QUERY = c -> C0.test(c) || " \"#<>".indexOf(c) >= 0;
    static final IntPredicate SPECIAL_QUERY = c -> QUERY.test(c) || c == '\'';
    static final IntPredicate PATH = c -> QUERY.test(c) || "?`{}".indexOf(c) >= 0;
    static final IntPredicate USERINFO = c -> PATH.test(c) || "/:;=@[\\]^|".indexOf(c) >= 0;

    String scheme = "";
    String username = "";
    String password = "";
    String host;
    int port = -1;
    String path = "";
    boolean opaque;
    String query;
    String fragment;
    final JsUrlSearchParams searchParams;

    JsUrl() {
        super(null, JsUrlPrototype.INSTANCE);
        searchParams = new JsUrlSearchParams(this);
    }

    boolean special() {
        return SPECIAL.containsKey(scheme);
    }

    String href() {
        StringBuilder sb = new StringBuilder(scheme).append(':');
        if (host != null) {
            sb.append("//");
            if (!username.isEmpty() || !password.isEmpty()) {
                sb.append(username);
                if (!password.isEmpty()) {
                    sb.append(':').append(password);
                }
                sb.append('@');
            }
            sb.append(hostPort());
        } else if (!opaque && path.startsWith("//")) {
            sb.append("/.");
        }
        sb.append(path);
        if (query != null) {
            sb.append('?').append(query);
        }
        if (fragment != null) {
            sb.append('#').append(fragment);
        }
        return sb.toString();
    }

    String hostPort() {
        if (host == null) {
            return "";
        }
        return port == -1 ? host : host + ":" + port;
    }

    String origin() {
        if (!special() || "file".equals(scheme)) {
            return "null";
        }
        return scheme + "://" + hostPort();
    }

    void assign(JsUrl from) {
        scheme = from.scheme;
        username = from.username;
        password = from.password;
        host = from.host;
        port = from.port;
        path = from.path;
        opaque = from.opaque;
        query = from.query;
        fragment = from.fragment;
        searchParams.reset(query);
    }

    //=== parsing ======================================================================================================

    /** The URL {@code input} names, resolved against {@code base}; null when it is not a valid URL. */
    static JsUrl parse(String input, JsUrl base) {
        JsUrl u = new JsUrl();
        boolean ok = parseInto(u, clean(input), base);
        if (!ok) {
            return null;
        }
        u.searchParams.reset(u.query);
        return u;
    }

    private static String clean(String input) {
        int start = 0;
        int end = input.length();
        while (start < end && input.charAt(start) <= ' ') start++;
        while (end > start && input.charAt(end - 1) <= ' ') end--;
        StringBuilder sb = new StringBuilder(end - start);
        for (int i = start; i < end; i++) {
            char c = input.charAt(i);
            if (c != '\t' && c != '\n' && c != '\r') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int schemeEnd(String s) {
        if (s.isEmpty() || !isAlpha(s.charAt(0))) {
            return -1;
        }
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ':') {
                return i;
            }
            if (!isAlpha(c) && !(c >= '0' && c <= '9') && c != '+' && c != '-' && c != '.') {
                return -1;
            }
        }
        return -1;
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isSlash(char c, boolean special) {
        return c == '/' || (special && c == '\\');
    }

    private static boolean parseInto(JsUrl u, String s, JsUrl base) {
        int colon = schemeEnd(s);
        if (colon > 0) {
            u.scheme = s.substring(0, colon).toLowerCase(Locale.ROOT);
            String rest = s.substring(colon + 1);
            boolean special = u.special();
            if ("file".equals(u.scheme)) {
                return parseFile(u, rest, base != null && "file".equals(base.scheme) ? base : null);
            }
            if (special) {
                if (base != null && base.scheme.equals(u.scheme) && (rest.isEmpty() || !isSlash(rest.charAt(0), true))) {
                    return relative(u, rest, base);
                }
                int i = 0;
                while (i < rest.length() && isSlash(rest.charAt(i), true)) i++;
                return authorityAndPath(u, rest.substring(i));
            }
            if (rest.startsWith("//")) {
                return authorityAndPath(u, rest.substring(2));
            }
            if (rest.startsWith("/")) {
                pathQueryFragment(u, rest);
                return true;
            }
            u.opaque = true;
            int hash = rest.indexOf('#');
            if (hash >= 0) {
                u.fragment = encode(rest.substring(hash + 1), FRAGMENT);
                rest = rest.substring(0, hash);
            }
            int q = rest.indexOf('?');
            if (q >= 0) {
                u.query = encode(rest.substring(q + 1), QUERY);
                rest = rest.substring(0, q);
            }
            u.path = encode(rest, C0);
            return true;
        }
        if (base == null) {
            return false;
        }
        if (base.opaque) {
            if (!s.startsWith("#")) {
                return false;
            }
            u.assign(base);
            u.fragment = encode(s.substring(1), FRAGMENT);
            return true;
        }
        u.scheme = base.scheme;
        if ("file".equals(u.scheme)) {
            return parseFile(u, s, base);
        }
        return relative(u, s, base);
    }

    private static boolean relative(JsUrl u, String s, JsUrl base) {
        boolean special = u.special();
        if (s.length() > 1 && isSlash(s.charAt(0), special) && isSlash(s.charAt(1), special)) {
            int i = 0;
            while (special && i < s.length() && isSlash(s.charAt(i), true)) i++;
            return authorityAndPath(u, s.substring(special ? i : 2));
        }
        u.username = base.username;
        u.password = base.password;
        u.host = base.host;
        u.port = base.port;
        if (!s.isEmpty() && isSlash(s.charAt(0), special)) {
            pathQueryFragment(u, s);
            return true;
        }
        if (s.isEmpty() || s.charAt(0) == '?' || s.charAt(0) == '#') {
            u.path = base.path;
            if (s.isEmpty() || s.charAt(0) == '#') {
                u.query = base.query;
            }
            queryFragment(u, s);
            return true;
        }
        String dir = base.path.substring(0, base.path.lastIndexOf('/') + 1);
        pathQueryFragment(u, (dir.isEmpty() ? "/" : dir) + s);
        return true;
    }

    private static boolean parseFile(JsUrl u, String s, JsUrl base) {
        u.scheme = "file";
        u.host = "";
        if (s.length() > 1 && isSlash(s.charAt(0), true) && isSlash(s.charAt(1), true)) {
            String rest = s.substring(2);
            int end = authorityEnd(rest, true);
            String h = rest.substring(0, end);
            if (!h.isEmpty()) {
                String parsed = parseHost(h, true);
                if (parsed == null) {
                    return false;
                }
                u.host = "localhost".equals(parsed) ? "" : parsed;
            }
            pathQueryFragment(u, rest.substring(end));
            return true;
        }
        if (base != null && (s.isEmpty() || !isSlash(s.charAt(0), true))) {
            return relative(u, s, base);
        }
        pathQueryFragment(u, s);
        return true;
    }

    private static int authorityEnd(String s, boolean special) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' || c == '?' || c == '#' || (special && c == '\\')) {
                return i;
            }
        }
        return s.length();
    }

    private static boolean authorityAndPath(JsUrl u, String s) {
        boolean special = u.special();
        int end = authorityEnd(s, special);
        String authority = s.substring(0, end);
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            String userinfo = authority.substring(0, at);
            int colon = userinfo.indexOf(':');
            u.username = encode(colon < 0 ? userinfo : userinfo.substring(0, colon), USERINFO);
            u.password = colon < 0 ? "" : encode(userinfo.substring(colon + 1), USERINFO);
            authority = authority.substring(at + 1);
        }
        if (!setHostPort(u, authority, true)) {
            return false;
        }
        pathQueryFragment(u, s.substring(end));
        return true;
    }

    /** {@code host[:port]}; with {@code withPort} false a port is not read. */
    static boolean setHostPort(JsUrl u, String hp, boolean withPort) {
        boolean special = u.special();
        String h;
        String p = null;
        if (hp.startsWith("[")) {
            int close = hp.indexOf(']');
            if (close < 0) {
                return false;
            }
            h = hp.substring(0, close + 1);
            String after = hp.substring(close + 1);
            if (!after.isEmpty()) {
                if (after.charAt(0) != ':') {
                    return false;
                }
                p = after.substring(1);
            }
        } else {
            int colon = hp.indexOf(':');
            h = colon < 0 ? hp : hp.substring(0, colon);
            p = colon < 0 ? null : hp.substring(colon + 1);
        }
        if (h.isEmpty() && special) {
            return false;
        }
        String parsed = h.isEmpty() ? "" : parseHost(h, special);
        if (parsed == null) {
            return false;
        }
        int port = u.port;
        if (withPort) {
            port = -1;
            if (p != null && !p.isEmpty()) {
                port = parsePort(p);
                if (port < 0) {
                    return false;
                }
            }
        }
        u.host = parsed;
        u.port = defaultPort(u.scheme, port);
        return true;
    }

    /** Digits only, at most 65535; -1 otherwise. */
    static int parsePort(String p) {
        if (p.isEmpty() || !p.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return -1;
        }
        String digits = p.replaceFirst("^0+(?=.)", "");
        return digits.length() > 5 || Integer.parseInt(digits) > 65535 ? -1 : Integer.parseInt(digits);
    }

    static int defaultPort(String scheme, int port) {
        Integer d = SPECIAL.get(scheme);
        return d != null && d == port ? -1 : port;
    }

    private static final String FORBIDDEN_HOST = "\0\t\n\r #/:<>?@[\\]^|";

    static String parseHost(String h, boolean special) {
        if (h.startsWith("[")) {
            if (!h.endsWith("]")) {
                return null;
            }
            String v6 = ipv6(h.substring(1, h.length() - 1));
            return v6 == null ? null : "[" + v6 + "]";
        }
        if (!special) {
            for (int i = 0; i < h.length(); i++) {
                if (FORBIDDEN_HOST.indexOf(h.charAt(i)) >= 0) {
                    return null;
                }
            }
            return encode(h, C0);
        }
        String domain = new String(percentDecode(h), StandardCharsets.UTF_8);
        String ascii;
        if (domain.chars().allMatch(c -> c < 0x80)) {
            ascii = domain.toLowerCase(Locale.ROOT);
        } else {
            try {
                ascii = IDN.toASCII(domain, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if (ascii.isEmpty()) {
            return null;
        }
        for (int i = 0; i < ascii.length(); i++) {
            char c = ascii.charAt(i);
            if (FORBIDDEN_HOST.indexOf(c) >= 0 || c == '%' || c < 0x20 || c == 0x7F) {
                return null;
            }
        }
        List<String> parts = new ArrayList<>(List.of(ascii.split("\\.", -1)));
        if (parts.size() > 1 && parts.get(parts.size() - 1).isEmpty()) {
            parts.remove(parts.size() - 1);
        }
        if (!isNumber(parts.get(parts.size() - 1))) {
            return ascii;
        }
        return ipv4(parts);
    }

    private static boolean isNumber(String part) {
        if (part.isEmpty()) {
            return false;
        }
        if (part.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return true;
        }
        return ipv4Number(part) >= 0;
    }

    /** A decimal, {@code 0x} hex or leading-zero octal part; -1 when it is none. */
    private static long ipv4Number(String part) {
        if (part.isEmpty()) {
            return -1;
        }
        int radix = 10;
        String digits = part;
        if (part.length() > 1 && (part.startsWith("0x") || part.startsWith("0X"))) {
            radix = 16;
            digits = part.substring(2);
            if (digits.isEmpty()) {
                return 0;
            }
        } else if (part.length() > 1 && part.charAt(0) == '0') {
            radix = 8;
            digits = part.substring(1);
        }
        try {
            return Long.parseLong(digits, radix);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String ipv4(List<String> parts) {
        if (parts.size() > 4) {
            return null;
        }
        long[] n = new long[parts.size()];
        for (int i = 0; i < n.length; i++) {
            n[i] = ipv4Number(parts.get(i));
            if (n[i] < 0 || (i < n.length - 1 && n[i] > 255)) {
                return null;
            }
        }
        long last = n[n.length - 1];
        if (last >= Math.pow(256, 5 - n.length)) {
            return null;
        }
        long address = last;
        for (int i = 0; i < n.length - 1; i++) {
            address += n[i] << (8 * (3 - i));
        }
        return (address >> 24 & 255) + "." + (address >> 16 & 255) + "." + (address >> 8 & 255) + "." + (address & 255);
    }

    /** The compressed form of an IPv6 address, or null when it is not one. */
    private static String ipv6(String s) {
        if (s.isEmpty() || !s.chars().allMatch(c -> Character.digit(c, 16) >= 0 || c == ':' || c == '.')) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = java.net.InetAddress.getByName("[" + s + "]").getAddress();
        } catch (Exception e) {
            return null;
        }
        if (bytes.length != 16) {
            // an IPv4-mapped address comes back as its 4 bytes
            byte[] mapped = new byte[16];
            mapped[10] = (byte) 0xFF;
            mapped[11] = (byte) 0xFF;
            System.arraycopy(bytes, 0, mapped, 12, 4);
            bytes = mapped;
        }
        int[] groups = new int[8];
        for (int i = 0; i < 8; i++) {
            groups[i] = ((bytes[2 * i] & 0xFF) << 8) | (bytes[2 * i + 1] & 0xFF);
        }
        int bestStart = -1;
        int bestLen = 1;
        for (int i = 0; i < 8; ) {
            if (groups[i] != 0) {
                i++;
                continue;
            }
            int j = i;
            while (j < 8 && groups[j] == 0) j++;
            if (j - i > bestLen) {
                bestStart = i;
                bestLen = j - i;
            }
            i = j;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == bestStart) {
                sb.append(i == 0 ? "::" : ":");
                i += bestLen - 1;
                continue;
            }
            sb.append(Integer.toHexString(groups[i]));
            if (i < 7) {
                sb.append(':');
            }
        }
        return sb.toString();
    }

    static void pathQueryFragment(JsUrl u, String s) {
        int hash = s.indexOf('#');
        int q = s.indexOf('?');
        int end = q >= 0 && (hash < 0 || q < hash) ? q : (hash >= 0 ? hash : s.length());
        u.path = normalizePath(s.substring(0, end), u.special(), u.host != null);
        queryFragment(u, s.substring(end));
    }

    private static void queryFragment(JsUrl u, String s) {
        int hash = s.indexOf('#');
        String q = hash >= 0 ? s.substring(0, hash) : s;
        if (q.startsWith("?")) {
            u.query = encode(q.substring(1), u.special() ? SPECIAL_QUERY : QUERY);
        }
        u.fragment = hash >= 0 ? encode(s.substring(hash + 1), FRAGMENT) : null;
    }

    static String normalizePath(String p, boolean special, boolean hasHost) {
        if (p.isEmpty()) {
            return special ? "/" : "";
        }
        String s = special ? p.replace('\\', '/') : p;
        if (s.charAt(0) != '/') {
            s = "/" + s;
        }
        String[] segments = s.split("/", -1);
        List<String> out = new ArrayList<>();
        for (int i = 1; i < segments.length; i++) {
            String seg = segments[i];
            boolean last = i == segments.length - 1;
            String lower = seg.toLowerCase(Locale.ROOT);
            if (lower.equals("..") || lower.equals(".%2e") || lower.equals("%2e.") || lower.equals("%2e%2e")) {
                if (!out.isEmpty()) {
                    out.remove(out.size() - 1);
                }
                if (last) {
                    out.add("");
                }
            } else if (lower.equals(".") || lower.equals("%2e")) {
                if (last) {
                    out.add("");
                }
            } else {
                out.add(encode(seg, PATH));
            }
        }
        return "/" + String.join("/", out);
    }

    //=== percent-encoding =============================================================================================

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /** UTF-8 percent-encodes what is outside ASCII or in {@code set}; a lone surrogate encodes as U+FFFD. */
    static String encode(String s, IntPredicate set) {
        StringBuilder sb = null;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int n = Character.charCount(cp);
            if (cp < 0x80 && !set.test(cp)) {
                if (sb != null) {
                    sb.append((char) cp);
                }
            } else {
                if (sb == null) {
                    sb = new StringBuilder(s.length() + 16).append(s, 0, i);
                }
                if (Character.isSurrogate((char) cp) && n == 1) {
                    cp = 0xFFFD;
                }
                for (byte b : new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8)) {
                    sb.append('%').append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
                }
            }
            i += n;
        }
        return sb == null ? s : sb.toString();
    }

    /** application/x-www-form-urlencoded: space is {@code +}, and only {@code *-._} and alphanumerics stay. */
    static String encodeForm(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int n = Character.charCount(cp);
            if (cp == ' ') {
                sb.append('+');
            } else if ((cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z') || (cp >= '0' && cp <= '9') || "*-._".indexOf(cp) >= 0) {
                sb.append((char) cp);
            } else {
                if (Character.isSurrogate((char) cp) && n == 1) {
                    cp = 0xFFFD;
                }
                for (byte b : new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8)) {
                    sb.append('%').append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
                }
            }
            i += n;
        }
        return sb.toString();
    }

    /** A {@code %} not followed by two hex digits stays as written. */
    static byte[] percentDecode(String s) {
        byte[] in = s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        for (int i = 0; i < in.length; i++) {
            byte b = in[i];
            if (b == '%' && i + 2 < in.length && Character.digit(in[i + 1], 16) >= 0 && Character.digit(in[i + 2], 16) >= 0) {
                out.write(Character.digit(in[i + 1], 16) * 16 + Character.digit(in[i + 2], 16));
                i += 2;
            } else {
                out.write(b);
            }
        }
        return out.toByteArray();
    }

}
