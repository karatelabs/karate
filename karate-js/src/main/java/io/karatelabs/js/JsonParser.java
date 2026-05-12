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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict RFC 8259 / ECMA-404 JSON parser. Public entry: {@link #parse(String)}.
 * Returns {@link Map}/{@link List}/{@link String}/{@link Integer}/{@link Long}/
 * {@link BigInteger}/{@link Double}/{@link Boolean}/{@code null}. Throws
 * {@link JsErrorException} (SyntaxError shape) on invalid input.
 *
 * <p>Numeric narrowing: integer values in {@code int} range → {@link Integer};
 * integer values fitting in {@code long} → {@link Long}; larger integers →
 * {@link BigInteger}; any literal with a fractional part or exponent →
 * {@link Double}. See {@code JsonParserTest} for the pinned contract.
 *
 * <p>Designed to be allocated per call ({@link ParseState} is a private,
 * non-static inner class); the {@code parse} entry point is thread-safe by
 * construction — no shared mutable state.
 */
public final class JsonParser {

    private JsonParser() {
        // static utility
    }

    public static Object parse(String input) {
        if (input == null) {
            throw JsErrorException.syntaxError("Unexpected end of JSON input");
        }
        ParseState state = new ParseState(input);
        state.skipWs();
        Object value = state.parseValue();
        state.skipWs();
        if (state.pos != input.length()) {
            throw state.syntaxError("Unexpected token after JSON value");
        }
        return value;
    }

    private static final class ParseState {

        private final String s;
        private final int len;
        private int pos;

        ParseState(String s) {
            this.s = s;
            this.len = s.length();
        }

        Object parseValue() {
            if (pos >= len) {
                throw syntaxError("Unexpected end of JSON input");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                case 'f':
                    return parseBoolean();
                case 'n':
                    return parseNull();
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return parseNumber();
                    }
                    throw syntaxError("Unexpected token '" + c + "' in JSON");
            }
        }

        private Map<String, Object> parseObject() {
            // we know s.charAt(pos) == '{'
            pos++;
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (pos < len && s.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                if (pos >= len || s.charAt(pos) != '"') {
                    throw syntaxError("Expected string key in object");
                }
                String key = parseString();
                skipWs();
                if (pos >= len || s.charAt(pos) != ':') {
                    throw syntaxError("Expected ':' after object key");
                }
                pos++;
                skipWs();
                Object value = parseValue();
                // RFC 8259: behavior of duplicate keys is unspecified; we keep
                // the last value (matches json-smart parseKeepingOrder).
                map.put(key, value);
                skipWs();
                if (pos >= len) {
                    throw syntaxError("Unexpected end of JSON input in object");
                }
                char c = s.charAt(pos);
                if (c == ',') {
                    pos++;
                    // trailing-comma is invalid per spec: the next iteration
                    // demands a string key, which rejects e.g. {"a":1,}.
                    continue;
                }
                if (c == '}') {
                    pos++;
                    return map;
                }
                throw syntaxError("Expected ',' or '}' in object");
            }
        }

        private List<Object> parseArray() {
            // we know s.charAt(pos) == '['
            pos++;
            List<Object> list = new ArrayList<>();
            skipWs();
            if (pos < len && s.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWs();
                list.add(parseValue());
                skipWs();
                if (pos >= len) {
                    throw syntaxError("Unexpected end of JSON input in array");
                }
                char c = s.charAt(pos);
                if (c == ',') {
                    pos++;
                    // trailing-comma rejected: the next parseValue() lands on
                    // ']' and parseValue's default branch throws.
                    continue;
                }
                if (c == ']') {
                    pos++;
                    return list;
                }
                throw syntaxError("Expected ',' or ']' in array");
            }
        }

        private String parseString() {
            // we know s.charAt(pos) == '"'
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < len) {
                char c = s.charAt(pos);
                if (c == '"') {
                    pos++;
                    return sb.toString();
                }
                if (c == '\\') {
                    pos++;
                    if (pos >= len) {
                        throw syntaxError("Unexpected end of JSON input in string escape");
                    }
                    char esc = s.charAt(pos);
                    pos++;
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            sb.append(parseHex4());
                            break;
                        default:
                            throw syntaxError("Invalid escape '\\" + esc + "' in JSON string");
                    }
                    continue;
                }
                if (c < 0x20) {
                    throw syntaxError("Invalid control character in JSON string");
                }
                sb.append(c);
                pos++;
            }
            throw syntaxError("Unterminated JSON string");
        }

        private char parseHex4() {
            if (pos + 4 > len) {
                throw syntaxError("Invalid \\u escape in JSON string");
            }
            int v = 0;
            for (int i = 0; i < 4; i++) {
                char h = s.charAt(pos + i);
                int d;
                if (h >= '0' && h <= '9') d = h - '0';
                else if (h >= 'a' && h <= 'f') d = 10 + (h - 'a');
                else if (h >= 'A' && h <= 'F') d = 10 + (h - 'A');
                else throw syntaxError("Invalid hex digit '" + h + "' in \\u escape");
                v = (v << 4) | d;
            }
            pos += 4;
            return (char) v;
        }

        private Object parseNumber() {
            int start = pos;
            boolean isFloat = false;
            if (s.charAt(pos) == '-') {
                pos++;
                if (pos >= len) {
                    throw syntaxError("Invalid number: bare '-'");
                }
            }
            // integer part
            char c = s.charAt(pos);
            if (c == '0') {
                pos++;
            } else if (c >= '1' && c <= '9') {
                pos++;
                while (pos < len && (s.charAt(pos) >= '0' && s.charAt(pos) <= '9')) {
                    pos++;
                }
            } else {
                throw syntaxError("Invalid number");
            }
            // fraction
            if (pos < len && s.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                int fracStart = pos;
                while (pos < len && (s.charAt(pos) >= '0' && s.charAt(pos) <= '9')) {
                    pos++;
                }
                if (pos == fracStart) {
                    throw syntaxError("Invalid number: missing fraction digits");
                }
            }
            // exponent
            if (pos < len && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < len && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                    pos++;
                }
                int expStart = pos;
                while (pos < len && (s.charAt(pos) >= '0' && s.charAt(pos) <= '9')) {
                    pos++;
                }
                if (pos == expStart) {
                    throw syntaxError("Invalid number: missing exponent digits");
                }
            }
            String lit = s.substring(start, pos);
            if (isFloat) {
                return Double.parseDouble(lit);
            }
            // integer literal — narrow to Integer / Long / BigInteger to match
            // the json-smart contract (see JsonNumberContractTest).
            // Negative-zero integer form: json-smart returns Integer 0 (sign
            // lost). We match that to keep instanceof-Integer call sites in
            // karate-core stable (OAuth2Token.fromMap, W3cDriver, etc.).
            try {
                long v = Long.parseLong(lit);
                if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
                    return (int) v;
                }
                return v;
            } catch (NumberFormatException nfe) {
                return new BigInteger(lit);
            }
        }

        private Boolean parseBoolean() {
            if (s.charAt(pos) == 't') {
                if (pos + 4 <= len && s.charAt(pos + 1) == 'r' && s.charAt(pos + 2) == 'u' && s.charAt(pos + 3) == 'e') {
                    pos += 4;
                    return Boolean.TRUE;
                }
                throw syntaxError("Invalid literal — expected 'true'");
            }
            if (pos + 5 <= len && s.charAt(pos + 1) == 'a' && s.charAt(pos + 2) == 'l' && s.charAt(pos + 3) == 's' && s.charAt(pos + 4) == 'e') {
                pos += 5;
                return Boolean.FALSE;
            }
            throw syntaxError("Invalid literal — expected 'false'");
        }

        private Object parseNull() {
            if (pos + 4 <= len && s.charAt(pos + 1) == 'u' && s.charAt(pos + 2) == 'l' && s.charAt(pos + 3) == 'l') {
                pos += 4;
                return null;
            }
            throw syntaxError("Invalid literal — expected 'null'");
        }

        void skipWs() {
            while (pos < len) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    return;
                }
            }
        }

        JsErrorException syntaxError(String message) {
            return JsErrorException.syntaxError(message + " at position " + pos);
        }

    }

}
