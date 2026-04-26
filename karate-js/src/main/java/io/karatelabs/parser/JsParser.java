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

import java.util.EnumSet;

import static io.karatelabs.parser.TokenType.*;

public class JsParser extends BaseParser {

    // EnumSet token sets for O(1) lookup in hot paths
    private static final EnumSet<TokenType> T_VAR_STMT = EnumSet.of(VAR, CONST, LET);
    private static final EnumSet<TokenType> T_ASSIGN_EXPR = EnumSet.of(EQ, PLUS_EQ, MINUS_EQ, STAR_EQ, SLASH_EQ, PERCENT_EQ, STAR_STAR_EQ, GT_GT_EQ, LT_LT_EQ, GT_GT_GT_EQ, AMP_EQ, PIPE_EQ, CARET_EQ);
    private static final EnumSet<TokenType> T_LOGIC_EQ_EXPR = EnumSet.of(EQ_EQ_EQ, NOT_EQ_EQ, EQ_EQ, NOT_EQ);
    private static final EnumSet<TokenType> T_LOGIC_CMP_EXPR = EnumSet.of(LT, GT, LT_EQ, GT_EQ);
    private static final EnumSet<TokenType> T_LOGIC_SHIFT_EXPR = EnumSet.of(GT_GT, LT_LT, GT_GT_GT);
    private static final EnumSet<TokenType> T_MATH_ADD_EXPR = EnumSet.of(PLUS, MINUS);
    private static final EnumSet<TokenType> T_MATH_MUL_EXPR = EnumSet.of(STAR, SLASH, PERCENT);
    private static final EnumSet<TokenType> T_REF_DOT_EXPR = EnumSet.of(DOT, QUES_DOT);
    private static final EnumSet<TokenType> T_MATH_POST_EXPR = EnumSet.of(PLUS_PLUS, MINUS_MINUS);
    private static final EnumSet<TokenType> T_UNARY_EXPR = EnumSet.of(NOT, TILDE, VOID);
    private static final EnumSet<TokenType> T_MATH_PRE_EXPR = EnumSet.of(PLUS_PLUS, MINUS_MINUS, MINUS, PLUS);
    // PropertyName is an IdentifierName (identifier OR any reserved word),
    // plus string/numeric literals, computed-key bracket, and spread. The
    // spec allows `{break: 1}`, `{default: 1}`, etc. in object literals and
    // destructuring patterns alike. Keywords are added at class-init time
    // from TokenType.keyword.
    private static final EnumSet<TokenType> T_OBJECT_ELEM = buildObjectElemSet();
    private static final EnumSet<TokenType> T_ACCESSOR_KEY_START = buildAccessorKeySet();

    private static EnumSet<TokenType> buildObjectElemSet() {
        EnumSet<TokenType> s = EnumSet.of(IDENT, S_STRING, D_STRING, NUMBER, BIGINT, DOT_DOT_DOT, L_BRACKET);
        for (TokenType t : TokenType.values()) {
            if (t.keyword) s.add(t);
        }
        return s;
    }

    private static EnumSet<TokenType> buildAccessorKeySet() {
        EnumSet<TokenType> s = EnumSet.of(IDENT, S_STRING, D_STRING, NUMBER, BIGINT, L_BRACKET);
        for (TokenType t : TokenType.values()) {
            if (t.keyword) s.add(t);
        }
        return s;
    }
    private static final EnumSet<TokenType> T_LIT_EXPR = EnumSet.of(S_STRING, D_STRING, NUMBER, BIGINT, TRUE, FALSE, NULL);
    private static final EnumSet<TokenType> T_FOR_IN_OF = EnumSet.of(IN, OF);

    // Lookahead sets - EnumSet for O(1) contains() via bitmask
    private static final EnumSet<TokenType> T_EXPR_START = EnumSet.of(
            IDENT, S_STRING, D_STRING, NUMBER, BIGINT, TRUE, FALSE, NULL,  // literals & ref
            L_CURLY, L_BRACKET, BACKTICK, REGEX,                           // compound literals
            FUNCTION, L_PAREN, NEW, TYPEOF, VOID,                          // keywords & grouping
            NOT, TILDE, PLUS_PLUS, MINUS_MINUS, MINUS, PLUS                // unary operators
    );
    private static final EnumSet<TokenType> T_LIT_EXPR_START = EnumSet.of(
            S_STRING, D_STRING, NUMBER, BIGINT, TRUE, FALSE, NULL,         // simple literals
            L_CURLY, L_BRACKET, BACKTICK, REGEX                            // compound literals
    );

    private Node ast;

    // True if any `?.` was consumed during parsing — gates the
    // post-parse early-error walk so files with no optional chaining
    // pay no AST-traversal cost. Reset to false implicitly per JsParser
    // instance (one parse per instance).
    private boolean sawOptionalChain;

    public JsParser(Resource resource) {
        super(resource, JsLexer.getTokens(resource), false);
    }

    public JsParser(Resource resource, boolean errorRecovery) {
        super(resource, JsLexer.getTokens(resource), errorRecovery);
    }

    /**
     * @return the AST root node (PROGRAM) for IDE features
     */
    public Node getAst() {
        return ast;
    }

