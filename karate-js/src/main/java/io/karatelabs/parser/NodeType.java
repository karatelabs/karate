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

public enum NodeType {

    TOKEN,
    ERROR,
    ROOT,
    PROGRAM,
    STATEMENT,
    IF_STMT,
    VAR_STMT,
    VAR_NAMES,
    RETURN_STMT,
    TRY_STMT,
    THROW_STMT,
    FOR_STMT,
    WHILE_STMT,
    DO_WHILE_STMT,
    SWITCH_STMT,
    CASE_BLOCK,
    DEFAULT_BLOCK,
    BREAK_STMT,
    CONTINUE_STMT,
    DELETE_STMT,
    BLOCK,
    EOS,
    EXPR,
    EXPR_LIST,
    FN_EXPR,
    FN_ARROW_EXPR,
    FN_DECL_ARGS,
    FN_DECL_ARG,
    NEW_EXPR,
    TYPEOF_EXPR,
    INSTANCEOF_EXPR,
    FN_CALL_EXPR,
    FN_CALL_ARGS,
    FN_CALL_ARG,
    ASSIGN_EXPR,
    LOGIC_EXPR,
    LOGIC_AND_EXPR,
    LOGIC_NULLISH_EXPR,
    LOGIC_TERN_EXPR,
    LOGIC_BIT_EXPR,
    MATH_ADD_EXPR,
    MATH_MUL_EXPR,
    MATH_EXP_EXPR,
    MATH_POST_EXPR,
    MATH_PRE_EXPR,
    PATH_EXPR,
    REF_EXPR,
    REF_DOT_EXPR,
    REF_BRACKET_EXPR,
    UNARY_EXPR,
    LIT_OBJECT,
    OBJECT_ELEM,
    LIT_ARRAY,
    ARRAY_ELEM,
    LIT_EXPR,
    PAREN_EXPR,
    LIT_TEMPLATE,
    PLACEHOLDER,
    LIT_REGEX,
    //====
    G_FEATURE,
    G_TAGS,
    G_NAME_DESC,
    G_BACKGROUND,
    G_SCENARIO,
    G_SCENARIO_OUTLINE,
    G_EXAMPLES,
    G_STEP,
    G_STEP_LINE,
    G_DOC_STRING,
    G_TABLE,
    G_TABLE_ROW;

    public final int expectedChildren;

    NodeType() {
        this.expectedChildren = expectedChildrenFor(this);
    }

    private static int expectedChildrenFor(NodeType type) {
        return switch (type) {
            // Leaf node - never has children
            case TOKEN -> 0;
            // Small nodes: 1-2 children typical
            // Single-child nodes: REF_EXPR only has 1 TOKEN child
            case REF_EXPR -> 1;
            // Two-child nodes: operator + operand
            case UNARY_EXPR, TYPEOF_EXPR, MATH_PRE_EXPR, MATH_POST_EXPR,
                 LIT_EXPR, LIT_REGEX, EOS, BREAK_STMT, CONTINUE_STMT,
                 PLACEHOLDER, FN_DECL_ARG, FN_CALL_ARG, ARRAY_ELEM -> 2;
            // Three-child nodes: binary ops after Shift.LEFT (left, op, right)
            case REF_DOT_EXPR, INSTANCEOF_EXPR -> 3;
            // Ternary: condition ? true_expr : false_expr = 5 children
            case LOGIC_TERN_EXPR -> 5;
            // Container nodes: variable, often many children
            case ROOT, PROGRAM -> 16;
            // Gherkin table rows: pipe + cell alternating, 5-col table = 11 tokens
            case G_TABLE_ROW -> 12;
            // Literals can have many elements: {a:1,b:2,...} or [1,2,3,...]
            case LIT_ARRAY, LIT_OBJECT -> 16;
            case BLOCK, FN_CALL_ARGS, FN_DECL_ARGS,
                 EXPR_LIST, CASE_BLOCK, DEFAULT_BLOCK, LIT_TEMPLATE,
                 G_FEATURE, G_SCENARIO, G_SCENARIO_OUTLINE, G_BACKGROUND,
                 G_TABLE, G_STEP, G_STEP_LINE, G_DOC_STRING, G_EXAMPLES -> 8;
            // Default: binary expressions and most statements (3-4 children typical)
            default -> 4;
        };
    }

}
