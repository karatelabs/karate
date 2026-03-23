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
package io.karatelabs.gherkin;

import io.karatelabs.common.Resource;
import io.karatelabs.parser.BaseLexer;
import io.karatelabs.parser.Token;
import io.karatelabs.parser.TokenType;

import static io.karatelabs.parser.TokenType.*;

/**
 * Lexer for Gherkin syntax with embedded JavaScript expressions.
 * Extends BaseLexer to handle Gherkin-specific tokens and states.
 */
public class GherkinLexer extends BaseLexer {

    private GherkinState gState = GherkinState.GHERKIN;

    private enum GherkinState {
        GHERKIN,        // Initial Gherkin state
        GS_DESC,        // Description state (after Feature:, Scenario:, etc.)
        GS_COMMENT,     // Comment state (after #)
        GS_TAGS,        // Tags state (after @)
        GS_TABLE_ROW,   // Table row state (after |)
        GS_DOC_STRING,  // Doc string state (between """)
        GS_STEP,        // Step state (after Given/When/Then/And/But/*)
        GS_STEP_MATCH,  // Match keyword state
        GS_RHS          // Right-hand side (JS expression)
    }

    public GherkinLexer(Resource resource) {
        super(resource);
    }

    @Override
    public Token nextToken() {
        tokenStart = pos;
        tokenLine = line;
        tokenCol = col;
        TokenType type = scanToken();
        int length = pos - tokenStart;
        return new Token(buffer, type, tokenStart, tokenLine, tokenCol, length);
    }

    @Override
    protected TokenType scanToken() {
        if (isAtEnd()) {
            return EOF;
        }

        // Handle Gherkin states
        return scanGherkinToken();
    }

    private TokenType scanGherkinToken() {
        // Whitespace handling applies to all Gherkin states
        char c = peek();

        if (c == ' ' || c == '\t') {
            return scanGherkinWhitespace();
        }

        return switch (gState) {
            case GHERKIN -> scanGherkinInitial();
            case GS_DESC -> scanGherkinDesc();
            case GS_COMMENT -> scanGherkinComment();
            case GS_TAGS -> scanGherkinTags();
            case GS_TABLE_ROW -> scanGherkinTableRow();
            case GS_DOC_STRING -> scanGherkinDocString();
            case GS_STEP -> scanGherkinStep();
            case GS_STEP_MATCH -> scanGherkinStepMatch();
            case GS_RHS -> scanGherkinRhs();
            default -> throw new IllegalStateException("Unexpected state: " + gState);
        };
    }