    public Node parse() {
        enter(NodeType.PROGRAM);
        ast = markerNode();
        while (true) {
            if (!statement(false)) {
                // In recovery mode, skip unparseable tokens to find next statement
                if (errorRecoveryEnabled && !peekIf(EOF)) {
                    error("cannot parse statement");
                    recoverToStatement();
                    continue;
                }
                break;
            }
        }
        if (!consumeIf(EOF)) {
            error("cannot parse statement");
        }
        exit();
        // Spec early errors: assignment-target validity (always) plus the
        // optional-chain rules (only when `?.` was actually seen — that
        // subtree-walk is otherwise pure dead weight).
        if (!errorRecoveryEnabled) {
            validateEarlyErrors(ast);
        }
        return ast;
    }

    /**
     * Spec early errors walked once over the parsed tree:
     * <ul>
     *   <li>IsValidSimpleAssignmentTarget: the LHS of {@code =} / compound assignment
     *       and the operand of {@code ++}/{@code --} must be a simple reference
     *       (identifier, member access) or a destructuring pattern. Patterns like
     *       {@code (a + b) = 1}, {@code () => {} = 1}, {@code 1 = 1}, {@code ++f()},
     *       {@code (x = y) = 1} are SyntaxErrors at parse phase.</li>
     *   <li>Optional-chain restrictions: an OptionalExpression cannot be an
     *       assignment target, the operand of {@code ++}/{@code --}, or the head of
     *       a tagged template literal.</li>
     * </ul>
     * Throws {@link ParserException} so the test262 runner classifies the failure as
     * {@code phase: parse}.
     */
    private void validateEarlyErrors(Node node) {
        if (node == null) return;
        switch (node.type) {
            case ASSIGN_EXPR -> {
                if (node.size() > 0) {
                    Node lhs = node.getFirst();
                    if (sawOptionalChain && subtreeContainsOptionalChain(lhs)) {
                        throw new ParserException("optional chain is not a valid assignment target");
                    }
                    checkSimpleAssignmentTarget(lhs, "assignment target");
                }
            }
            case MATH_POST_EXPR -> {
                // children: [operand, ++/--]
                if (node.size() > 0) {
                    Node operand = node.getFirst();
                    if (sawOptionalChain && subtreeContainsOptionalChain(operand)) {
                        throw new ParserException("optional chain cannot be the operand of postfix ++/--");
                    }
                    checkSimpleAssignmentTarget(operand, "operand of postfix update");
                }
            }
            case MATH_PRE_EXPR -> {
                // children: [op, operand] — only ++/-- need a valid update target;
                // unary +/- have no assignment side and parse the same shape.
                if (node.size() > 1) {
                    Node op = node.getFirst();
                    if (op.isToken() && (op.token.type == PLUS_PLUS || op.token.type == MINUS_MINUS)) {
                        Node operand = node.get(1);
                        if (sawOptionalChain && subtreeContainsOptionalChain(operand)) {
                            throw new ParserException("optional chain cannot be the operand of prefix ++/--");
                        }
                        checkSimpleAssignmentTarget(operand, "operand of prefix update");
                    }
                }
            }
            case FN_TAGGED_TEMPLATE_EXPR -> {
                if (sawOptionalChain && node.size() > 0
                        && subtreeContainsOptionalChain(node.getFirst())) {
                    throw new ParserException("tagged template literal cannot follow an optional chain");
                }
            }
            default -> {
            }
        }
        for (int i = 0, n = node.size(); i < n; i++) {
            Node child = node.get(i);
            if (!child.isToken()) {
                validateEarlyErrors(child);
            }
        }
    }

