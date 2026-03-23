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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.karatelabs.parser.TokenType.*;
import static org.junit.jupiter.api.Assertions.*;

class JsLexerTest {

    private static List<Token> tokenize(String text) {
        JsLexer lexer = new JsLexer(Resource.text(text));
        List<Token> tokens = new ArrayList<>();
        Token token;
        do {
            token = lexer.nextToken();
            tokens.add(token);
        } while (token.type != EOF);
        return tokens;
    }

    private static List<TokenType> types(String text) {
        return tokenize(text).stream().map(t -> t.type).toList();
    }

    private static List<String> texts(String text) {
        return tokenize(text).stream().map(Token::getText).toList();
    }

    // ========== Operator Tests ==========

    @Test
    void testOperatorLongestMatch() {
        // Triple equals
        assertEquals(List.of(EQ_EQ_EQ, EOF), types("==="));
        assertEquals(List.of(EQ_EQ, EOF), types("=="));
        assertEquals(List.of(EQ, EOF), types("="));

        // Triple not equals
        assertEquals(List.of(NOT_EQ_EQ, EOF), types("!=="));
        assertEquals(List.of(NOT_EQ, EOF), types("!="));
        assertEquals(List.of(NOT, EOF), types("!"));

        // Unsigned right shift
        assertEquals(List.of(GT_GT_GT_EQ, EOF), types(">>>="));
        assertEquals(List.of(GT_GT_GT, EOF), types(">>>"));
        assertEquals(List.of(GT_GT_EQ, EOF), types(">>="));
        assertEquals(List.of(GT_GT, EOF), types(">>"));
        assertEquals(List.of(GT_EQ, EOF), types(">="));
        assertEquals(List.of(GT, EOF), types(">"));

        // Left shift
        assertEquals(List.of(LT_LT_EQ, EOF), types("<<="));
        assertEquals(List.of(LT_LT, EOF), types("<<"));
        assertEquals(List.of(LT_EQ, EOF), types("<="));
        assertEquals(List.of(LT, EOF), types("<"));

        // Exponentiation
        assertEquals(List.of(STAR_STAR_EQ, EOF), types("**="));
        assertEquals(List.of(STAR_STAR, EOF), types("**"));
        assertEquals(List.of(STAR_EQ, EOF), types("*="));
        assertEquals(List.of(STAR, EOF), types("*"));

        // Logical operators with assignment
        assertEquals(List.of(PIPE_PIPE_EQ, EOF), types("||="));
        assertEquals(List.of(PIPE_PIPE, EOF), types("||"));
        assertEquals(List.of(PIPE_EQ, EOF), types("|="));
        assertEquals(List.of(PIPE, EOF), types("|"));

        assertEquals(List.of(AMP_AMP_EQ, EOF), types("&&="));
        assertEquals(List.of(AMP_AMP, EOF), types("&&"));
        assertEquals(List.of(AMP_EQ, EOF), types("&="));
        assertEquals(List.of(AMP, EOF), types("&"));

        // Arrow function
        assertEquals(List.of(EQ_GT, EOF), types("=>"));

        // Optional chaining
        assertEquals(List.of(QUES_DOT, EOF), types("?."));
        assertEquals(List.of(QUES_QUES, EOF), types("??"));
        assertEquals(List.of(QUES, EOF), types("?"));

        // Spread/rest
        assertEquals(List.of(DOT_DOT_DOT, EOF), types("..."));
        assertEquals(List.of(DOT, EOF), types("."));

        // Increment/decrement
        assertEquals(List.of(PLUS_PLUS, EOF), types("++"));
        assertEquals(List.of(PLUS_EQ, EOF), types("+="));
        assertEquals(List.of(PLUS, EOF), types("+"));
        assertEquals(List.of(MINUS_MINUS, EOF), types("--"));
        assertEquals(List.of(MINUS_EQ, EOF), types("-="));
        assertEquals(List.of(MINUS, EOF), types("-"));
    }

    @Test
    void testPunctuation() {
        assertEquals(List.of(L_CURLY, R_CURLY, EOF), types("{}"));
        assertEquals(List.of(L_BRACKET, R_BRACKET, EOF), types("[]"));
        assertEquals(List.of(L_PAREN, R_PAREN, EOF), types("()"));
        assertEquals(List.of(COMMA, EOF), types(","));
        assertEquals(List.of(COLON, EOF), types(":"));
        assertEquals(List.of(SEMI, EOF), types(";"));
        assertEquals(List.of(TILDE, EOF), types("~"));
    }