    private TokenType scanGherkinWhitespace() {
        while (!isAtEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t') {
                advance();
            } else if (c == '\r' || c == '\n') {
                return scanGherkinWhitespaceWithNewline();
            } else {
                break;
            }
        }
        return WS;
    }

    private TokenType scanGherkinWhitespaceWithNewline() {
        while (!isAtEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                advance();
            } else {
                break;
            }
        }
        // After newline, return to appropriate state
        if (gState == GherkinState.GS_COMMENT || gState == GherkinState.GS_DESC
                || gState == GherkinState.GS_TAGS || gState == GherkinState.GS_TABLE_ROW
                || gState == GherkinState.GS_STEP || gState == GherkinState.GS_STEP_MATCH
                || gState == GherkinState.GS_RHS) {
            gState = GherkinState.GHERKIN;
        }
        return WS_LF;
    }

    // ========== GHERKIN State ==========

    private TokenType scanGherkinInitial() {
        char c = peek();

        // Newline handling
        if (c == '\r' || c == '\n') {
            return scanGherkinWhitespaceWithNewline();
        }

        // Comment
        if (c == '#') {
            gState = GherkinState.GS_COMMENT;
            advance();
            return scanGherkinComment();
        }

        // Tags
        if (c == '@') {
            return scanGherkinTag();
        }

        // Table row
        if (c == '|') {
            gState = GherkinState.GS_TABLE_ROW;
            return scanGherkinTableRow();
        }

        // Triple quote for doc string
        if (c == '"' && peek(1) == '"' && peek(2) == '"') {
            advance();
            advance();
            advance();
            gState = GherkinState.GS_DOC_STRING;
            return G_TRIPLE_QUOTE;
        }

        // Keywords - check for Gherkin keywords
        if (isIdentifierStart(c) || c == '*') {
            return scanGherkinKeyword();
        }

        // Fallback - shouldn't normally reach here
        advance();
        return WS;
    }

    private TokenType scanGherkinKeyword() {
        int start = pos;

        // Handle * prefix specially
        if (peek() == '*') {
            advance();
            gState = GherkinState.GS_STEP;
            return G_PREFIX;
        }

        // Scan identifier
        while (!isAtEnd() && isIdentifierPart(peek())) {
            advance();
        }
        String text = source.substring(start, pos);

        // Check for Gherkin prefixes (step keywords)
        if (text.equals("Given") || text.equals("When") || text.equals("Then")
                || text.equals("And") || text.equals("But")) {
            gState = GherkinState.GS_STEP;
            return G_PREFIX;
        }

        // Check for section headers
        if (text.equals("Feature") && match(':')) {
            gState = GherkinState.GS_DESC;
            return G_FEATURE;
        }
        if (text.equals("Scenario")) {
            if (peek() == ' ' && source.startsWith("Outline:", pos + 1)) {
                // Consume " Outline:"
                advance(); // space
                for (int i = 0; i < 8; i++) advance(); // "Outline:"
                gState = GherkinState.GS_DESC;
                return G_SCENARIO_OUTLINE;
            }
            if (match(':')) {
                gState = GherkinState.GS_DESC;
                return G_SCENARIO;
            }
        }
        if (text.equals("Background") && match(':')) {
            gState = GherkinState.GS_DESC;
            return G_BACKGROUND;
        }
        if (text.equals("Examples") && match(':')) {
            gState = GherkinState.GS_DESC;
            return G_EXAMPLES;
        }

        // Unknown - treat as description or skip
        return WS;
    }

    private TokenType scanGherkinTag() {
        advance(); // consume @
        while (!isAtEnd() && !isWhitespace(peek())) {
            advance();
        }
        gState = GherkinState.GS_TAGS;
        return G_TAG;
    }

    // ========== GS_DESC State ==========

    private TokenType scanGherkinDesc() {
        char c = peek();
        if (c == '\r' || c == '\n') {
            // Check lookahead for transition - only transition if next line starts with Gherkin keyword
            boolean transition = shouldTransitionToGherkin();
            // Consume whitespace/newlines
            while (!isAtEnd() && (peek() == ' ' || peek() == '\t' || peek() == '\r' || peek() == '\n')) {
                advance();
            }
            if (transition) {
                gState = GherkinState.GHERKIN;
            }
            // Stay in GS_DESC if not transitioning
            return WS_LF;
        }

        // Consume rest of line as description
        while (!isAtEnd() && peek() != '\r' && peek() != '\n') {
            advance();
        }
        return G_DESC;
    }

    private boolean shouldTransitionToGherkin() {
        // Look ahead past whitespace to see if next non-ws is a Gherkin keyword
        int lookahead = pos;
        while (lookahead < length) {
            char c = source.charAt(lookahead);
            if (c == '\r' || c == '\n' || c == ' ' || c == '\t') {
                lookahead++;
            } else {
                break;
            }
        }
        if (lookahead >= length) return true;

        // Check for Gherkin keywords at lookahead position
        String rest = source.substring(lookahead);
        return rest.startsWith("Given") || rest.startsWith("When") || rest.startsWith("Then")
                || rest.startsWith("And") || rest.startsWith("But") || rest.startsWith("*")
                || rest.startsWith("Scenario:") || rest.startsWith("Scenario Outline:")
                || rest.startsWith("Background:") || rest.startsWith("Examples:")
                || rest.startsWith("@") || rest.startsWith("|");
    }

    // ========== GS_COMMENT State ==========

    private TokenType scanGherkinComment() {
        char c = peek();
        if (c == '\r' || c == '\n') {
            gState = GherkinState.GHERKIN;
            return scanGherkinWhitespaceWithNewline();
        }

        // Consume rest of line as comment
        while (!isAtEnd() && peek() != '\r' && peek() != '\n') {
            advance();
        }
        return G_COMMENT;
    }

    // ========== GS_TAGS State ==========

    private TokenType scanGherkinTags() {
        char c = peek();
        if (c == '\r' || c == '\n') {
            gState = GherkinState.GHERKIN;
            return scanGherkinWhitespaceWithNewline();
        }
        if (c == '@') {
            return scanGherkinTag();
        }
        // Skip other characters
        advance();
        return WS;
    }

    // ========== GS_TABLE_ROW State ==========

    private TokenType scanGherkinTableRow() {
        char c = peek();
        if (c == '\r' || c == '\n') {
            gState = GherkinState.GHERKIN;
            return scanGherkinWhitespaceWithNewline();
        }
        if (c == '|') {
            advance();
            return G_PIPE;
        }
        // Table cell content
        while (!isAtEnd() && peek() != '|' && peek() != '\r' && peek() != '\n') {
            advance();
        }
        return G_TABLE_CELL;
    }

    // ========== GS_DOC_STRING State ==========

    private TokenType scanGherkinDocString() {
        char c = peek();

        // Check for closing triple quote
        if (c == '"' && peek(1) == '"' && peek(2) == '"') {
            advance();
            advance();
            advance();
            gState = GherkinState.GHERKIN;
            return G_TRIPLE_QUOTE;
        }

        // Handle newlines
        if (c == '\r' || c == '\n') {
            while (!isAtEnd() && (peek() == '\r' || peek() == '\n' || peek() == ' ' || peek() == '\t')) {
                advance();
            }
            return WS_LF;
        }

        // Content - consume until newline or triple quote
        while (!isAtEnd()) {
            c = peek();
            if (c == '\r' || c == '\n') break;
            if (c == '"' && peek(1) == '"' && peek(2) == '"') break;
            advance();
        }
        return G_EXPR;
    }

    // ========== GS_STEP State ==========

    private TokenType scanGherkinStep() {
        char c = peek();
        if (c == '\r' || c == '\n') {
            gState = GherkinState.GHERKIN;
            return scanGherkinWhitespaceWithNewline();
        }

        // Look for keywords
        if (isIdentifierStart(c)) {
            return scanStepKeyword();
        }

        // Equals sign
        if (c == '=') {
            advance();
            if (peek() == '\r' || peek() == '\n' || isAtEnd()) {
                gState = GherkinState.GHERKIN;
            } else {
                gState = GherkinState.GS_RHS;
            }
            return EQ;
        }

        // Start of JS expression (non-keyword character)
        gState = GherkinState.GS_RHS;
        return scanGherkinRhs();
    }

    private TokenType scanStepKeyword() {
        int start = pos;
        while (!isAtEnd() && (isIdentifierPart(peek()) || peek() == ' ' || peek() == '.')) {
            // Handle dotted identifiers like foo.bar
            if (peek() == '.') {
                advance();
                continue;
            }
            // Handle spaced keywords like "form field" - but only specific ones
            if (peek() == ' ') {
                String sofar = source.substring(start, pos);
                if (isSpacedKeywordPrefix(sofar)) {
                    advance();
                    continue;
                }
                break;
            }
            advance();
        }

        String text = source.substring(start, pos);

        // Check if followed by [ or ( - treat as JS expression, not keyword
        // This must be checked BEFORE keyword checks so that cookie({...}) is JS, not keyword
        if (peek() == '[' || peek() == '(') {
            // Rewind and treat as JS
            pos = start;
            col = tokenCol;
            gState = GherkinState.GS_RHS;
            return scanGherkinRhs();
        }

        // Check for "match" keyword
        if (text.equals("match")) {
            gState = GherkinState.GS_STEP_MATCH;
            return G_KEYWORD;
        }

        // Check for type keywords
        if (isTypeKeyword(text) || isAssignKeyword(text)) {
            return G_KEYWORD;
        }

        // Check for spaced keywords
        if (isSpacedKeyword(text)) {
            gState = GherkinState.GS_RHS;
            return G_KEYWORD;
        }

        // Check if followed by newline (docstring/table coming)
        if (peek() == '\r' || peek() == '\n' || isAtEnd()) {
            gState = GherkinState.GHERKIN;
            return G_KEYWORD;
        }

        // Regular keyword
        return G_KEYWORD;
    }

    private boolean isTypeKeyword(String text) {
        return text.equals("def") || text.equals("json") || text.equals("xml")
                || text.equals("xmlstring") || text.equals("yaml") || text.equals("csv")
                || text.equals("string") || text.equals("bytes") || text.equals("copy");
    }

    private boolean isAssignKeyword(String text) {
        return text.equals("configure") || text.equals("header") || text.equals("param")
                || text.equals("cookie") || text.equals("form field")
                || text.equals("multipart file") || text.equals("multipart field");
    }

    private boolean isSpacedKeywordPrefix(String text) {
        return text.equals("form") || text.equals("multipart") || text.equals("soap")
                || text.equals("retry");
    }

    private boolean isSpacedKeyword(String text) {
        return text.equals("form fields") || text.equals("multipart fields")
                || text.equals("multipart files") || text.equals("soap action")
                || text.equals("retry until") || text.equals("multipart entity");
    }

    // ========== GS_STEP_MATCH State ==========

    private TokenType scanGherkinStepMatch() {
        char c = peek();
        if (c == '\r' || c == '\n') {
            gState = GherkinState.GHERKIN;
            return scanGherkinWhitespaceWithNewline();
        }

        // Check for match type operators first: ==, !=
        if (c == '=' && peek(1) == '=') {
            advance();
            advance();
            return finishMatchType();
        }
        if (c == '!' && peek(1) == '=') {
            advance();
            advance();
            return finishMatchType();
        }

        // Keywords in match context
        if (isIdentifierStart(c)) {
            int start = pos;
            // Scan identifier (allowing hyphens for Content-Type style)
            while (!isAtEnd() && (isIdentifierPart(peek()) || peek() == '-')) {
                advance();
            }
            String text = source.substring(start, pos);

            if (text.equals("each") || text.equals("header")) {
                return G_KEYWORD;
            }

            // Match type keywords: contains, !contains, within, !within
            if (text.equals("contains") || text.equals("within")) {
                return finishMatchType();
            }

            // Regular identifier (possibly hyphenated like Content-Type)
            return IDENT;
        }

        // !contains or !within
        if (c == '!') {
            advance();
            int start = pos;
            while (!isAtEnd() && isIdentifierPart(peek())) {
                advance();
            }
            String text = source.substring(start, pos);
            if (text.equals("contains") || text.equals("within")) {
                return finishMatchType();
            }
            // Not a match type, treat as expression part
            return G_EXPR;
        }

        // Quoted strings as expressions
        if (c == '"') {
            return scanQuotedExpr();
        }
        if (c == '\'') {
            return scanSingleQuotedExpr();
        }

        // XPath: $ or @
        if (c == '$' || c == '@') {
            advance();
            while (!isAtEnd() && isIdentifierPart(peek())) {
                advance();
            }
            return IDENT;
        }

        // JSON path: ?(...)
        if (c == '?') {
            advance();
            if (match('(')) {
                while (!isAtEnd() && peek() != ')') {
                    advance();
                }
                match(')');
            }
            return IDENT;
        }

        // JSON path or XPath: .. or :
        if (c == '.' && peek(1) == '.') {
            advance();
            advance();
            return DOT;
        }
        if (c == ':') {
            advance();
            return DOT;
        }

        // XPath: // or /
        if (c == '/') {
            advance();
            match('/');
            while (!isAtEnd() && isIdentifierPart(peek())) {
                advance();
            }
            return IDENT;
        }

        // Fallback: any non-whitespace is part of match expression
        if (!isWhitespace(c)) {
            advance();
            return G_EXPR;
        }

        advance();
        return WS;
    }

    /**
     * After scanning a match type operator, scan optional modifiers (only, any, deep)
     * and transition to GS_RHS state.
     */
    private TokenType finishMatchType() {
        // Scan optional modifiers: (WS+ (only|any))? (WS+ deep)?
        while (!isAtEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t') {
                int wsStart = pos;
                int wsCol = col;
                while (!isAtEnd() && (peek() == ' ' || peek() == '\t')) {
                    advance();
                }
                // Check for modifier - must be followed by non-identifier char or end of input
                if (checkAndConsumeModifier("only", 4) ||
                    checkAndConsumeModifier("any", 3) ||
                    checkAndConsumeModifier("deep", 4)) {
                    continue;
                }
                // Not a modifier, rewind whitespace
                pos = wsStart;
                col = wsCol;
                break;
            }
            break;
        }

        // Transition state
        if (peek() == '\r' || peek() == '\n' || isAtEnd()) {
            gState = GherkinState.GHERKIN;
        } else {
            gState = GherkinState.GS_RHS;
        }
        return G_KEYWORD;
    }

    /**
     * Check if current position starts with the given modifier word and consume it if so.
     * A modifier is valid if it's followed by a non-identifier character or end of input.
     */
    private boolean checkAndConsumeModifier(String modifier, int len) {
        if (pos + len > length) {
            // Not enough characters left for this modifier
            return false;
        }
        // Check if source matches the modifier at current position
        for (int i = 0; i < len; i++) {
            if (source.charAt(pos + i) != modifier.charAt(i)) {
                return false;
            }
        }
        // Check character after modifier (must be non-identifier or end)
        int afterPos = pos + len;
        if (afterPos < length && isIdentifierPart(source.charAt(afterPos))) {
            return false; // Modifier is prefix of a longer identifier
        }
        // Consume the modifier
        for (int i = 0; i < len; i++) {
            advance();
        }
        return true;
    }

    private char charAtSafe(int index) {
        return index < length ? source.charAt(index) : '\0';
    }

    private TokenType scanQuotedExpr() {
        advance(); // opening "
        while (!isAtEnd() && peek() != '"' && peek() != '\r' && peek() != '\n') {
            if (peek() == '\\') {
                advance();
                if (!isAtEnd()) advance();
            } else {
                advance();
            }
        }
        if (peek() == '"') advance();
        return G_EXPR;
    }

    private TokenType scanSingleQuotedExpr() {
        advance(); // opening '
        while (!isAtEnd() && peek() != '\'' && peek() != '\r' && peek() != '\n') {
            if (peek() == '\\') {
                advance();
                if (!isAtEnd()) advance();
            } else {
                advance();
            }
        }
        if (peek() == '\'') advance();
        return G_EXPR;
    }

    // ========== GS_RHS State ==========

    private TokenType scanGherkinRhs() {
        char c = peek();
        if (c == '\r' || c == '\n') {
            gState = GherkinState.GHERKIN;
            return scanGherkinWhitespaceWithNewline();
        }

        // Consume rest of line as expression
        while (!isAtEnd() && peek() != '\r' && peek() != '\n') {
            advance();
        }
        return G_EXPR;
    }

    // ========== Helpers ==========

    private boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

}