    /**
     * Per spec ({@code IsValidSimpleAssignmentTarget} / {@code AssignmentTargetType}),
     * the LHS of an assignment or update expression must be either:
     * <ul>
     *   <li>An IdentifierReference (bare identifier),</li>
     *   <li>A MemberExpression of the form {@code x.y} or {@code x[k]} (including over
     *       a CallExpression head, e.g. {@code f().x = 1}),</li>
     *   <li>An ObjectLiteral or ArrayLiteral at the top of the LHS — refined into a
     *       destructuring AssignmentPattern.</li>
     * </ul>
     * Anything else (binary expressions, unary operators, literals, function/arrow
     * expressions, comma expressions, parenthesized destructuring patterns, nested
     * assignment expressions, etc.) is a SyntaxError at parse phase.
     * <p>
     * {@code f() = 1} is the one notable exception: the spec's web-compat carve-out
     * allows it in non-strict mode (returns {@code ~web-compat~}, not {@code invalid}).
     * The engine has no separate strict mode so non-strict is permanent; the
     * corresponding test262 cases are flagged {@code onlyStrict} and skipped by the
     * harness.
     */
    private static void checkSimpleAssignmentTarget(Node lhs, String siteName) {
        Node n = stripExprWrappers(lhs);
        if (n == null) {
            // Defensive — empty LHS only happens in error-recovery edge cases.
            return;
        }
        // Top-level Object/Array literal refines into a destructuring pattern.
        if (n.type == NodeType.LIT_EXPR && n.size() >= 1) {
            NodeType lit = n.getFirst().type;
            if (lit == NodeType.LIT_ARRAY || lit == NodeType.LIT_OBJECT) {
                return;
            }
        }
        // Peel any layers of parens. Per spec, a ParenthesizedExpression around an
        // ObjectLiteral or ArrayLiteral does NOT refine to a destructuring pattern,
        // so it becomes invalid; everything else falls through to the simple check.
        while (n.type == NodeType.PAREN_EXPR) {
            // PAREN_EXPR shape: [(, body, )]
            Node body = n.size() >= 2 ? n.get(1) : null;
            if (body == null) {
                throw new ParserException("invalid " + siteName);
            }
            if (body.type == NodeType.EXPR_LIST && body.size() != 1) {
                throw new ParserException("invalid " + siteName + ": comma expression");
            }
            Node inner = stripExprWrappers(body);
            if (inner == null) {
                throw new ParserException("invalid " + siteName);
            }
            if (inner.type == NodeType.LIT_EXPR && inner.size() >= 1) {
                NodeType lit = inner.getFirst().type;
                if (lit == NodeType.LIT_ARRAY || lit == NodeType.LIT_OBJECT) {
                    throw new ParserException("invalid " + siteName + ": parenthesized destructuring pattern");
                }
            }
            n = inner;
        }
        switch (n.type) {
            case REF_EXPR -> {
                // REF_EXPR is single-arg arrow `x => ...` when its first child is an
                // FN_ARROW_EXPR rather than the IDENT token; that form is invalid.
                if (n.size() >= 1 && !n.getFirst().isToken()
                        && n.getFirst().type == NodeType.FN_ARROW_EXPR) {
                    throw new ParserException("invalid " + siteName + ": arrow function");
                }
            }
            case REF_DOT_EXPR, REF_BRACKET_EXPR -> {
                // Plain member access; a `?.` would have been caught earlier by
                // the optional-chain branch above with a more specific message.
            }
            case FN_CALL_EXPR -> {
                // Web-compat carve-out: see method javadoc.
            }
            default ->
                    throw new ParserException("invalid " + siteName + ": "
                            + n.type.name() + " is not a valid assignment target");
        }
    }

    /**
     * Strip thin {@code EXPR} / {@code EXPR_LIST} single-child wrappers introduced
     * by the parser so that callers can inspect the underlying expression shape.
     * Returns the node unchanged once the wrappers run out or the node has more
     * than one child.
     */
    private static Node stripExprWrappers(Node n) {
        while (n != null && n.size() == 1
                && (n.type == NodeType.EXPR || n.type == NodeType.EXPR_LIST)) {
            n = n.get(0);
        }
        return n;
    }

    /**
     * True if {@code node}'s chain subtree contains any {@code ?.} marker. Walks
     * down through chain types only — PAREN_EXPR or any non-chain node ends the
     * walk, since parens reset the optional-chain scope per spec.
     */
    private static boolean subtreeContainsOptionalChain(Node node) {
        Node cur = node;
        // Skip thin wrappers — EXPR / EXPR_LIST single-child / PAREN_EXPR — to reach
        // the actual chain head. Parens reset the chain scope per spec for execution,
        // but `(a?.b) = 1` and `--(a?.b)` are still parse errors because the cover
        // OptionalExpression is not a valid simple-assignment target.
        while (cur != null && (cur.type == NodeType.EXPR || cur.type == NodeType.EXPR_LIST
                || cur.type == NodeType.PAREN_EXPR)) {
            if (cur.size() == 0) return false;
            // PAREN_EXPR has shape [(, body, )]; EXPR/EXPR_LIST wrap their content as the first child.
            cur = cur.type == NodeType.PAREN_EXPR ? cur.get(1) : cur.getFirst();
        }
        while (cur != null) {
            if (cur.type == NodeType.REF_DOT_EXPR) {
                Node second = cur.size() > 1 ? cur.get(1) : null;
                if (second != null) {
                    if (second.isToken() && second.token.type == QUES_DOT) return true;
                    if (!second.isToken() && second.size() > 0) {
                        Node firstOfSecond = second.getFirst();
                        if (firstOfSecond.isToken() && firstOfSecond.token.type == QUES_DOT) return true;
                    }
                }
                cur = cur.size() > 0 ? cur.getFirst() : null;
            } else if (cur.type == NodeType.REF_BRACKET_EXPR
                    || cur.type == NodeType.FN_CALL_EXPR
                    || cur.type == NodeType.FN_TAGGED_TEMPLATE_EXPR) {
                cur = cur.size() > 0 ? cur.getFirst() : null;
            } else {
                return false;
            }
        }
        return false;
    }

    // Recovery points for statements
    private static final TokenType[] STMT_RECOVERY = {
            IF, FOR, WHILE, DO, SWITCH, TRY, RETURN, THROW, BREAK, CONTINUE,
            VAR, LET, CONST, FUNCTION, L_CURLY, R_CURLY, SEMI, EOF
    };

    private void recoverToStatement() {
        recoverTo(STMT_RECOVERY);
    }

