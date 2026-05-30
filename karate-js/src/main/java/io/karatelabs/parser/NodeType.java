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
    VAR_DECL,
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
    IN_EXPR,
    FN_CALL_EXPR,
    FN_CALL_ARGS,
    FN_CALL_ARG,
    FN_TAGGED_TEMPLATE_EXPR,
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
    CLASS_EXPR,
    CLASS_METHOD,
    CLASS_FIELD,
    SUPER_EXPR,
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

    // expectedChildren is the initial children-array size. The cost is asymmetric:
    // outgrowing it triggers an Arrays.copyOf doubling chain (4->8->16->32...), while
    // over-sizing only wastes a few references per node — and real ASTs (especially
    // the per-step Karate workload) are small, so that waste is cheap in absolute
    // terms. We therefore err toward MORE capacity on any type with size variance,
    // and keep tight sizing only for the structural single-child wrappers that
    // literally cannot hold more. Values informed by measured child-count
    // distributions (NodeSizeAnalysis) over the RealisticBenchmark corpus (the real
    // workload) cross-checked against EngineBenchmark's two 20KB scripts. Re-run
    // NodeSizeAnalysis if the grammar changes materially.
    private static int expectedChildrenFor(NodeType type) {
        return switch (type) {
            // Leaf node - never has children
            case TOKEN -> 0;
            // Structural single-child wrappers — measured at exactly 1 child across
            // thousands of samples and structurally incapable of more. EXPR is the
            // single most frequent node type, so this is the bulk of the win at zero
            // growth risk. (EXPR_LIST is excluded — the comma operator can hold N.)
            case EXPR, REF_EXPR, LIT_EXPR, EOS -> 1;
            // Two-child nodes: operator + operand. STATEMENT measured p95/max=2.
            case UNARY_EXPR, TYPEOF_EXPR, MATH_PRE_EXPR, MATH_POST_EXPR,
                 LIT_REGEX, BREAK_STMT, CONTINUE_STMT,
                 PLACEHOLDER, FN_DECL_ARG, FN_CALL_ARG, ARRAY_ELEM,
                 FN_TAGGED_TEMPLATE_EXPR, STATEMENT, EXPR_LIST -> 2;
            // Three-child nodes: binary ops after Shift.LEFT (left, op, right);
            // VAR_DECL measured ~always 3 (name = value).
            case REF_DOT_EXPR, INSTANCEOF_EXPR, IN_EXPR, VAR_DECL -> 3;
            // Ternary (cond ? a : b = 5); call-arg lists measured p95~3 — padded so
            // typical multi-arg calls never grow.
            case LOGIC_TERN_EXPR, FN_CALL_ARGS -> 5;
            // Parameter lists measured p95~5; padded a little for higher-arity fns.
            case FN_DECL_ARGS -> 6;
            // PROGRAM is 1-2 statements in the per-step workload, but a whole script /
            // multi-statement REPL eval can be much larger — keep it generous so real
            // files don't grow.
            case PROGRAM -> 8;
            // A C-style for-header is measured at exactly 9 children (init ; test ;
            // update + body + punctuation) and grew 100% of the time at the old 4.
            case FOR_STMT -> 9;
            // Object/array literals — measured p95 ~8-12; padded so everyday literals
            // don't grow (real-world objects/arrays run larger than the benchmark's).
            case LIT_OBJECT, LIT_ARRAY -> 12;
            // Gherkin table rows: pipe + cell alternating, 5-col table = 11 tokens
            case G_TABLE_ROW -> 12;
            // Containers that span a wide range (large function bodies up to ~50
            // statements, class bodies, gherkin sections). Kept generous: the
            // small-body common case wastes a few slots cheaply, the large-body case
            // avoids a copyOf chain.
            case ROOT, CLASS_EXPR, BLOCK, CASE_BLOCK, DEFAULT_BLOCK, LIT_TEMPLATE,
                 G_FEATURE, G_SCENARIO, G_SCENARIO_OUTLINE, G_BACKGROUND,
                 G_TABLE, G_STEP, G_STEP_LINE, G_DOC_STRING, G_EXAMPLES -> 16;
            // Default: binary expressions and most statements (3-4 children typical)
            default -> 4;
        };
    }

}
