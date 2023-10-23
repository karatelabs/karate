/*
 * The MIT License
 *
 * Copyright 2023 Karate Labs Inc.
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
package com.intuit.karate.playwright.driver.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KarateTokenParser {

    private static final Pattern WILDCARD_LOCATOR_PATTERN = Pattern.compile("\\{(\\^)?([a-zA-Z*/]+)?(:\\d+)?\\}(.+)");

    public static String parse(String karateToken, KarateTokenParserListener listener) {
        Matcher matcher = WILDCARD_LOCATOR_PATTERN.matcher(karateToken);
        if (matcher.find()) {
            boolean isContain = matcher.group(1) != null;
            String tag = matcher.group(2);
            String indexStr = matcher.group(3);
            String text = matcher.group(4);

            return listener.onText(isContain, text, Optional.ofNullable(tag), Optional.ofNullable(indexStr).map(s -> Integer.parseInt(s.substring(1) /* remove the initial : */)));
        } else {
            return karateToken.startsWith("./") ? "xpath=" + karateToken : karateToken;
        }
    }

    public static String toPlaywrightToken(String karateToken) {
        return parse(karateToken, KarateTokenParser::toXPathToken);
    }

    public static interface KarateTokenParserListener {

        String onText(boolean isContain, String text, Optional<String> tag, Optional<Integer> index);
    }

// Example of alternative implementation
    // private static String toPlaywrightToken(boolean isContain, String text, Optional<String> tag, Optional<Integer> index) {
    //     String token = tag.orElse("*") + ":"+ (isContain ? "text" : "text-is") + "('" + text + "')";
    //     return index.map(idx -> ":nth-match("+token+","+idx+")").orElse(token);
    // }
    private static String toXPathToken(boolean isContain, String text, Optional<String> tag, Optional<Integer> index) {
        String token = "//" + tag.orElse("*") + (isContain ? "[contains(normalize-space(text()),'" + text + "')]" : "[normalize-space(text())='" + text + "']");
        return index.map(idx -> "/(" + token + ")[" + idx + "]").orElse(token);
    }

}
