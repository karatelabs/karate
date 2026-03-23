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
package io.karatelabs.parser;

import io.karatelabs.common.Resource;

import java.util.ArrayDeque;
import java.util.List;

import static io.karatelabs.parser.TokenType.*;

/**
 * Hand-rolled lexer for JavaScript. This implementation is designed to be
 * portable to other languages (JS, Rust) and serves as the canonical spec.
 */
public class JsLexer extends BaseLexer {

    private boolean regexAllowed = true;
    private final ArrayDeque<LexerState> stateStack = new ArrayDeque<>();

    protected enum LexerState {
        INITIAL, TEMPLATE, PLACEHOLDER
    }

    public JsLexer(Resource resource) {
        super(resource);
        stateStack.push(LexerState.INITIAL);
    }

    public static List<Token> getTokens(Resource resource) {
        return tokenize(new JsLexer(resource));
    }

    // ========== Public API ==========

    @Override
    public Token nextToken() {
        tokenStart = pos;
        tokenLine = line;
        tokenCol = col;
        TokenType type = scanToken();
        int length = pos - tokenStart;
        if (type.regexAllowed != null) {
            regexAllowed = type.regexAllowed;
        }
        return new Token(buffer, type, tokenStart, tokenLine, tokenCol, length);
    }

    // ========== State Management ==========

    protected LexerState currentState() {
        return stateStack.peek();
    }

    protected void pushState(LexerState state) {
        stateStack.push(state);
    }

    protected LexerState popState() {
        if (stateStack.size() > 1) {
            return stateStack.pop();
        }
        return stateStack.peek();
    }

    // ========== Main Scanner ==========

    @Override
    protected TokenType scanToken() {
        if (isAtEnd()) {
            return EOF;
        }

        LexerState state = currentState();
        if (state == LexerState.TEMPLATE) {
            return scanTemplateContent();
        }

        char c = source.charAt(pos);

        // Whitespace (most common in typical code)
        if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
            return scanWhitespace();
        }

        // Comments and slash
        if (c == '/') {
            return scanSlash();
        }

        // Strings
        if (c == '"') {
            return scanDoubleString();
        }
        if (c == '\'') {
            return scanSingleString();
        }

        // Template literal
        if (c == '`') {
            advance();
            pushState(LexerState.TEMPLATE);
            return BACKTICK;
        }

