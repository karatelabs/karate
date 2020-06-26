/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.jersey.karate.uri.internal;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;



/**
 * Jersey's UriTemplateParser without any other dependencies, modified to be (almost) self-contained.
 *
 * Source: https://github.com/eclipse-ee4j/jersey/blob/master/core-common/src/main/java/org/glassfish/jersey/uri/internal/UriTemplateParser.java @ 12a0573
 * */

/**
 * A URI template parser that parses JAX-RS specific URI templates.
 *
 * @author Paul Sandoz
 * @author Gerard Davison (gerard.davison at oracle.com)
 */
public class UriTemplateParser {

    /* package */ static final int[] EMPTY_INT_ARRAY = new int[0];
    private static final Set<Character> RESERVED_REGEX_CHARACTERS = initReserved();
    private static final String[] HEX_TO_UPPERCASE_REGEX = initHexToUpperCaseRegex();

    private static Set<Character> initReserved() {
        char[] reserved = {
                '.', '^', '&', '!',
                '?', '-', ':', '<',
                '(', '[', '$', '=',
                ')', ']', ',', '>',
                '*', '+', '|'};

        Set<Character> s = new HashSet<Character>(reserved.length);
        for (char c : reserved) {
            s.add(c);
        }
        return s;
    }

    /**
     * Default URI template value regexp pattern.
     */
    public static final Pattern TEMPLATE_VALUE_PATTERN = Pattern.compile("[^/]+");

    private final String template;
    private final StringBuffer regex = new StringBuffer();
    private final StringBuffer normalizedTemplate = new StringBuffer();
    private final StringBuffer literalCharactersBuffer = new StringBuffer();
    private final Pattern pattern;
    private final List<String> names = new ArrayList<String>();
    private final List<Integer> groupCounts = new ArrayList<Integer>();
    private final Map<String, Pattern> nameToPattern = new HashMap<String, Pattern>();
    private int numOfExplicitRegexes;
    private int skipGroup;

    private int literalCharacters;

    /**
     * Parse a template.
     *
     * @param template the template.
     * @throws IllegalArgumentException if the template is null, an empty string
     *                                  or does not conform to a JAX-RS URI template.
     */
    public UriTemplateParser(final String template) throws IllegalArgumentException {
        if (template == null || template.isEmpty()) {
            throw new IllegalArgumentException("Template is null or has zero length");
        }

        this.template = template;
        parse(new CharacterIterator(template));
        try {
            pattern = Pattern.compile(regex.toString());
        } catch (PatternSyntaxException ex) {
            throw new IllegalArgumentException("Invalid syntax for the template expression '"
                    + regex + "'",
                    ex
            );
        }
    }

    /**
     * Get the template.
     *
     * @return the template.
     */
    public final String getTemplate() {
        return template;
    }

    /**
     * Get the pattern.
     *
     * @return the pattern.
     */
    public final Pattern getPattern() {
        return pattern;
    }

    /**
     * Get the normalized template.
     * <p>
     * A normalized template is a template without any explicit regular
     * expressions.
     *
     * @return the normalized template.
     */
    public final String getNormalizedTemplate() {
        return normalizedTemplate.toString();
    }

    /**
     * Get the map of template names to patterns.
     *
     * @return the map of template names to patterns.
     */
    public final Map<String, Pattern> getNameToPattern() {
        return nameToPattern;
    }

    /**
     * Get the list of template names.
     *
     * @return the list of template names.
     */
    public final List<String> getNames() {
        return names;
    }

    /**
     * Get the capturing group counts for each template variable.
     *
     * @return the capturing group counts.
     */
    public final List<Integer> getGroupCounts() {
        return groupCounts;
    }

    /**
     * Get the group indexes to capturing groups.
     * <p>
     * Any nested capturing groups will be ignored and the
     * the group index will refer to the top-level capturing
     * groups associated with the templates variables.
     *
     * @return the group indexes to capturing groups.
     */
    public final int[] getGroupIndexes() {
        if (names.isEmpty()) {
            return EMPTY_INT_ARRAY;
        }

        int[] indexes = new int[names.size()];
        indexes[0] = 0 + groupCounts.get(0);
        for (int i = 1; i < indexes.length; i++) {
            indexes[i] = indexes[i - 1] + groupCounts.get(i);
        }

        return indexes;
    }

    /**
     * Get the number of explicit regular expressions.
     *
     * @return the number of explicit regular expressions.
     */
    public final int getNumberOfExplicitRegexes() {
        return numOfExplicitRegexes;
    }

