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
package io.karatelabs.output;

import io.karatelabs.common.Json;
import io.karatelabs.js.JavaCallable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Declarative HTTP log masking. Applied by {@link HttpLogger} when formatting
 * request / response payloads for the report buffer and the console.
 * <p>
 * Constructed from the map shape stored under {@code configure logging = { mask: {...} }}:
 * <pre>
 * mask: {
 *   headers:     ['Authorization', 'Cookie'],     // matched case-insensitively
 *   jsonPaths:   ['$.password', '$.token'],       // simple "$.x.y" / "$..x" paths
 *   patterns:    [{ regex: '\\bBearer ...', replacement: 'Bearer ***' }],
 *   replacement: '***',                           // default for headers / jsonPaths
 *   enableForUri: function(uri) { return !uri.contains('/health') }   // optional gate
 * }
 * </pre>
 */
public class LogMask {

    private static final Logger logger = LogContext.RUNTIME_LOGGER;
    private static final Set<String> KNOWN_KEYS = Set.of(
            "headers", "jsonPaths", "patterns", "replacement", "enableForUri");

    private static final String DEFAULT_REPLACEMENT = "***";

    private final Set<String> maskedHeadersLower;
    private final List<String> jsonPaths;
    private final List<PatternRule> patternRules;
    private final String replacement;
    private final JavaCallable enableForUri;

    private LogMask(Set<String> maskedHeadersLower,
                    List<String> jsonPaths,
                    List<PatternRule> patternRules,
                    String replacement,
                    JavaCallable enableForUri) {
        this.maskedHeadersLower = maskedHeadersLower;
        this.jsonPaths = jsonPaths;
        this.patternRules = patternRules;
        this.replacement = replacement;
        this.enableForUri = enableForUri;
    }