    // ========== Number Tests ==========

    @Test
    void testNumbers() {
        // Integers
        assertEquals(List.of(NUMBER, EOF), types("0"));
        assertEquals(List.of(NUMBER, EOF), types("123"));

        // Decimals
        assertEquals(List.of(NUMBER, EOF), types("1.5"));
        assertEquals(List.of(NUMBER, EOF), types(".5"));
        assertEquals(List.of(NUMBER, EOF), types("0.123"));

        // Exponents
        assertEquals(List.of(NUMBER, EOF), types("1e10"));
        assertEquals(List.of(NUMBER, EOF), types("1E10"));
        assertEquals(List.of(NUMBER, EOF), types("1e+10"));
        assertEquals(List.of(NUMBER, EOF), types("1e-10"));
        assertEquals(List.of(NUMBER, EOF), types("1.5e10"));

        // Hex
        assertEquals(List.of(NUMBER, EOF), types("0x0"));
        assertEquals(List.of(NUMBER, EOF), types("0xFF"));
        assertEquals(List.of(NUMBER, EOF), types("0XFF"));
        assertEquals(List.of(NUMBER, EOF), types("0xDEADBEEF"));
    }

    @Test
    void testNumberValues() {
        assertEquals("123", texts("123").getFirst());
        assertEquals(".5", texts(".5").getFirst());
        assertEquals("1e-10", texts("1e-10").getFirst());
        assertEquals("0xFF", texts("0xFF").getFirst());
    }

    // ========== String Tests ==========

    @Test
    void testStrings() {
        assertEquals(List.of(D_STRING, EOF), types("\"hello\""));
        assertEquals(List.of(S_STRING, EOF), types("'hello'"));

        // Escapes
        assertEquals(List.of(D_STRING, EOF), types("\"hello\\\"world\""));
        assertEquals(List.of(S_STRING, EOF), types("'hello\\'world'"));

        // Empty strings
        assertEquals(List.of(D_STRING, EOF), types("\"\""));
        assertEquals(List.of(S_STRING, EOF), types("''"));
    }

    @Test
    void testStringValues() {
        assertEquals("\"hello\"", texts("\"hello\"").getFirst());
        assertEquals("'world'", texts("'world'").getFirst());
    }

    // ========== Comment Tests ==========

    @Test
    void testLineComment() {
        assertEquals(List.of(L_COMMENT, EOF), types("// comment"));
        assertEquals(List.of(L_COMMENT, WS_LF, IDENT, EOF), types("// comment\ncode"));
    }

    @Test
    void testBlockComment() {
        assertEquals(List.of(B_COMMENT, EOF), types("/* comment */"));
        assertEquals(List.of(B_COMMENT, EOF), types("/* multi\nline */"));
        assertEquals(List.of(B_COMMENT, WS, IDENT, EOF), types("/* comment */ code"));
    }

    // ========== Regex Tests ==========

    @Test
    void testRegexVsDivision() {
        // After operators that allow regex
        assertEquals(List.of(IDENT, WS, EQ, WS, REGEX, EOF), types("a = /test/"));
        assertEquals(List.of(RETURN, WS, REGEX, EOF), types("return /test/"));
        assertEquals(List.of(L_PAREN, REGEX, R_PAREN, EOF), types("(/test/)"));

        // After values - division
        assertEquals(List.of(IDENT, WS, SLASH, WS, IDENT, EOF), types("a / b"));
        assertEquals(List.of(NUMBER, SLASH, NUMBER, EOF), types("6/2"));
        assertEquals(List.of(R_PAREN, WS, SLASH, WS, NUMBER, EOF), types(") / 2"));
    }

    @Test
    void testRegexWithFlags() {
        List<Token> tokens = tokenize("return /test/gi");
        assertEquals(REGEX, tokens.get(2).type);
        assertEquals("/test/gi", tokens.get(2).getText());
    }

    @Test
    void testRegexWithEscapes() {
        List<Token> tokens = tokenize("return /a\\/b/");
        assertEquals(REGEX, tokens.get(2).type);
        assertEquals("/a\\/b/", tokens.get(2).getText());
    }

    @Test
    void testRegexWithCharacterClass() {
        List<Token> tokens = tokenize("return /[a-z]/");
        assertEquals(REGEX, tokens.get(2).type);
        assertEquals("/[a-z]/", tokens.get(2).getText());

        // Slash inside character class shouldn't end regex
        tokens = tokenize("return /[/]/");
        assertEquals(REGEX, tokens.get(2).type);
        assertEquals("/[/]/", tokens.get(2).getText());
    }