        // Identifiers and keywords (very common - check before numbers)
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$') {
            return scanIdentifier();
        }

        // Numbers
        if ((c >= '0' && c <= '9') || (c == '.' && pos + 1 < length && isDigit(source.charAt(pos + 1)))) {
            return scanNumber();
        }

        // Unicode identifiers (rare)
        if (c > 127 && Character.isJavaIdentifierStart(c)) {
            return scanIdentifier();
        }

        // Operators and punctuation
        return scanOperator();
    }

    // ========== Whitespace ==========

    private TokenType scanWhitespace() {
        // Fast path: consume spaces/tabs with minimal overhead
        boolean hasNewline = false;
        while (pos < length) {
            char c = source.charAt(pos);
            if (c == ' ' || c == '\t') {
                pos++;
                col++;
            } else if (c == '\n') {
                pos++;
                line++;
                col = 0;
                hasNewline = true;
            } else if (c == '\r') {
                pos++;
                col++;
                hasNewline = true;
            } else {
                break;
            }
        }
        return hasNewline ? WS_LF : WS;
    }

    // ========== Comments and Slash ==========

    private TokenType scanSlash() {
        advance(); // consume '/'
        if (match('/')) {
            return scanLineComment();
        }
        if (match('*')) {
            return scanBlockComment();
        }
        if (regexAllowed) {
            return scanRegex();
        }
        return match('=') ? SLASH_EQ : SLASH;
    }

    private TokenType scanLineComment() {
        // Fast scan to end of line
        while (pos < length) {
            char c = source.charAt(pos);
            if (c == '\n' || c == '\r') break;
            pos++;
            col++;
        }
        return L_COMMENT;
    }

    private TokenType scanBlockComment() {
        while (pos < length) {
            char c = source.charAt(pos);
            if (c == '*' && pos + 1 < length && source.charAt(pos + 1) == '/') {
                pos += 2;
                col += 2;
                break;
            }
            if (c == '\n') {
                pos++;
                line++;
                col = 0;
            } else {
                pos++;
                col++;
            }
        }
        return B_COMMENT;
    }

    private TokenType scanRegex() {
        // Already consumed initial '/'
        // Scan until closing '/' handling escape sequences and character classes
        boolean inCharClass = false;
        while (!isAtEnd()) {
            char c = peek();
            if (c == '\n' || c == '\r') {
                // Unterminated regex
                break;
            }
            if (c == '\\') {
                advance(); // consume backslash
                if (!isAtEnd() && peek() != '\n' && peek() != '\r') {
                    advance(); // consume escaped char
                }
                continue;
            }
            if (c == '[') {
                inCharClass = true;
                advance();
                continue;
            }
            if (c == ']' && inCharClass) {
                inCharClass = false;
                advance();
                continue;
            }
            if (c == '/' && !inCharClass) {
                advance(); // consume closing '/'
                // Scan flags
                while (!isAtEnd() && isIdentifierPart(peek())) {
                    advance();
                }
                break;
            }
            advance();
        }
        return REGEX;
    }

    // ========== Strings ==========

    private TokenType scanDoubleString() {
        pos++; col++; // consume opening "
        while (pos < length) {
            char c = source.charAt(pos);
            if (c == '"') {
                pos++; col++;
                break;
            }
            if (c == '\\' && pos + 1 < length) {
                // Skip escape sequence
                char next = source.charAt(pos + 1);
                if (next == '\n') {
                    pos += 2;
                    line++;
                    col = 0;
                } else {
                    pos += 2;
                    col += 2;
                }
                continue;
            }
            if (c == '\n') {
                pos++;
                line++;
                col = 0;
            } else {
                pos++;
                col++;
            }
        }
        return D_STRING;
    }

    private TokenType scanSingleString() {
        pos++; col++; // consume opening '
        while (pos < length) {
            char c = source.charAt(pos);
            if (c == '\'') {
                pos++; col++;
                break;
            }
            if (c == '\\' && pos + 1 < length) {
                // Skip escape sequence
                char next = source.charAt(pos + 1);
                if (next == '\n') {
                    pos += 2;
                    line++;
                    col = 0;
                } else {
                    pos += 2;
                    col += 2;
                }
                continue;
            }
            if (c == '\n') {
                pos++;
                line++;
                col = 0;
            } else {
                pos++;
                col++;
            }
        }
        return S_STRING;
    }

    // ========== Template Literals ==========

    private TokenType scanTemplateContent() {
        // In TEMPLATE state, scan until we hit `, ${, or end
        if (isAtEnd()) {
            return EOF;
        }

        char c = peek();

        // End of template
        if (c == '`') {
            advance();
            popState();
            return BACKTICK;
        }

        // Placeholder start
        if (c == '$' && peek(1) == '{') {
            advance(); // $
            advance(); // {
            pushState(LexerState.PLACEHOLDER);
            return DOLLAR_L_CURLY;
        }

        // Template string content
        while (!isAtEnd()) {
            c = peek();
            if (c == '`') {
                break;
            }
            if (c == '$' && peek(1) == '{') {
                break;
            }
            if (c == '\\') {
                advance();
                if (!isAtEnd()) {
                    advance();
                }
                continue;
            }
            advance();
        }
        return T_STRING;
    }

    // ========== Numbers ==========

    private TokenType scanNumber() {
        char c = peek();

        // Hex number
        if (c == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
            advance(); // 0
            advance(); // x
            while (!isAtEnd() && isHexDigit(peek())) {
                advance();
            }
            return NUMBER;
        }

        // Decimal number
        // Integer part
        if (c == '.') {
            // Number starting with .
            advance();
        } else {
            while (!isAtEnd() && isDigit(peek())) {
                advance();
            }
            // Decimal part
            if (peek() == '.' && isDigit(peek(1))) {
                advance(); // .
            }
        }

        // Fractional part
        while (!isAtEnd() && isDigit(peek())) {
            advance();
        }

        // Exponent part
        if (peek() == 'e' || peek() == 'E') {
            advance();
            if (peek() == '+' || peek() == '-') {
                advance();
            }
            while (!isAtEnd() && isDigit(peek())) {
                advance();
            }
        }

        return NUMBER;
    }

    // ========== Identifiers and Keywords ==========

    private TokenType scanIdentifier() {
        // Fast path for ASCII identifiers (most common case)
        while (pos < length) {
            char c = source.charAt(pos);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') || c == '_' || c == '$') {
                pos++;
                col++;
            } else if (c > 127 && Character.isJavaIdentifierPart(c)) {
                // Unicode identifier part - rare but must handle
                pos++;
                col++;
            } else {
                break;
            }
        }
        return keywordOrIdent(tokenStart, pos - tokenStart);
    }

    private TokenType keywordOrIdent(int start, int len) {
        // Note: "this" and "void" are NOT keywords in the lexer - they're identifiers
        // that get special handling in the parser/evaluator
        // Fast path: switch on length first, then first char
        if (len == 2) {
            char c0 = source.charAt(start);
            if (c0 == 'i') {
                if (source.charAt(start + 1) == 'f') return IF;
                if (source.charAt(start + 1) == 'n') return IN;
            } else if (c0 == 'o' && source.charAt(start + 1) == 'f') {
                return OF;
            } else if (c0 == 'd' && source.charAt(start + 1) == 'o') {
                return DO;
            }
        } else if (len == 3) {
            char c0 = source.charAt(start);
            if (c0 == 'v' && source.charAt(start + 1) == 'a' && source.charAt(start + 2) == 'r') return VAR;
            if (c0 == 'l' && source.charAt(start + 1) == 'e' && source.charAt(start + 2) == 't') return LET;
            if (c0 == 'n' && source.charAt(start + 1) == 'e' && source.charAt(start + 2) == 'w') return NEW;
            if (c0 == 't' && source.charAt(start + 1) == 'r' && source.charAt(start + 2) == 'y') return TRY;
            if (c0 == 'f' && source.charAt(start + 1) == 'o' && source.charAt(start + 2) == 'r') return FOR;
        } else if (len == 4) {
            char c0 = source.charAt(start);
            if (c0 == 'n' && matchKeyword(start, "null")) return NULL;
            if (c0 == 't' && matchKeyword(start, "true")) return TRUE;
            if (c0 == 'e' && matchKeyword(start, "else")) return ELSE;
            if (c0 == 'c' && matchKeyword(start, "case")) return CASE;
        } else if (len == 5) {
            char c0 = source.charAt(start);
            if (c0 == 'f' && matchKeyword(start, "false")) return FALSE;
            if (c0 == 'c') {
                if (matchKeyword(start, "const")) return CONST;
                if (matchKeyword(start, "catch")) return CATCH;
            }
            if (c0 == 't' && matchKeyword(start, "throw")) return THROW;
            if (c0 == 'w' && matchKeyword(start, "while")) return WHILE;
            if (c0 == 'b' && matchKeyword(start, "break")) return BREAK;
        } else if (len == 6) {
            char c0 = source.charAt(start);
            if (c0 == 'r' && matchKeyword(start, "return")) return RETURN;
            if (c0 == 't' && matchKeyword(start, "typeof")) return TYPEOF;
            if (c0 == 'd' && matchKeyword(start, "delete")) return DELETE;
            if (c0 == 's' && matchKeyword(start, "switch")) return SWITCH;
        } else if (len == 7) {
            char c0 = source.charAt(start);
            if (c0 == 'f') {
                if (matchKeyword(start, "finally")) return FINALLY;
            }
            if (c0 == 'd' && matchKeyword(start, "default")) return DEFAULT;
        } else if (len == 8) {
            char c0 = source.charAt(start);
            if (c0 == 'f' && matchKeyword(start, "function")) return FUNCTION;
            if (c0 == 'c' && matchKeyword(start, "continue")) return CONTINUE;
        } else if (len == 10) {
            if (source.charAt(start) == 'i' && matchKeyword(start, "instanceof")) return INSTANCEOF;
        }
        return IDENT;
    }

    private boolean matchKeyword(int start, String keyword) {
        int len = keyword.length();
        for (int i = 0; i < len; i++) {
            if (source.charAt(start + i) != keyword.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    // ========== Operators and Punctuation ==========

    private TokenType scanOperator() {
        char c = advance();

        switch (c) {
            case '{':
                return L_CURLY;
            case '}':
                if (currentState() == LexerState.PLACEHOLDER) {
                    popState();
                }
                return R_CURLY;
            case '[':
                return L_BRACKET;
            case ']':
                return R_BRACKET;
            case '(':
                return L_PAREN;
            case ')':
                return R_PAREN;
            case ',':
                return COMMA;
            case ':':
                return COLON;
            case ';':
                return SEMI;
            case '~':
                return TILDE;

            case '.':
                if (match('.')) {
                    if (match('.')) {
                        return DOT_DOT_DOT;
                    }
                    // Two dots without third - back up and return single dot
                    // This shouldn't happen in valid JS, but handle gracefully
                }
                return DOT;

            case '?':
                if (match('.')) {
                    return QUES_DOT;
                }
                if (match('?')) {
                    return QUES_QUES;
                }
                return QUES;

            case '=':
                if (match('=')) {
                    return match('=') ? EQ_EQ_EQ : EQ_EQ;
                }
                if (match('>')) {
                    return EQ_GT;
                }
                return EQ;

            case '<':
                if (match('<')) {
                    return match('=') ? LT_LT_EQ : LT_LT;
                }
                return match('=') ? LT_EQ : LT;

            case '>':
                if (match('>')) {
                    if (match('>')) {
                        return match('=') ? GT_GT_GT_EQ : GT_GT_GT;
                    }
                    return match('=') ? GT_GT_EQ : GT_GT;
                }
                return match('=') ? GT_EQ : GT;

            case '!':
                if (match('=')) {
                    return match('=') ? NOT_EQ_EQ : NOT_EQ;
                }
                return NOT;

            case '|':
                if (match('|')) {
                    return match('=') ? PIPE_PIPE_EQ : PIPE_PIPE;
                }
                return match('=') ? PIPE_EQ : PIPE;

            case '&':
                if (match('&')) {
                    return match('=') ? AMP_AMP_EQ : AMP_AMP;
                }
                return match('=') ? AMP_EQ : AMP;

            case '^':
                return match('=') ? CARET_EQ : CARET;

            case '+':
                if (match('+')) {
                    return PLUS_PLUS;
                }
                return match('=') ? PLUS_EQ : PLUS;

            case '-':
                if (match('-')) {
                    return MINUS_MINUS;
                }
                return match('=') ? MINUS_EQ : MINUS;

            case '*':
                if (match('*')) {
                    return match('=') ? STAR_STAR_EQ : STAR_STAR;
                }
                return match('=') ? STAR_EQ : STAR;

            case '%':
                return match('=') ? PERCENT_EQ : PERCENT;

            default:
                // Unknown character - return as IDENT (will likely cause parse error)
                return IDENT;
        }
    }

}