    private boolean statement(boolean mandatory) {
        enter(NodeType.STATEMENT);
        boolean result = if_stmt()
                || (var_stmt(false) && eos())
                || (return_stmt() && eos())
                || (throw_stmt() && eos())
                || try_stmt()
                || for_stmt()
                || while_stmt()
                || do_while_stmt()
                || switch_stmt()
                || (break_stmt() && eos())
                || (continue_stmt() && eos())
                || (delete_stmt() && eos())
                || fn_expr() // function declarations don't need eos (ASI)
                || block(false) // block before expr_list: per JS spec, { } at statement position is a block
                || (expr_list(false) && eos())
                || consumeIf(SEMI); // empty statement
        // In error recovery mode, accept incomplete statements if we consumed any tokens
        if (!result && errorRecoveryEnabled && !markerNode().isEmpty()) {
            return exitSoft();
        }
        return exit(result, mandatory);
    }

    private boolean eos() {
        if (enter(NodeType.EOS, SEMI)) {
            return exit();
        }
        Token next = peekToken();
        if (next.type == R_CURLY || next.type == EOF) {
            return true;
        }
        Token prev = next.getPrev();
        return prev != null && prev.type == WS_LF;
    }

    private boolean expr_list(boolean mandatory) {
        // Fast lookahead: skip if current token can't start an expression
        if (!mandatory && !peekAnyOf(T_EXPR_START)) {
            return false;
        }
        enter(NodeType.EXPR_LIST);
        boolean atLeastOne = false;
        while (true) {
            if (expr(-1, false)) {
                atLeastOne = true;
            } else {
                break;
            }
            if (!consumeIf(COMMA)) {
                break;
            }
        }
        return exit(atLeastOne, mandatory);
    }

    private boolean if_stmt() {
        if (!enter(NodeType.IF_STMT, IF)) {
            return false;
        }
        consumeSoft(L_PAREN);
        expr_list(true);
        consumeSoft(R_PAREN);
        statement(true);
        if (consumeIf(ELSE)) {
            statement(true);
        }
        return exit();
    }

    private boolean var_stmt(boolean forLoop) {
        if (!enter(NodeType.VAR_STMT, T_VAR_STMT)) {
            return false;
        }
        boolean requireInit = lastConsumed() == CONST && !forLoop;
        if (!var_decl(requireInit)) {
            error(NodeType.VAR_DECL);
        }
        while (consumeIf(COMMA)) {
            if (!var_decl(requireInit)) {
                error(NodeType.VAR_DECL);
            }
        }
        return exit();
    }

    private boolean var_decl(boolean requireInit) {
        enter(NodeType.VAR_DECL);
        boolean hasBinding = lit_array() || lit_object() || consumeIf(IDENT);
        if (!hasBinding) {
            return exit(false, false);
        }
        if (consumeIf(EQ)) {
            expr(-1, true);
        } else if (requireInit) {
            error(EQ);
        }
        return exit();
    }

    private boolean return_stmt() {
        if (!enter(NodeType.RETURN_STMT, RETURN)) {
            return false;
        }
        expr_list(false);
        return exit();
    }

    private boolean throw_stmt() {
        if (!enter(NodeType.THROW_STMT, THROW)) {
            return false;
        }
        expr_list(true);
        return exit();
    }

    private boolean try_stmt() {
        if (!enter(NodeType.TRY_STMT, TRY)) {
            return false;
        }
        block(true);
        if (consumeIf(CATCH)) {
            if (consumeIf(L_PAREN) && consumeIf(IDENT) && consumeIf(R_PAREN) && block(true)) {
                if (consumeIf(FINALLY)) {
                    block(true);
                }
            } else if (!block(false)) { // catch without exception variable
                error(CATCH);
            }
        } else if (consumeIf(FINALLY)) {
            block(true);
        } else {
            error("expected " + CATCH + " or " + FINALLY);
        }
        return exit();
    }

    private boolean for_stmt() {
        if (!enter(NodeType.FOR_STMT, FOR)) {
            return false;
        }
        consumeSoft(L_PAREN);
        if (!(peekIf(SEMI) || var_stmt(true) || expr_list(false))) {
            error(NodeType.VAR_STMT, NodeType.EXPR);
        }
        if (consumeIf(SEMI)) {
            if (peekIf(SEMI) || expr_list(false)) {
                if (consumeIf(SEMI)) {
                    if (!(peekIf(R_PAREN) || expr_list(false))) {
                        error(NodeType.EXPR);
                    }
                } else {
                    error(SEMI);
                }
            } else {
                error(NodeType.EXPR);
            }
        } else if (anyOf(T_FOR_IN_OF)) {
            expr(-1, true);
        } else {
            error(SEMI, IN, OF);
        }
        consumeSoft(R_PAREN);
        if (!peekIf(EOF)) {
            statement(true);
        } else {
            error(NodeType.STATEMENT);
        }
        return exit();
    }

    private boolean while_stmt() {
        if (!enter(NodeType.WHILE_STMT, WHILE)) {
            return false;
        }
        consumeSoft(L_PAREN);
        expr_list(true);
        consumeSoft(R_PAREN);
        statement(true);
        return exit();
    }