    // ========== Template Literal Tests ==========

    @Test
    void testTemplateLiteral() {
        assertEquals(List.of(BACKTICK, T_STRING, BACKTICK, EOF), types("`hello`"));
    }

    @Test
    void testTemplateLiteralEmpty() {
        assertEquals(List.of(BACKTICK, BACKTICK, EOF), types("``"));
    }

    @Test
    void testTemplateLiteralWithPlaceholder() {
        List<TokenType> expected = List.of(
                BACKTICK, T_STRING, DOLLAR_L_CURLY, IDENT, R_CURLY, T_STRING, BACKTICK, EOF
        );
        assertEquals(expected, types("`hello ${name}!`"));
    }

    @Test
    void testTemplateLiteralNested() {
        // `${a + `${b}`}`
        List<TokenType> expected = List.of(
                BACKTICK, DOLLAR_L_CURLY, IDENT, WS, PLUS, WS,
                BACKTICK, DOLLAR_L_CURLY, IDENT, R_CURLY, BACKTICK,
                R_CURLY, BACKTICK, EOF
        );
        assertEquals(expected, types("`${a + `${b}`}`"));
    }

    @Test
    void testTemplateLiteralWithExpression() {
        List<TokenType> expected = List.of(
                BACKTICK, T_STRING, DOLLAR_L_CURLY, NUMBER, WS, PLUS, WS, NUMBER, R_CURLY, BACKTICK, EOF
        );
        assertEquals(expected, types("`result: ${1 + 2}`"));
    }

    // ========== Keyword Tests ==========

    @Test
    void testKeywords() {
        assertEquals(List.of(NULL, EOF), types("null"));
        assertEquals(List.of(TRUE, EOF), types("true"));
        assertEquals(List.of(FALSE, EOF), types("false"));
        assertEquals(List.of(FUNCTION, EOF), types("function"));
        assertEquals(List.of(RETURN, EOF), types("return"));
        assertEquals(List.of(IF, EOF), types("if"));
        assertEquals(List.of(ELSE, EOF), types("else"));
        assertEquals(List.of(FOR, EOF), types("for"));
        assertEquals(List.of(WHILE, EOF), types("while"));
        assertEquals(List.of(DO, EOF), types("do"));
        assertEquals(List.of(SWITCH, EOF), types("switch"));
        assertEquals(List.of(CASE, EOF), types("case"));
        assertEquals(List.of(DEFAULT, EOF), types("default"));
        assertEquals(List.of(BREAK, EOF), types("break"));
        assertEquals(List.of(CONTINUE, EOF), types("continue"));
        assertEquals(List.of(VAR, EOF), types("var"));
        assertEquals(List.of(LET, EOF), types("let"));
        assertEquals(List.of(CONST, EOF), types("const"));
        assertEquals(List.of(NEW, EOF), types("new"));
        // Note: "this" and "void" are identifiers, not keywords (handled in evaluator)
        assertEquals(List.of(IDENT, EOF), types("this"));
        assertEquals(List.of(TYPEOF, EOF), types("typeof"));
        assertEquals(List.of(INSTANCEOF, EOF), types("instanceof"));
        assertEquals(List.of(DELETE, EOF), types("delete"));
        assertEquals(List.of(TRY, EOF), types("try"));
        assertEquals(List.of(CATCH, EOF), types("catch"));
        assertEquals(List.of(FINALLY, EOF), types("finally"));
        assertEquals(List.of(THROW, EOF), types("throw"));
        assertEquals(List.of(IN, EOF), types("in"));
        assertEquals(List.of(OF, EOF), types("of"));
        assertEquals(List.of(IDENT, EOF), types("void"));
    }

    @Test
    void testIdentifiers() {
        assertEquals(List.of(IDENT, EOF), types("foo"));
        assertEquals(List.of(IDENT, EOF), types("_private"));
        assertEquals(List.of(IDENT, EOF), types("$jquery"));
        assertEquals(List.of(IDENT, EOF), types("camelCase"));
        assertEquals(List.of(IDENT, EOF), types("PascalCase"));
        assertEquals(List.of(IDENT, EOF), types("snake_case"));
        assertEquals(List.of(IDENT, EOF), types("x123"));
    }

