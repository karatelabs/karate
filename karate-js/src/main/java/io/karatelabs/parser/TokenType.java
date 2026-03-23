/*
 * The MIT License
 *
 * Copyright 2024 Karate Labs Inc.
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

public enum TokenType {

    WS_LF,
    WS,
    EOF,
    BACKTICK,
    L_CURLY,
    R_CURLY,
    L_BRACKET,
    R_BRACKET,
    L_PAREN,
    R_PAREN,
    COMMA,
    COLON,
    SEMI,
    DOT_DOT_DOT,
    QUES_DOT,
    DOT,
    //==== keywords
    NULL(true),
    TRUE(true),
    FALSE(true),
    FUNCTION(true),
    RETURN(true),
    TRY(true),
    CATCH(true),
    FINALLY(true),
    THROW(true),
    NEW(true),
    VAR(true),
    LET(true),
    CONST(true),
    IF(true),
    ELSE(true),
    TYPEOF(true),
    INSTANCEOF(true),
    DELETE(true),
    FOR(true),
    IN(true),
    OF(true),
    DO(true),
    WHILE(true),
    SWITCH(true),
    CASE(true),
    DEFAULT(true),
    BREAK(true),
    CONTINUE(true),
    THIS(true),
    VOID(true),
    //====
    EQ_EQ_EQ,
    EQ_EQ,
    EQ,
    EQ_GT, // arrow
    LT_LT_EQ,
    LT_LT,
    LT_EQ,
    LT,
    GT_GT_GT_EQ,
    GT_GT_GT,
    GT_GT_EQ,
    GT_GT,
    GT_EQ,
    GT,
    //====
    NOT_EQ_EQ,
    NOT_EQ,
    NOT,
    PIPE_PIPE_EQ,
    PIPE_PIPE,
    PIPE_EQ,
    PIPE,
    AMP_AMP_EQ,
    AMP_AMP,
    AMP_EQ,
    AMP,
    CARET_EQ,
    CARET,
    QUES_QUES,
    QUES,
    //====
    PLUS_PLUS,
    PLUS_EQ,
    PLUS,
    MINUS_MINUS,
    MINUS_EQ,
    MINUS,
    STAR_STAR_EQ,
    STAR_STAR,
    STAR_EQ,
    STAR,
    SLASH_EQ,
    SLASH,
    PERCENT_EQ,
    PERCENT,
    TILDE,
    //====
    L_COMMENT,
    B_COMMENT,
    S_STRING,
    D_STRING,
    NUMBER,
    IDENT,
    //====
    REGEX,
    DOLLAR_L_CURLY,
    T_STRING,
    //====
    G_PREFIX,
    G_COMMENT,
    G_DESC,
    G_FEATURE,
    G_SCENARIO,
    G_SCENARIO_OUTLINE,
    G_EXAMPLES,
    G_BACKGROUND,
    G_TAG,
    G_TRIPLE_QUOTE,
    G_PIPE,
    G_TABLE_CELL,
    G_KEYWORD,
    G_EXPR;

    public final boolean primary;
    public final boolean keyword;
    public final Boolean regexAllowed;

    TokenType() {
        this(false);
    }

    TokenType(boolean keyword) {
        this.primary = !isCommentOrWhitespace(this);
        this.keyword = keyword;
        regexAllowed = isRegexAllowed(this);
    }

    private static boolean isCommentOrWhitespace(TokenType type) {
        return switch (type) {
            // note that EOF is "primary" for parsing and not considered white-space
            case L_COMMENT, B_COMMENT, G_COMMENT, WS, WS_LF -> true;
            default -> false;
        };
    }

    private static Boolean isRegexAllowed(TokenType type) {
        return switch (type) {
            // after these tokens, a regex literal is allowed (rather than division)
            case L_PAREN, L_BRACKET, L_CURLY, COMMA, SEMI, COLON, EQ, EQ_EQ, EQ_EQ_EQ, NOT_EQ, NOT_EQ_EQ, LT, LT_EQ, GT,
                 GT_EQ, PLUS, PLUS_EQ, MINUS, MINUS_EQ, STAR, STAR_EQ, STAR_STAR, STAR_STAR_EQ, SLASH_EQ, PERCENT,
                 PERCENT_EQ, AMP, AMP_EQ, AMP_AMP, AMP_AMP_EQ, PIPE, PIPE_EQ, PIPE_PIPE, PIPE_PIPE_EQ, CARET, CARET_EQ,
                 QUES, QUES_QUES, TILDE, NOT, RETURN, TYPEOF, DELETE, INSTANCEOF, IN, DO, IF, ELSE, CASE, DEFAULT,
                 THROW, EQ_GT -> true;
            // after these tokens, a regex literal is not allowed
            case R_PAREN, R_BRACKET, R_CURLY, IDENT, NUMBER, S_STRING, D_STRING, TRUE, FALSE, NULL -> false;
            // for other tokens, keep the current value of regexAllowed
            default -> null;
        };
    }

    public boolean oneOf(TokenType... types) {
        for (TokenType type : types) {
            if (this == type) {
                return true;
            }
        }
        return false;
    }

}