    private boolean do_while_stmt() {
        if (!enter(NodeType.DO_WHILE_STMT, DO)) {
            return false;
        }
        statement(true);
        consumeSoft(WHILE);
        consumeSoft(L_PAREN);
        expr_list(true);
        consumeSoft(R_PAREN);
        return exit();
    }

    private boolean switch_stmt() {
        if (!enter(NodeType.SWITCH_STMT, SWITCH)) {
            return false;
        }
        consumeSoft(L_PAREN);
        expr_list(true);
        consumeSoft(R_PAREN);
        consumeSoft(L_CURLY);
        while (true) {
            if (peekIf(R_CURLY) || peekIf(DEFAULT) || peekIf(EOF)) {
                break;
            }
            if (!case_block()) {
                if (errorRecoveryEnabled) {
                    error("invalid case block");
                    recoverTo(R_CURLY, CASE, DEFAULT, EOF);
                    continue;
                }
                break;
            }
        }
        default_block();
        consumeSoft(R_CURLY);
        return exit();
    }

    private boolean case_block() {
        if (!enter(NodeType.CASE_BLOCK, CASE)) {
            return false;
        }
        expr(-1, true);
        consumeSoft(COLON);
        while (true) {
            if (peekIf(CASE) || peekIf(DEFAULT) || peekIf(R_CURLY) || peekIf(EOF)) {
                break;
            }
            if (!statement(false)) {
                if (errorRecoveryEnabled) {
                    error("cannot parse statement in case block");
                    recoverTo(CASE, DEFAULT, R_CURLY, SEMI, EOF);
                    continue;
                }
                break;
            }
        }
        return exit();
    }

    private void default_block() {
        if (!enter(NodeType.DEFAULT_BLOCK, DEFAULT)) {
            return;
        }
        consumeSoft(COLON);
        while (true) {
            if (peekIf(R_CURLY) || peekIf(EOF)) {
                break;
            }
            if (!statement(false)) {
                if (errorRecoveryEnabled) {
                    error("cannot parse statement in default block");
                    recoverTo(R_CURLY, SEMI, EOF);
                    continue;
                }
                break;
            }
        }
        exit();
    }

    private boolean break_stmt() {
        if (!enter(NodeType.BREAK_STMT, BREAK)) {
            return false;
        }
        return exit();
    }

    private boolean continue_stmt() {
        if (!enter(NodeType.CONTINUE_STMT, CONTINUE)) {
            return false;
        }
        return exit();
    }

    // as per spec this is an expression
    private boolean delete_stmt() {
        if (!enter(NodeType.DELETE_STMT, DELETE)) {
            return false;
        }
        expr(13, true);
        return exit();
    }

    private boolean block(boolean mandatory) {
        if (!enter(NodeType.BLOCK, L_CURLY)) {
            if (mandatory) {
                // In recovery mode, create empty block node
                if (errorRecoveryEnabled) {
                    enter(NodeType.BLOCK);
                    error(L_CURLY);
                    return exitSoft();
                }
                error(NodeType.BLOCK);
            }
            return false;
        }
        while (true) {
            if (peekIf(R_CURLY) || peekIf(EOF)) {
                break;
            }
            if (!statement(false)) {
                // In recovery mode, skip unparseable content
                if (errorRecoveryEnabled) {
                    error("cannot parse statement in block");
                    recoverTo(R_CURLY, SEMI, EOF);
                    if (peekIf(SEMI)) {
                        consumeNext();
                    }
                    continue;
                }
                break;
            }
        }
        consumeSoft(R_CURLY);
        return exit();
    }

    //==================================================================================================================
    //
    private boolean expr(int priority, boolean mandatory) {
        // Fast lookahead: skip if current token can't start an expression
        if (!mandatory && !peekAnyOf(T_EXPR_START)) {
            return false;
        }
        enter(NodeType.EXPR);
        boolean result = ref_expr() // also handles single-arg arrow functions without parentheses
                || lit_expr()
                || fn_expr()
                || fn_arrow_expr()
                || paren_expr()
                || unary_expr()
                || math_pre_expr()
                || new_expr()
                || typeof_expr();
        if (result) {
            expr_rhs(priority);
        }
        if (!result && mandatory && errorRecoveryEnabled) {
            // In recovery mode, create ERROR node for missing expression
            enter(NodeType.ERROR);
            error(NodeType.EXPR);
            exit();
            return exit(true, false); // Return true to continue parsing
        }
        return exit(result, mandatory);
    }