    @Test
    void testUnicodeIdentifiers() {
        assertEquals(List.of(IDENT, EOF), types("αβγ"));
        assertEquals(List.of(IDENT, EOF), types("日本語"));
        // Note: emoji are not valid identifier characters in Java/JS
        assertEquals(List.of(IDENT, EOF), types("_ident123"));
    }

    // ========== Whitespace Tests ==========

    @Test
    void testWhitespace() {
        assertEquals(List.of(WS, IDENT, EOF), types("  foo"));
        assertEquals(List.of(IDENT, WS, IDENT, EOF), types("a b"));
        assertEquals(List.of(IDENT, WS, IDENT, EOF), types("a\tb"));
    }

    @Test
    void testNewlines() {
        assertEquals(List.of(IDENT, WS_LF, IDENT, EOF), types("a\nb"));
        assertEquals(List.of(IDENT, WS_LF, IDENT, EOF), types("a\r\nb"));
        assertEquals(List.of(IDENT, WS_LF, EOF), types("a\n"));
    }

    // ========== Line/Column Tracking ==========

    @Test
    void testLineTracking() {
        List<Token> tokens = tokenize("a\nb\nc");
        assertEquals(0, tokens.get(0).line); // a
        assertEquals(1, tokens.get(2).line); // b
        assertEquals(2, tokens.get(4).line); // c
    }

    @Test
    void testColumnTracking() {
        List<Token> tokens = tokenize("abc def");
        assertEquals(0, tokens.get(0).col); // abc at col 0
        assertEquals(4, tokens.get(2).col); // def at col 4
    }

    // ========== Complex Expressions ==========

    @Test
    void testArrowFunction() {
        List<TokenType> expected = List.of(
                L_PAREN, IDENT, R_PAREN, WS, EQ_GT, WS, IDENT, EOF
        );
        assertEquals(expected, types("(x) => x"));
    }

    @Test
    void testObjectLiteral() {
        List<TokenType> expected = List.of(
                L_CURLY, WS, IDENT, COLON, WS, NUMBER, WS, R_CURLY, EOF
        );
        assertEquals(expected, types("{ a: 1 }"));
    }

    @Test
    void testArrayLiteral() {
        List<TokenType> expected = List.of(
                L_BRACKET, NUMBER, COMMA, WS, NUMBER, COMMA, WS, NUMBER, R_BRACKET, EOF
        );
        assertEquals(expected, types("[1, 2, 3]"));
    }

    @Test
    void testFunctionCall() {
        List<TokenType> expected = List.of(
                IDENT, L_PAREN, IDENT, COMMA, WS, IDENT, R_PAREN, EOF
        );
        assertEquals(expected, types("foo(a, b)"));
    }

    @Test
    void testChainedOptional() {
        List<TokenType> expected = List.of(
                IDENT, QUES_DOT, IDENT, QUES_DOT, IDENT, EOF
        );
        assertEquals(expected, types("a?.b?.c"));
    }

    // ========== Edge Cases ==========

    @Test
    void testEmptyInput() {
        assertEquals(List.of(EOF), types(""));
    }

    @Test
    void testThisAsIdent() {
        // "this" is tokenized as IDENT, not a keyword (handled in evaluator)
        assertEquals(List.of(L_CURLY, WS, IDENT, WS, DOT, WS, IDENT, WS, EQ, WS, IDENT, WS, R_CURLY, EOF),
                types("{ this . b = x }"));
    }

    @Test
    void testOnlyWhitespace() {
        assertEquals(List.of(WS, EOF), types("   "));
        assertEquals(List.of(WS_LF, EOF), types("\n\n"));
    }

    @Test
    void testConsecutiveOperators() {
        // a+++b should be a ++ + b
        assertEquals(List.of(IDENT, PLUS_PLUS, PLUS, IDENT, EOF), types("a+++b"));

        // a---b should be a -- - b
        assertEquals(List.of(IDENT, MINUS_MINUS, MINUS, IDENT, EOF), types("a---b"));
    }

    @Test
    void testDivisionAfterParen() {
        // (a+b)/c - division after closing paren
        assertEquals(List.of(L_PAREN, IDENT, PLUS, IDENT, R_PAREN, SLASH, IDENT, EOF), types("(a+b)/c"));
    }

    @Test
    void testRegexAfterKeyword() {
        // if (/test/) - regex after keyword
        List<Token> tokens = tokenize("if (/test/)");
        assertEquals(REGEX, tokens.get(3).type);
    }

}
