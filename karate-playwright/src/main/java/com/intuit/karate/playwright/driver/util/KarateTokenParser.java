package com.intuit.karate.playwright.driver.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KarateTokenParser {

    private static final Pattern WILDCARD_LOCATOR_PATTERN = Pattern.compile("\\{(\\^)?([a-zA-Z*/]+)?(:\\d+)?\\}(.+)");
    public static String parse(String karateToken, KarateTokenParserListener listener) {
        Matcher matcher = WILDCARD_LOCATOR_PATTERN.matcher(karateToken);
        if (matcher.find()) {
            boolean isContain = matcher.group(1)!=null;
            String tag = matcher.group(2);
            String indexStr = matcher.group(3);
            String text= matcher.group(4);

            return listener.onText(isContain, text, Optional.ofNullable(tag), Optional.ofNullable(indexStr).map(s -> Integer.parseInt(s.substring(1) /* remove the initial : */)));
        } else{
            return karateToken.startsWith("./") ? "xpath="+karateToken : karateToken;
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
       String token = "//"+tag.orElse("*") + (isContain ? "[contains(normalize-space(text()),'"+text+"')]" : "[normalize-space(text())='"+text+"']");
       return index.map(idx -> "/("+token+")["+idx+"]").orElse(token);
   }


}