    private void expr_rhs(int priority) {
        while (true) {
            if (priority < 0 && enter(NodeType.ASSIGN_EXPR, T_ASSIGN_EXPR)) {
                expr(-1, true);
                exit(Shift.RIGHT);
            } else if (priority < 1 && enter(NodeType.LOGIC_TERN_EXPR, QUES)) {
                expr(-1, true);
                consumeSoft(COLON);
                expr(-1, true);
                exit(Shift.RIGHT);
            } else if (priority < 2 && enter(NodeType.LOGIC_AND_EXPR, PIPE_PIPE)) {
                expr(2, true);
                exit(Shift.LEFT);
            } else if (priority < 2 && enter(NodeType.LOGIC_NULLISH_EXPR, QUES_QUES)) {
                expr(2, true);
                exit(Shift.LEFT);
            } else if (priority < 3 && enter(NodeType.LOGIC_AND_EXPR, AMP_AMP)) {
                expr(3, true);
                exit(Shift.LEFT);
            } else if (priority < 4 && enter(NodeType.LOGIC_BIT_EXPR, PIPE)) {
                expr(4, true);
                exit(Shift.LEFT);
            } else if (priority < 5 && enter(NodeType.LOGIC_BIT_EXPR, CARET)) {
                expr(5, true);
                exit(Shift.LEFT);
            } else if (priority < 6 && enter(NodeType.LOGIC_BIT_EXPR, AMP)) {
                expr(6, true);
                exit(Shift.LEFT);
            } else if (priority < 7 && enter(NodeType.LOGIC_EXPR, T_LOGIC_EQ_EXPR)) {
                expr(7, true);
                exit(Shift.LEFT);
            } else if (priority < 8 && enter(NodeType.LOGIC_EXPR, T_LOGIC_CMP_EXPR)) {
                expr(8, true);
                exit(Shift.LEFT);
            } else if (priority < 9 && enter(NodeType.LOGIC_BIT_EXPR, T_LOGIC_SHIFT_EXPR)) {
                expr(9, true);
                exit(Shift.LEFT);
            } else if (priority < 10 && enter(NodeType.MATH_ADD_EXPR, T_MATH_ADD_EXPR)) {
                expr(10, true);
                exit(Shift.LEFT);
            } else if (priority < 11 && enter(NodeType.MATH_MUL_EXPR, T_MATH_MUL_EXPR)) {
                expr(11, true);
                exit(Shift.LEFT);
            } else if (priority < 12 && peekIf(STAR_STAR)) {
                do {
                    enter(NodeType.MATH_EXP_EXPR);
                    consumeNext();
                    expr(12, true);
                    exit(Shift.RIGHT);
                } while (peekIf(STAR_STAR));
            } else if (enter(NodeType.FN_CALL_EXPR, L_PAREN)) {
                fn_call_args();
                consumeSoft(R_PAREN);
                exit(Shift.LEFT);
                // new binds to the first call expression only
                // new Foo().bar() should parse as (new Foo()).bar(), not new (Foo().bar())
                if (isCallerType(NodeType.NEW_EXPR)) {
                    break;
                }
            } else if (peekIf(BACKTICK)) {
                // tagged template: prior postfix expression applied to the template literal
                // shape: FN_TAGGED_TEMPLATE_EXPR -> [<callable>, LIT_TEMPLATE]
                enter(NodeType.FN_TAGGED_TEMPLATE_EXPR);
                lit_template();
                exit(Shift.LEFT);
            } else if (enter(NodeType.REF_DOT_EXPR, T_REF_DOT_EXPR)) {
                TokenType dotType = lastConsumed();
                if (dotType == QUES_DOT) {
                    sawOptionalChain = true;
                }
                // allow reserved words as property accessors
                TokenType dotNext = peek();
                if (dotNext == IDENT || dotNext.keyword) {
                    consumeNext();
                } else if (dotType == QUES_DOT) {
                    // Parse only the immediate `?.[expr]` or `?.(args)` step. Subsequent
                    // postfix ops (`.x`, `[k]`, `(args)`) chain at the outer expr_rhs
                    // level, producing a flat left-recursive AST. Recursing into
                    // expr_rhs(12) here would nest the rest of the chain inside this
                    // REF_DOT_EXPR — confusing the interpreter's chain-step traversal.
                    if (dotNext == L_BRACKET) {
                        enter(NodeType.REF_BRACKET_EXPR, L_BRACKET);
                        expr(-1, true);
                        consumeSoft(R_BRACKET);
                        exit(Shift.LEFT);
                    } else if (dotNext == L_PAREN) {
                        enter(NodeType.FN_CALL_EXPR, L_PAREN);
                        fn_call_args();
                        consumeSoft(R_PAREN);
                        exit(Shift.LEFT);
                    } else {
                        error(L_BRACKET, L_PAREN);
                    }
                } else {
                    error(IDENT);
                }
                exit(Shift.LEFT);
            } else if (enter(NodeType.REF_BRACKET_EXPR, L_BRACKET)) {
                expr(-1, true);
                consumeSoft(R_BRACKET);
                exit(Shift.LEFT);
            } else if (enter(NodeType.MATH_POST_EXPR, T_MATH_POST_EXPR)) {
                exit(Shift.LEFT);
            } else if (priority < 8 && enter(NodeType.INSTANCEOF_EXPR, INSTANCEOF)) {
                expr(8, true);
                exit(Shift.LEFT);
            } else {
                break;
            }
        }
    }

    private boolean fn_arrow_expr() {
        // Fast lookahead: skip if this can't be an arrow function
        // Avoids creating nodes that will be discarded on backtrack
        if (!looksLikeArrowFunction()) {
            return false;
        }
        enter(NodeType.FN_ARROW_EXPR);
        // what started out looking like arrow function args may not be
        // e.g: "(function(){})", so fn_decl_args() will re-wind
        // and paren_expr() is next in line in expr() to handle
        if (fn_decl_args()) {
            if (consumeIf(EQ_GT)) {
                if (block(false) || expr(-1, false)) {
                    return exit();
                }
                error(NodeType.BLOCK, NodeType.EXPR);
            }
        }
        return exit(false, false);
    }