    /**
     * Get the number of regular expression groups
     *
     * @return the number of regular expressions groups
     *
     * @since 2.9
     */
    public final int getNumberOfRegexGroups() {
        if (groupCounts.isEmpty()) {
            return 0;
        } else {
            int[] groupIndex = getGroupIndexes();
            return groupIndex[groupIndex.length - 1] + skipGroup;
        }
    }

    /**
     * Get the number of literal characters.
     *
     * @return the number of literal characters.
     */
    public final int getNumberOfLiteralCharacters() {
        return literalCharacters;
    }

    /**
     * Encode literal characters of a template.
     *
     * @param characters the literal characters
     * @return the encoded literal characters.
     */
    protected String encodeLiteralCharacters(final String characters) {
        return characters;
    }

    private void parse(final CharacterIterator ci) {
        try {
            while (ci.hasNext()) {
                char c = ci.next();
                if (c == '{') {
                    processLiteralCharacters();
                    skipGroup = parseName(ci, skipGroup);
                } else {
                    literalCharactersBuffer.append(c);
                }
            }
            processLiteralCharacters();
        } catch (NoSuchElementException ex) {
            throw new IllegalArgumentException(MessageFormat.format(ERROR_TEMPLATE_PARSER_INVALID_SYNTAX_TERMINATED,
                    template), ex);
        }
    }