    @SuppressWarnings("unchecked")
    public static LogMask fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        for (String key : map.keySet()) {
            if (!KNOWN_KEYS.contains(key)) {
                logger.warn("configure logging.mask: unknown key '{}' (known: {})", key, KNOWN_KEYS);
            }
        }
        String replacement = map.get("replacement") instanceof String s ? s : DEFAULT_REPLACEMENT;
        Set<String> headers = new LinkedHashSet<>();
        if (map.get("headers") instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    headers.add(item.toString().toLowerCase());
                }
            }
        }
        List<String> jsonPaths = new ArrayList<>();
        if (map.get("jsonPaths") instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    jsonPaths.add(item.toString());
                }
            }
        }
        List<PatternRule> patternRules = new ArrayList<>();
        if (map.get("patterns") instanceof List<?> list) {
            int idx = 0;
            for (Object item : list) {
                if (item instanceof Map<?, ?> pm) {
                    Object regex = pm.get("regex");
                    if (regex == null) {
                        logger.warn("configure logging.mask.patterns[{}] missing 'regex' — skipped", idx);
                        idx++;
                        continue;
                    }
                    Object rep = pm.get("replacement");
                    String repStr = rep == null ? replacement : rep.toString();
                    try {
                        patternRules.add(new PatternRule(Pattern.compile(regex.toString()), repStr));
                    } catch (PatternSyntaxException e) {
                        // Skip rather than fail-fast — a typo in one rule shouldn't blow up
                        // the whole config-js eval. The warn names the entry so the user
                        // can find it. Other rules in the same mask still apply.
                        logger.warn("configure logging.mask.patterns[{}] invalid regex '{}': {} — skipped",
                                idx, regex, e.getDescription());
                    }
                }
                idx++;
            }
        }
        JavaCallable enableForUri = map.get("enableForUri") instanceof JavaCallable c ? c : null;
        if (headers.isEmpty() && jsonPaths.isEmpty() && patternRules.isEmpty()) {
            // User provided a mask map but every rule list is empty / invalid. Without this
            // warn the mask silently does nothing, which was hard to debug in #2826.
            logger.warn("configure logging.mask: no usable rules — set at least one of "
                    + "headers / jsonPaths / patterns. mask is OFF.");
            return null;
        }
        return new LogMask(headers, jsonPaths, patternRules, replacement, enableForUri);
    }

    /**
     * One-line description of mask rule counts, e.g.
     * {@code "3 headers, 2 jsonPaths, 1 pattern"}. Used by the per-scenario
     * "mask active" debug log so users can verify their config compiled.
     */
    public String describe() {
        int h = maskedHeadersLower.size();
        int j = jsonPaths.size();
        int p = patternRules.size();
        return h + (h == 1 ? " header, " : " headers, ")
                + j + (j == 1 ? " jsonPath, " : " jsonPaths, ")
                + p + (p == 1 ? " pattern" : " patterns");
    }

    public boolean enabledForUri(String uri) {
        if (enableForUri == null) {
            return true;
        }
        try {
            Object result = enableForUri.call(null, uri);
            if (result instanceof Boolean b) return b;
            // Truthy semantics: non-null, non-empty-string, non-zero treated as true.
            if (result == null) return false;
            if (result instanceof String s) return !s.isEmpty();
            if (result instanceof Number n) return n.doubleValue() != 0;
            return true;
        } catch (Exception e) {
            // Swallow JS errors and default to "mask everything" — safer than leaking.
            return true;
        }
    }

    public boolean isHeaderMasked(String headerName) {
        return headerName != null && maskedHeadersLower.contains(headerName.toLowerCase());
    }

    public String maskHeader(String name, String value) {
        if (isHeaderMasked(name)) {
            return replacement;
        }
        return applyPatterns(value);
    }

    public String maskBody(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        String result = body;
        if (!jsonPaths.isEmpty() && looksLikeJson(result)) {
            try {
                Object parsed = Json.parseLenient(result);
                boolean changed = false;
                for (String path : jsonPaths) {
                    changed |= applyJsonPath(parsed, path, replacement);
                }
                if (changed) {
                    // Re-serialize. Keep pretty-printing decision to the caller (HttpLogger).
                    result = io.karatelabs.common.StringUtils.formatJson(parsed);
                }
            } catch (Exception e) {
                // body wasn't valid JSON despite the heuristic — fall through to pattern application
            }
        }
        return applyPatterns(result);
    }

    private String applyPatterns(String text) {
        if (text == null || patternRules.isEmpty()) {
            return text;
        }
        String result = text;
        for (PatternRule rule : patternRules) {
            result = rule.pattern.matcher(result).replaceAll(rule.replacement);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static boolean applyJsonPath(Object root, String path, String replacement) {
        // Minimal subset: "$.a.b.c" (descend) and "$..foo" (recursive search by key).
        if (path == null || path.length() < 2 || path.charAt(0) != '$') {
            return false;
        }
        String rest = path.substring(1);
        if (rest.startsWith("..")) {
            String key = rest.substring(2);
            return replaceKeyRecursive(root, key, replacement);
        }
        if (rest.startsWith(".")) {
            return descendAndReplace(root, rest.substring(1), replacement);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean descendAndReplace(Object node, String dottedKeys, String replacement) {
        if (dottedKeys.isEmpty() || node == null) {
            return false;
        }
        int dot = dottedKeys.indexOf('.');
        String head = dot == -1 ? dottedKeys : dottedKeys.substring(0, dot);
        String tail = dot == -1 ? "" : dottedKeys.substring(dot + 1);
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> mutable = (Map<String, Object>) map;
            if (!mutable.containsKey(head)) {
                return false;
            }
            if (tail.isEmpty()) {
                mutable.put(head, replacement);
                return true;
            }
            return descendAndReplace(mutable.get(head), tail, replacement);
        }
        if (node instanceof List<?> list) {
            boolean changed = false;
            for (Object item : list) {
                changed |= descendAndReplace(item, dottedKeys, replacement);
            }
            return changed;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean replaceKeyRecursive(Object node, String key, String replacement) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> mutable = (Map<String, Object>) map;
            boolean changed = false;
            for (String k : new HashSet<>(mutable.keySet())) {
                if (k.equals(key)) {
                    mutable.put(k, replacement);
                    changed = true;
                } else {
                    changed |= replaceKeyRecursive(mutable.get(k), key, replacement);
                }
            }
            return changed;
        }
        if (node instanceof List<?> list) {
            boolean changed = false;
            for (Object item : list) {
                changed |= replaceKeyRecursive(item, key, replacement);
            }
            return changed;
        }
        return false;
    }

    private static boolean looksLikeJson(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) continue;
            return c == '{' || c == '[';
        }
        return false;
    }

    private static final class PatternRule {
        final Pattern pattern;
        final String replacement;

        PatternRule(Pattern pattern, String replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
        }
    }

}