    // Lookahead to detect arrow function: (...) =>
    private boolean looksLikeArrowFunction() {
        if (peek() != L_PAREN) {
            return false;
        }
        int depth = 0;
        int size = tokens.size();
        for (int i = getPosition(); i < size; i++) {
            TokenType t = tokens.get(i).type;
            if (t == L_PAREN) {
                depth++;
            } else if (t == R_PAREN) {
                if (--depth == 0) {
                    // Check if next token is =>
                    return i + 1 < size && tokens.get(i + 1).type == EQ_GT;
                }
            } else if (t == L_CURLY || t == SEMI || t == EOF) {
                // Can't be arrow function args - bail early
                return false;
            }
        }
        return false;
    }

    private boolean fn_expr() {
        if (!enter(NodeType.FN_EXPR, FUNCTION)) {
            return false;
        }
        consumeIf(IDENT);
        fn_decl_args();
        block(true);
        return exit();
    }

    private boolean fn_decl_args() {
        if (!enter(NodeType.FN_DECL_ARGS, L_PAREN)) {
            return false;
        }
        boolean hasParams = false;
        while (true) {
            if (peekIf(R_PAREN) || peekIf(EOF)) {
                break;
            }
            if (!fn_decl_arg()) {
                if (errorRecoveryEnabled && hasParams) {
                    // Only error/recover if we've parsed at least one param
                    // If first param fails, this might be a paren expr, not arrow fn args
                    error("invalid function parameter");
                    recoverTo(R_PAREN, COMMA, EOF);
                    continue;
                }
                break;
            }
            hasParams = true;
        }
        return exit(consumeIf(R_PAREN), false);
    }

    private boolean fn_decl_arg() {
        enter(NodeType.FN_DECL_ARG);
        if (consumeIf(DOT_DOT_DOT)) {
            consume(IDENT);
            if (!peekIf(R_PAREN)) {
                error(R_PAREN);
            }
            return exit();
        }
        boolean result = consumeIf(IDENT) || lit_array() || lit_object();
        if (result && consumeIf(EQ)) {
            expr(-1, true);
        }
        result = result && (consumeIf(COMMA) || peekIf(R_PAREN));
        return exit(result, false);
    }

    private void fn_call_args() {
        enter(NodeType.FN_CALL_ARGS);
        while (true) {
            if (peekIf(R_PAREN) || peekIf(EOF)) {
                break;
            }
            if (!fn_call_arg()) {
                if (errorRecoveryEnabled) {
                    error("invalid function argument");
                    recoverTo(R_PAREN, COMMA, EOF);
                    continue;
                }
                break;
            }
        }
        exit();
    }

    private boolean fn_call_arg() {
        enter(NodeType.FN_CALL_ARG);
        consumeIf(DOT_DOT_DOT);
        boolean result = expr(-1, false);
        result = result && (consumeIf(COMMA) || peekIf(R_PAREN));
        return exit(result, false);
    }

    private boolean new_expr() {
        if (!enter(NodeType.NEW_EXPR, NEW)) {
            return false;
        }
        expr(13, true);
        return exit();
    }

    private boolean typeof_expr() {
        if (!enter(NodeType.TYPEOF_EXPR, TYPEOF)) {
            return false;
        }
        expr(13, true);
        return exit();
    }

    private boolean ref_expr() {
        if (!enter(NodeType.REF_EXPR, IDENT)) {
            return false;
        }
        if (enter(NodeType.FN_ARROW_EXPR, EQ_GT)) {
            if (block(false) || expr(-1, false)) {
                exit(Shift.LEFT); // change the node type
            } else {
                error(NodeType.BLOCK, NodeType.EXPR);
            }
        }
        return exit();
    }

    private boolean lit_expr() {
        // Fast lookahead: skip if current token can't start a literal
        if (!peekAnyOf(T_LIT_EXPR_START)) {
            return false;
        }
        enter(NodeType.LIT_EXPR);
        boolean result = anyOf(T_LIT_EXPR)
                || lit_object()
                || lit_array()
                || lit_template()
                || lit_regex();
        return exit(result, false);
    }

    private boolean lit_template() {
        if (!enter(NodeType.LIT_TEMPLATE, BACKTICK)) {
            return false;
        }
        while (true) {
            if (peek() == EOF) { // unbalanced backticks
                error(BACKTICK);
                break;
            }
            if (consumeIf(BACKTICK)) {
                break;
            }
            if (!consumeIf(T_STRING)) {
                if (consumeIf(DOLLAR_L_CURLY)) {
                    expr(-1, false);
                    consumeSoft(R_CURLY);
                }
            }
        }
        return exit();
    }

    private boolean unary_expr() {
        if (!enter(NodeType.UNARY_EXPR, T_UNARY_EXPR)) {
            return false;
        }
        expr(13, true);
        return exit();
    }