    private void processLiteralCharacters() {
        if (literalCharactersBuffer.length() > 0) {
            literalCharacters += literalCharactersBuffer.length();

            String s = encodeLiteralCharacters(literalCharactersBuffer.toString());

            normalizedTemplate.append(s);

            // Escape if reserved regex character
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (RESERVED_REGEX_CHARACTERS.contains(c)) {
                    regex.append("\\");
                    regex.append(c);
                } else if (c == '%') {
                    final char c1 = s.charAt(i + 1);
                    final char c2 = s.charAt(i + 2);
                    if (isHexCharacter(c1) && isHexCharacter(c2)) {
                        regex.append("%").append(HEX_TO_UPPERCASE_REGEX[c1]).append(HEX_TO_UPPERCASE_REGEX[c2]);
                        i += 2;
                    }
                } else {
                    regex.append(c);
                }
            }
            literalCharactersBuffer.setLength(0);
        }
    }

    private static String[] initHexToUpperCaseRegex() {
        String[] table = new String[0x80];
        for (int i = 0; i < table.length; i++) {
            table[i] = String.valueOf((char) i);
        }

        for (char c = 'a'; c <= 'f'; c++) {
            // initialize table values: table[a] = ([aA]) ...
            table[c] = "[" + c + (char) (c - 'a' + 'A') + "]";
        }

        for (char c = 'A'; c <= 'F'; c++) {
            // initialize table values: table[A] = ([aA]) ...
            table[c] = "[" + (char) (c - 'A' + 'a') + c + "]";
        }
        return table;
    }

    private int parseName(final CharacterIterator ci, int skipGroup) {
        char c = consumeWhiteSpace(ci);

        char paramType = 'p'; // Normal path param unless otherwise stated
        StringBuilder nameBuffer = new StringBuilder();

        // Look for query or matrix types
        if (c == '?' || c == ';') {
            paramType = c;
            c = ci.next();
        }

        if (Character.isLetterOrDigit(c) || c == '_') {
            // Template name character
            nameBuffer.append(c);
        } else {
            throw new IllegalArgumentException(MessageFormat.format(ERROR_TEMPLATE_PARSER_ILLEGAL_CHAR_START_NAME, c, ci.pos(),
                    template));
        }

        String nameRegexString = "";
        while (true) {
            c = ci.next();
            // "\\{(\\w[-\\w\\.]*)
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
                // Template name character
                nameBuffer.append(c);
            } else if (c == ',' && paramType != 'p') {
                // separator allowed for non-path parameter names
                nameBuffer.append(c);
            } else if (c == ':' && paramType == 'p') {
                nameRegexString = parseRegex(ci);
                break;
            } else if (c == '}') {
                break;
            } else if (c == ' ') {
                c = consumeWhiteSpace(ci);

                if (c == ':') {
                    nameRegexString = parseRegex(ci);
                    break;
                } else if (c == '}') {
                    break;
                } else {
                    // Error
                    throw new IllegalArgumentException(
                            MessageFormat.format(ERROR_TEMPLATE_PARSER_ILLEGAL_CHAR_AFTER_NAME, c, ci.pos(), template));
                }
            } else {
                throw new IllegalArgumentException(
                        MessageFormat.format(ERROR_TEMPLATE_PARSER_ILLEGAL_CHAR_PART_OF_NAME, c, ci.pos(), template));
            }
        }

        String name = nameBuffer.toString();
        Pattern namePattern;
        try {
            if (paramType == '?' || paramType == ';') {
                String[] subNames = name.split(",\\s?");

                // Build up the regex for each of these properties
                StringBuilder regexBuilder = new StringBuilder(paramType == '?' ? "\\?" : ";");
                String separator = paramType == '?' ? "\\&" : ";/\\?";

                // Start a group because each parameter could repeat
                //                names.add("__" + (paramType == '?' ? "query" : "matrix"));

                boolean first = true;

                regexBuilder.append("(");
                for (String subName : subNames) {
                    regexBuilder.append("(&?");
                    regexBuilder.append(subName);
                    regexBuilder.append("(=([^");
                    regexBuilder.append(separator);
                    regexBuilder.append("]*))?");
                    regexBuilder.append(")");
                    if (!first) {
                        regexBuilder.append("|");
                    }

                    names.add(subName);
                    groupCounts.add(
                            first ? 5 : 3);
                    first = false;
                }

                //                groupCounts.add(1);
                skipGroup = 1;

                // Knock of last bar
                regexBuilder.append(")*");

                namePattern = Pattern.compile(regexBuilder.toString());

                // Make sure we display something useful
                name = paramType + name;
            } else {
                names.add(name);
                //               groupCounts.add(1 + skipGroup);

                if (!nameRegexString.isEmpty()) {
                    numOfExplicitRegexes++;
                }
                namePattern = (nameRegexString.isEmpty())
                        ? TEMPLATE_VALUE_PATTERN : Pattern.compile(nameRegexString);
                if (nameToPattern.containsKey(name)) {
                    if (!nameToPattern.get(name).equals(namePattern)) {
                        throw new IllegalArgumentException(
                                MessageFormat.format(ERROR_TEMPLATE_PARSER_NAME_MORE_THAN_ONCE, name, template));
                    }
                } else {
                    nameToPattern.put(name, namePattern);
                }

                // Determine group count of pattern
                Matcher m = namePattern.matcher("");
                int g = m.groupCount();
                groupCounts.add(1 + skipGroup);
                skipGroup = g;
            }

            regex.append('(')
                    .append(namePattern)
                    .append(')');

            normalizedTemplate.append('{')
                    .append(name)
                    .append('}');
        } catch (PatternSyntaxException ex) {
            throw new IllegalArgumentException(
                    MessageFormat.format(ERROR_TEMPLATE_PARSER_INVALID_SYNTAX, nameRegexString, name, template), ex);
        }

        // Tell the next time through the loop how many to skip
        return skipGroup;
    }

    private String parseRegex(final CharacterIterator ci) {
        StringBuilder regexBuffer = new StringBuilder();

        int braceCount = 1;
        while (true) {
            char c = ci.next();
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    break;
                }
            }
            regexBuffer.append(c);
        }

        return regexBuffer.toString().trim();
    }

    private char consumeWhiteSpace(final CharacterIterator ci) {
        char c;
        do {
            c = ci.next();
        } while (Character.isWhitespace(c));

        return c;
    }

    //
    public static final String HEX_CHARACTERS = "0123456789abcdefABCDEF";
    private static boolean isHexCharacter(char c) {
        return HEX_CHARACTERS.indexOf(c) != -1;
    }

    public static final String ERROR_TEMPLATE_PARSER_INVALID_SYNTAX_TERMINATED = "Invalid syntax in the template \"{0}\". Check if a path parameter is terminated with a \"}\".";
    public static final String ERROR_TEMPLATE_PARSER_ILLEGAL_CHAR_START_NAME = "Illegal character \"{0}\" at position {1} is not allowed as a start of a name in a path template \"{2}\".";
    public static final String ERROR_TEMPLATE_PARSER_ILLEGAL_CHAR_AFTER_NAME = "Illegal character \"{0}\" at position {1} is not allowed after a name in a path template \"{2}\".";
    public static final String ERROR_TEMPLATE_PARSER_ILLEGAL_CHAR_PART_OF_NAME = "Illegal character \"{0}\" at position {1} is not allowed as a part of a name in a path template \"{2}\".";
    public static final String ERROR_TEMPLATE_PARSER_NAME_MORE_THAN_ONCE = "The name \"{0}\" is declared more than once with different regular expressions in a path template \"{1}\".";
    public static final String ERROR_TEMPLATE_PARSER_INVALID_SYNTAX = "Invalid syntax for the expression \"{0}\" associated with the name \"{1}\" in a path template \"{2}\".";
}