    private boolean math_pre_expr() {
        if (!enter(NodeType.MATH_PRE_EXPR, T_MATH_PRE_EXPR)) {
            return false;
        }
        if (!(expr(13, false) || consumeIf(NUMBER) || consumeIf(BIGINT))) {
            error(NodeType.EXPR);
        }
        return exit();
    }

    private boolean lit_object() {
        if (!enter(NodeType.LIT_OBJECT, L_CURLY)) {
            return false;
        }
        boolean hasElements = false;
        while (true) {
            if (peekIf(R_CURLY) || peekIf(EOF)) {
                break;
            }
            if (!object_elem()) {
                if (errorRecoveryEnabled && hasElements) {
                    // Only error/recover if we've parsed at least one element
                    // If first element fails, this might be a block, not an object
                    error("invalid object element");
                    recoverTo(R_CURLY, COMMA, EOF);
                    continue;
                }
                break;
            }
            hasElements = true;
        }
        boolean result = consumeIf(R_CURLY);
        return exit(result, false);
    }

    private boolean object_elem() {
        if (!enter(NodeType.OBJECT_ELEM, T_OBJECT_ELEM)) {
            return false;
        }
        // ES6 getter/setter: `get name() { ... }` or `set name(v) { ... }`.
        // `get`/`set` was consumed by enter as an IDENT. It is an accessor keyword
        // only when followed by a property-name token; otherwise fall through so
        // {get}, {get: 1}, {get() {}} etc. continue to work as regular entries.
        if (lastConsumed() == IDENT
                && ("get".equals(lastConsumedText()) || "set".equals(lastConsumedText()))
                && peekAnyOf(T_ACCESSOR_KEY_START)) {
            if (consumeIf(L_BRACKET)) {
                expr(-1, true);
                if (!consumeIf(R_BRACKET)) {
                    error(R_BRACKET);
                    return exit(false, false);
                }
            } else if (!(consumeIf(IDENT) || consumeIf(S_STRING) || consumeIf(D_STRING) || consumeIf(NUMBER) || consumeIf(BIGINT))) {
                error(IDENT, S_STRING);
                return exit(false, false);
            }
            enter(NodeType.FN_EXPR);
            fn_decl_args();
            block(true);
            exit();
            if (!(consumeIf(COMMA) || peekIf(R_CURLY))) {
                error(COMMA, R_CURLY);
            }
            return exit();
        }
        // ES6 computed key: `[expr]: value` or `[expr](args) { body }`.
        // The L_BRACKET was consumed by enter; parse the key expression and R_BRACKET here,
        // then fall through to the colon/shorthand-method branches below.
        if (lastConsumed() == L_BRACKET) {
            expr(-1, true);
            if (!consumeIf(R_BRACKET)) {
                error(R_BRACKET);
                return exit(false, false);
            }
        }
        if (consumeIf(EQ)) { // var / assigment destructuring
            expr(-1, true);
        }
        // ES6 shorthand method: `foo(args) { body }` inside an object literal.
        // Detected after the key token is consumed, before the comma/brace
        // shortcut for {foo, bar} shorthand properties.
        if (peekIf(L_PAREN)) {
            enter(NodeType.FN_EXPR);
            fn_decl_args();
            block(true);
            exit();
            if (!(consumeIf(COMMA) || peekIf(R_CURLY))) {
                error(COMMA, R_CURLY);
            }
            return exit();
        }
        if (consumeIf(COMMA) || peekIf(R_CURLY)) { // es6 enhanced object literals
            return exit();
        }
        boolean spread = false;
        if (!consumeIf(COLON)) {
            if (lastConsumed() == DOT_DOT_DOT) { // spread operator
                if (consumeIf(IDENT)) {
                    spread = true;
                } else {
                    error(IDENT);
                }
            } else {
                return exit(false, false); // could be block
            }
        }
        if (!spread) {
            expr(-1, true);
        }
        if (!(consumeIf(COMMA) || peekIf(R_CURLY))) {
            error(COMMA, R_CURLY);
        }
        return exit();
    }

    private boolean lit_array() {
        if (!enter(NodeType.LIT_ARRAY, L_BRACKET)) {
            return false;
        }
        while (true) {
            if (peekIf(R_BRACKET) || peekIf(EOF)) {
                break;
            }
            if (!array_elem()) {
                if (errorRecoveryEnabled) {
                    error("invalid array element");
                    recoverTo(R_BRACKET, COMMA, EOF);
                    continue;
                }
                break;
            }
        }
        consumeSoft(R_BRACKET);
        return exit();
    }

    private boolean array_elem() {
        enter(NodeType.ARRAY_ELEM);
        consumeIf(DOT_DOT_DOT); // spread operator
        expr(-1, false); // optional for sparse array
        if (!(consumeIf(COMMA) || peekIf(R_BRACKET) || peekIf(EOF))) {
            error(COMMA, R_BRACKET);
        }
        return exit();
    }

    private boolean lit_regex() {
        if (!enter(NodeType.LIT_REGEX, REGEX)) {
            return false;
        }
        return exit();
    }

    private boolean paren_expr() {
        if (!enter(NodeType.PAREN_EXPR, L_PAREN)) {
            return false;
        }
        expr_list(true);
        consumeSoft(R_PAREN);
        return exit();
    }

}
