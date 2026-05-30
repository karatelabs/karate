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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.karatelabs.parser.TokenType.*;

public class JsParser extends BaseParser {

    // ECMA "no-in" production flag. Set inside the for-statement init
    // expression (between `for (` and the first `;`) so the IN_EXPR branch
    // skips IN — leaving it to be consumed as the for-in / for-of header
    // token. Reset to false inside parens / brackets / function calls so
    // `for (var x = (a in b); cond; upd)` still works as a relational `in`.
    private boolean noIn;

    // EnumSet token sets for O(1) lookup in hot paths
    private static final EnumSet<TokenType> T_VAR_STMT = EnumSet.of(VAR, CONST, LET);
    private static final EnumSet<TokenType> T_ASSIGN_EXPR = EnumSet.of(EQ, PLUS_EQ, MINUS_EQ, STAR_EQ, SLASH_EQ, PERCENT_EQ, STAR_STAR_EQ, GT_GT_EQ, LT_LT_EQ, GT_GT_GT_EQ, AMP_EQ, PIPE_EQ, CARET_EQ, PIPE_PIPE_EQ, AMP_AMP_EQ, QUES_QUES_EQ);
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
            FUNCTION, CLASS, SUPER, L_PAREN, NEW, TYPEOF, VOID,            // keywords & grouping
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
            validateCoverInitializedNames(ast, false);
            // Strict-mode early errors are gated on lexical strictness, computed
            // top-down during the walk; the program starts sloppy unless it opens
            // with a "use strict" directive prologue.
            checkStrictEarlyErrors(ast, false);
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
                    // Logical-assignment operators (||=, &&=, ??=) are ES2021 and have no
                    // Annex B web-compat carve-out for CallExpression LHS — the SimpleAssignmentTarget
                    // requirement is strict. Plain `=` and the older compound operators still allow
                    // `f() = …` in non-strict mode.
                    TokenType op = node.size() > 1 && node.get(1).isToken() ? node.get(1).token.type : null;
                    boolean noCallCarveOut = op == PIPE_PIPE_EQ || op == AMP_AMP_EQ || op == QUES_QUES_EQ;
                    checkSimpleAssignmentTarget(lhs, "assignment target", noCallCarveOut);
                }
            }
            case MATH_POST_EXPR -> {
                // children: [operand, ++/--]
                if (node.size() > 0) {
                    Node operand = node.getFirst();
                    if (sawOptionalChain && subtreeContainsOptionalChain(operand)) {
                        throw new ParserException("optional chain cannot be the operand of postfix ++/--");
                    }
                    checkSimpleAssignmentTarget(operand, "operand of postfix update", false);
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
                        checkSimpleAssignmentTarget(operand, "operand of prefix update", false);
                    }
                }
            }
            case FN_TAGGED_TEMPLATE_EXPR -> {
                if (sawOptionalChain && node.size() > 0
                        && subtreeContainsOptionalChain(node.getFirst())) {
                    throw new ParserException("tagged template literal cannot follow an optional chain");
                }
            }
            // A FunctionDeclaration is a StatementListItem, never a Statement, so it
            // may not be the sole body of an iteration statement (§13.7). Unlike the
            // `if` clause (Annex B.3.4) there is no web-compat carve-out, so this is an
            // early error in BOTH sloppy and strict code — hence it lives here, not in
            // the strict-gated walk. `for (…) { function f(){} }` stays legal: the body
            // Statement wraps a BLOCK, so its direct child is BLOCK, not FN_EXPR.
            case FOR_STMT, WHILE_STMT, DO_WHILE_STMT -> checkNoFunctionDeclarationBody(node, "a loop");
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

    /** Throws if any direct {@code STATEMENT} child of {@code node} is itself a
     *  function declaration (its first non-token child is an {@code FN_EXPR}) — the
     *  body of `if` / loop / labelled clauses is a Statement, and FunctionDeclaration
     *  is a StatementListItem only. A braced body (BLOCK) is fine. */
    private static void checkNoFunctionDeclarationBody(Node node, String where) {
        for (int i = 0, n = node.size(); i < n; i++) {
            Node child = node.get(i);
            if (child.isToken() || child.type != NodeType.STATEMENT) {
                continue;
            }
            for (int j = 0, m = child.size(); j < m; j++) {
                Node inner = child.get(j);
                if (!inner.isToken()) {
                    if (inner.type == NodeType.FN_EXPR) {
                        throw new ParserException(
                                "a function declaration may not be the body of " + where);
                    }
                    break; // first non-token child decides
                }
            }
        }
    }

    /**
     * Spec strict-mode early errors. Strictness is lexical and computed top-down:
     * the program is strict iff it opens with a {@code "use strict"} directive
     * prologue; a function adds strictness if its own body carries the prologue;
     * a class body is always strict (§15.7). The test262 runner prepends a strict
     * directive for {@code flags:[onlyStrict]} tests, so this same walk covers them.
     * When strict, the following are SyntaxErrors at parse phase:
     * <ul>
     *   <li>Legacy octal ({@code 0755}) / NonOctalDecimal ({@code 08}/{@code 09})
     *       integer literals (§12.9.3.1).</li>
     *   <li>Assigning to / updating {@code eval} or {@code arguments} (§13.15.1).</li>
     *   <li>Binding {@code eval} / {@code arguments} as a function name, formal
     *       parameter, or var/let/const declaration name (BoundNames, §various).</li>
     *   <li>Duplicate formal parameter names (§15.x).</li>
     * </ul>
     * Every check is strict-gated, so a correct strictness determination is the one
     * invariant that keeps this from disturbing sloppy ({@code noStrict}) code.
     * Throws {@link ParserException} so the runner classifies it as {@code phase: parse}.
     */
    private void checkStrictEarlyErrors(Node node, boolean strict) {
        if (node == null) {
            return;
        }
        boolean childStrict = strict;
        switch (node.type) {
            case PROGRAM -> childStrict = strict || hasUseStrictPrologue(node);
            case FN_EXPR, FN_ARROW_EXPR -> {
                childStrict = strict || fnBodyHasUseStrict(node);
                if (childStrict) {
                    checkFunctionName(node);
                }
                // Parameter early errors gate themselves: duplicate BoundNames fire
                // for arrows / non-simple parameter lists even in sloppy code, and for
                // any strict function; eval/arguments only under strict.
                checkFormalParameters(node, childStrict, node.type == NodeType.FN_ARROW_EXPR);
            }
            // Duplicate BoundNames in a catch parameter are an early error always
            // (not strict-gated); eval/arguments only under strict. TRY does not
            // change lexical strictness, so childStrict stays = strict.
            case TRY_STMT -> checkCatchParameter(node, strict);
            // Class bodies — and every FN_EXPR (constructor / method) nested inside
            // — are always strict regardless of any enclosing directive (§15.7).
            case CLASS_EXPR -> childStrict = true;
            default -> {
            }
        }
        if (childStrict) {
            switch (node.type) {
                case LIT_EXPR -> checkLegacyOctalLiteral(node);
                case ASSIGN_EXPR, MATH_POST_EXPR ->
                        checkAssignTargetBinding(node.size() > 0 ? node.getFirst() : null);
                case MATH_PRE_EXPR -> {
                    if (node.size() > 1 && node.getFirst().isToken()
                            && (node.getFirst().token.type == PLUS_PLUS
                                || node.getFirst().token.type == MINUS_MINUS)) {
                        checkAssignTargetBinding(node.get(1));
                    }
                }
                case VAR_DECL -> checkVarDeclName(node);
                // Annex B.3.4 lets a FunctionDeclaration be an `if`/`else` clause body in
                // sloppy code, but that extension is NOT honored in strict mode — there it
                // is an early error (§13.6). The always-illegal loop bodies are handled
                // mode-independently in validateEarlyErrors.
                case IF_STMT -> checkNoFunctionDeclarationBody(node, "an `if` statement in strict mode");
                default -> {
                }
            }
        }
        for (int i = 0, n = node.size(); i < n; i++) {
            Node child = node.get(i);
            if (!child.isToken()) {
                checkStrictEarlyErrors(child, childStrict);
            }
        }
    }

    private static boolean isEvalOrArguments(String s) {
        return "eval".equals(s) || "arguments".equals(s);
    }

    /** Scans a {@code PROGRAM} / {@code BLOCK}'s leading directive prologue for an
     *  unescaped {@code "use strict"} / {@code 'use strict'}. Mirrors the eval-time
     *  scan in {@code Interpreter.hasUseStrictDirective}, kept parser-local so the
     *  parser carries no dependency on the interpreter package. */
    private static boolean hasUseStrictPrologue(Node body) {
        for (int i = 0, n = body.size(); i < n; i++) {
            Node child = body.get(i);
            if (child.isToken()) {
                continue; // structural L_CURLY / R_CURLY / EOF — keep scanning
            }
            if (child.type != NodeType.STATEMENT) {
                return false;
            }
            String dir = directiveString(child);
            if (dir == null) {
                return false; // first non-directive statement ends the prologue
            }
            if ("\"use strict\"".equals(dir) || "'use strict'".equals(dir)) {
                return true;
            }
            // a different directive (e.g. "use asm") — keep scanning the prologue
        }
        return false;
    }

    /** If {@code stmt} is an expression statement consisting of exactly one string
     *  literal, returns its raw token text (quotes included); else {@code null}.
     *  Shape: {@code STATEMENT > EXPR_LIST(1) > EXPR(1) > LIT_EXPR(1) > TOKEN}. A
     *  parenthesized string ({@code ("use strict")}) is a PAREN_EXPR, not LIT_EXPR,
     *  so it correctly does not count as a directive. */
    private static String directiveString(Node stmt) {
        if (stmt.size() == 0) {
            return null;
        }
        Node exprList = stmt.getFirst();
        if (exprList.type != NodeType.EXPR_LIST || exprList.size() != 1) {
            return null;
        }
        Node expr = exprList.getFirst();
        if (expr.type != NodeType.EXPR || expr.size() != 1) {
            return null;
        }
        Node lit = expr.getFirst();
        if (lit.type != NodeType.LIT_EXPR || lit.size() != 1) {
            return null;
        }
        Node tok = lit.getFirst();
        if (!tok.isToken()) {
            return null;
        }
        TokenType tt = tok.token.type;
        return (tt == S_STRING || tt == D_STRING) ? tok.getText() : null;
    }

    /** True if the function's own body block opens with a {@code "use strict"}
     *  prologue. Arrow functions with a concise (expression) body have no BLOCK and
     *  therefore can never introduce strictness on their own. */
    private static boolean fnBodyHasUseStrict(Node fn) {
        for (int i = 0, n = fn.size(); i < n; i++) {
            Node child = fn.get(i);
            if (!child.isToken() && child.type == NodeType.BLOCK) {
                return hasUseStrictPrologue(child);
            }
        }
        return false;
    }

    /** A strict function may not be named {@code eval} / {@code arguments}. The name
     *  is the single IDENT token between {@code function} and the parameter list;
     *  arrows and anonymous expressions reach FN_DECL_ARGS first and return. */
    private static void checkFunctionName(Node fn) {
        for (int i = 0, n = fn.size(); i < n; i++) {
            Node child = fn.get(i);
            if (child.isToken()) {
                if (child.token.type == IDENT) {
                    if (isEvalOrArguments(child.getText())) {
                        throw new ParserException(
                                "'" + child.getText() + "' is not a valid function name in strict mode");
                    }
                    return;
                }
            } else if (child.type == NodeType.FN_DECL_ARGS) {
                return; // reached the parameter list — function was anonymous
            }
        }
    }

    /**
     * Formal-parameter early errors over the full BoundNames of the parameter list:
     * <ul>
     *   <li>Duplicate bound names are a SyntaxError when the list is non-simple (any
     *       destructuring pattern, default, or rest element), when the function is an
     *       arrow (UniqueFormalParameters), or when the function is strict. Plain
     *       duplicate simple params in a sloppy non-arrow function stay legal.</li>
     *   <li>Under strict, no bound name may be {@code eval} / {@code arguments}.</li>
     * </ul>
     * BoundNames mirror the binding structure, so {@code ({a: x = y}) => …} binds
     * only {@code x} — not the key {@code a} or the default {@code y}.
     */
    private static void checkFormalParameters(Node fn, boolean strict, boolean isArrow) {
        Node args = null;
        for (int i = 0, n = fn.size(); i < n; i++) {
            Node child = fn.get(i);
            if (!child.isToken() && child.type == NodeType.FN_DECL_ARGS) {
                args = child;
                break;
            }
        }
        if (args == null) {
            return;
        }
        // Hot-path guard: a sloppy non-arrow function with a simple parameter list
        // can't have any of these early errors, so skip the BoundNames allocation
        // entirely (the common case — every `.map(function(x){…})` callback). The
        // nonSimple scan is allocation-free.
        boolean nonSimple = false;
        for (int i = 0, n = args.size(); i < n; i++) {
            Node arg = args.get(i);
            if (!arg.isToken() && arg.type == NodeType.FN_DECL_ARG && isNonSimpleParam(arg)) {
                nonSimple = true;
                break;
            }
        }
        if (!strict && !isArrow && !nonSimple) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (int i = 0, n = args.size(); i < n; i++) {
            Node arg = args.get(i);
            if (arg.isToken() || arg.type != NodeType.FN_DECL_ARG) {
                continue;
            }
            collectBindingBoundNames(arg, names);
        }
        if (strict) {
            for (String name : names) {
                if (isEvalOrArguments(name)) {
                    throw new ParserException(
                            "'" + name + "' is not a valid parameter name in strict mode");
                }
            }
        }
        if (strict || isArrow || nonSimple) {
            String dup = firstDuplicate(names);
            if (dup != null) {
                throw new ParserException(
                        "duplicate parameter name '" + dup + "' is not allowed here");
            }
        }
    }

    /** A FormalParameter is non-simple if it is a destructuring pattern, carries a
     *  default initializer, or is a rest element — any of which makes the whole
     *  parameter list non-simple, so duplicate bound names become an early error. */
    private static boolean isNonSimpleParam(Node arg) {
        for (int i = 0, n = arg.size(); i < n; i++) {
            Node ch = arg.get(i);
            if (ch.isToken()) {
                if (ch.token.type == EQ || ch.token.type == DOT_DOT_DOT) {
                    return true;
                }
            } else if (ch.type == NodeType.LIT_ARRAY || ch.type == NodeType.LIT_OBJECT) {
                return true;
            }
        }
        return false;
    }

    /** Catch-clause early errors: duplicate BoundNames of the catch parameter are a
     *  SyntaxError always (not strict-gated); under strict, {@code eval}/{@code arguments}
     *  may not be bound. A simple {@code catch (e)} can't collide — the duplicate rule
     *  only bites for a destructuring catch param ({@code catch ([x, x])}). */
    private static void checkCatchParameter(Node tryStmt, boolean strict) {
        // TRY_STMT: ... CATCH L_PAREN <binding> R_PAREN BLOCK ...  The binding is the
        // first node/ident between the post-CATCH L_PAREN and the matching R_PAREN.
        int catchIdx = -1;
        for (int i = 0, n = tryStmt.size(); i < n; i++) {
            Node ch = tryStmt.get(i);
            if (ch.isToken() && ch.token.type == CATCH) {
                catchIdx = i;
                break;
            }
        }
        if (catchIdx < 0) {
            return;
        }
        Node binding = null;
        for (int i = catchIdx + 1, n = tryStmt.size(); i < n; i++) {
            Node ch = tryStmt.get(i);
            if (ch.isToken()) {
                if (ch.token.type == R_PAREN) {
                    break;
                }
                if (ch.token.type == IDENT) {
                    binding = ch; // simple catch binding — no duplicate possible
                    break;
                }
                // skip the L_PAREN
            } else {
                binding = ch; // LIT_ARRAY / LIT_OBJECT pattern
                break;
            }
        }
        if (binding == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        collectBoundNames(binding, names);
        String dup = firstDuplicate(names);
        if (dup != null) {
            throw new ParserException(
                    "duplicate binding name '" + dup + "' is not allowed in a catch parameter");
        }
        if (strict) {
            for (String name : names) {
                if (isEvalOrArguments(name)) {
                    throw new ParserException(
                            "'" + name + "' is not a valid binding name in strict mode");
                }
            }
        }
    }

    /** A strict var/let/const declaration may not bind {@code eval} / {@code arguments}
     *  — including inside a destructuring pattern. (Lexical duplicate-BoundNames for
     *  let/const patterns is a distinct rule, deferred — VAR_DECL doesn't carry the
     *  let-vs-var distinction.) */
    private static void checkVarDeclName(Node varDecl) {
        List<String> names = new ArrayList<>();
        collectBindingBoundNames(varDecl, names);
        for (String name : names) {
            if (isEvalOrArguments(name)) {
                throw new ParserException(
                        "'" + name + "' is not a valid binding name in strict mode");
            }
        }
    }

    /** Bound names of one {@code FN_DECL_ARG} / {@code VAR_DECL} — the binding target
     *  before any {@code = default} initializer (the default expression binds nothing).
     *  Handles simple idents, rest ({@code ...r}), and destructuring patterns. */
    private static void collectBindingBoundNames(Node binding, List<String> out) {
        for (int i = 0, n = binding.size(); i < n; i++) {
            Node ch = binding.get(i);
            if (ch.isToken()) {
                if (ch.token.type == EQ) {
                    return; // default initializer follows — target already collected
                }
                if (ch.token.type == IDENT) {
                    out.add(ch.getText()); // simple binding, or the ...rest ident
                }
                // DOT_DOT_DOT, COMMA: structural — skip
            } else {
                collectBoundNames(ch, out); // LIT_ARRAY / LIT_OBJECT pattern
            }
        }
    }

    /** BoundNames of a binding target, mirroring the binding structure so a default
     *  value or property key contributes nothing. Handles bare/REF identifiers,
     *  default targets ({@code x = y} → {@code x}), array patterns, object patterns. */
    private static void collectBoundNames(Node n, List<String> out) {
        if (n == null) {
            return;
        }
        if (n.isToken()) {
            if (n.token.type == IDENT) {
                out.add(n.getText());
            }
            return;
        }
        switch (n.type) {
            case EXPR, EXPR_LIST, LIT_EXPR, PAREN_EXPR -> {
                for (int i = 0, c = n.size(); i < c; i++) {
                    collectBoundNames(n.get(i), out);
                }
            }
            case REF_EXPR -> {
                Node f = n.getFirst();
                if (f != null && f.isToken() && f.token.type == IDENT) {
                    out.add(f.getText());
                }
            }
            // A destructuring default `target = value`: only the target binds.
            case ASSIGN_EXPR -> {
                if (n.size() > 0) {
                    collectBoundNames(n.getFirst(), out);
                }
            }
            case LIT_OBJECT -> {
                for (int i = 0, c = n.size(); i < c; i++) {
                    Node ch = n.get(i);
                    if (!ch.isToken() && ch.type == NodeType.OBJECT_ELEM) {
                        collectObjectElemBoundNames(ch, out);
                    }
                }
            }
            case LIT_ARRAY -> {
                for (int i = 0, c = n.size(); i < c; i++) {
                    Node ch = n.get(i);
                    if (ch.isToken() || ch.type != NodeType.ARRAY_ELEM) {
                        continue;
                    }
                    // Array element: skip the leading `...` / trailing `,`, recurse
                    // into the target (a hole has no target and binds nothing).
                    for (int j = 0, e = ch.size(); j < e; j++) {
                        Node sub = ch.get(j);
                        if (!sub.isToken()) {
                            collectBoundNames(sub, out);
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    /** BoundNames of one object-pattern property: {@code {a}} / {@code {a = d}} bind
     *  {@code a}; {@code {k: target}} binds BoundNames(target); {@code {...r}} binds
     *  {@code r}. The key and any default initializer contribute nothing. A property
     *  that is really a method/accessor (carries an FN_EXPR) is not a binding. */
    private static void collectObjectElemBoundNames(Node elem, List<String> out) {
        int colonIdx = -1;
        int restIdx = -1;
        for (int i = 0, n = elem.size(); i < n; i++) {
            Node ch = elem.get(i);
            if (ch.isToken()) {
                if (ch.token.type == COLON) {
                    colonIdx = i;
                } else if (ch.token.type == DOT_DOT_DOT) {
                    restIdx = i;
                }
            } else if (ch.type == NodeType.FN_EXPR) {
                return; // method / accessor — not a binding element
            }
        }
        if (colonIdx >= 0 || restIdx >= 0) {
            // `key: target` or `...rest` — bind the first node after the marker.
            for (int i = (colonIdx >= 0 ? colonIdx : restIdx) + 1, n = elem.size(); i < n; i++) {
                Node ch = elem.get(i);
                if (!ch.isToken()) {
                    collectBoundNames(ch, out);
                    return;
                }
            }
            return;
        }
        // Shorthand `{a}` or `{a = default}`: the first IDENT token is the bound name.
        for (int i = 0, n = elem.size(); i < n; i++) {
            Node ch = elem.get(i);
            if (ch.isToken() && ch.token.type == IDENT) {
                out.add(ch.getText());
                return;
            }
        }
    }

    /** First duplicate element in {@code names}, or {@code null} if all are distinct. */
    private static String firstDuplicate(List<String> names) {
        Set<String> seen = new HashSet<>(Math.max(4, names.size() * 2));
        for (String name : names) {
            if (!seen.add(name)) {
                return name;
            }
        }
        return null;
    }

    /** A strict assignment / update may not target {@code eval} / {@code arguments}.
     *  Only a bare identifier reference is an early error; member targets
     *  ({@code arguments[0] = …}) are fine. */
    private void checkAssignTargetBinding(Node lhs) {
        if (lhs == null) {
            return;
        }
        Node n = stripExprWrappers(lhs);
        if (n != null && n.type == NodeType.REF_EXPR && n.size() == 1
                && n.getFirst().isToken() && n.getFirst().token.type == IDENT
                && isEvalOrArguments(n.getFirst().getText())) {
            throw new ParserException(
                    "invalid assignment to '" + n.getFirst().getText() + "' in strict mode");
        }
    }

    /** Strict-mode early error for a legacy octal ({@code 0755}) or NonOctalDecimal
     *  ({@code 08} / {@code 09}) integer literal — any NUMBER whose text starts with
     *  {@code 0} immediately followed by a decimal digit. {@code 0x…} / {@code 0b…} /
     *  {@code 0o…} / {@code 0.…} / {@code 0e…} have a non-digit second char and a plain
     *  {@code 0} is length 1, so all are correctly excluded. */
    private static void checkLegacyOctalLiteral(Node litExpr) {
        if (litExpr.size() != 1) {
            return;
        }
        Node tok = litExpr.getFirst();
        if (!tok.isToken() || tok.token.type != NUMBER) {
            return;
        }
        String text = tok.getText();
        if (text.length() >= 2 && text.charAt(0) == '0') {
            char c = text.charAt(1);
            if (c >= '0' && c <= '9') {
                throw new ParserException("octal literals are not allowed in strict mode");
            }
        }
    }

    /**
     * Spec early error: a {@code CoverInitializedName} (the shorthand-with-default
     * {@code IDENT = AssignmentExpression} inside object-literal braces) is only
     * legal when the surrounding {@code {...}} ends up refined as an
     * {@code ObjectAssignmentPattern} or {@code ObjectBindingPattern}. In a plain
     * object literal it must be a SyntaxError (§12.2.6.1).
     * <p>
     * The parser accepts the cover form unconditionally in {@code object_elem()}
     * because at parse time it can't yet tell whether {@code {...}} is the LHS of
     * an assignment / a binding / an arrow param. This walk catches the cases
     * where the surrounding shape never turns out to be a destructuring target.
     */
    private void validateCoverInitializedNames(Node node, boolean inPattern) {
        if (node == null) return;
        if (node.type == NodeType.OBJECT_ELEM && hasCoverInitForm(node) && !inPattern) {
            throw new ParserException("invalid shorthand initializer: only allowed in destructuring pattern");
        }
        if (inPattern && node.type == NodeType.LIT_ARRAY) {
            validateRestElementRules(node);
        }
        for (int i = 0, n = node.size(); i < n; i++) {
            Node child = node.get(i);
            if (child.isToken()) continue;
            validateCoverInitializedNames(child, childInPatternContext(node, i, inPattern));
        }
    }

    /**
     * Spec early errors for {@code BindingRestElement} / {@code AssignmentRestElement}
     * inside an {@code ArrayBindingPattern} / {@code ArrayAssignmentPattern}:
     * <ul>
     *   <li>Rest element must be the last binding element — {@code [...x, y]} is
     *       invalid (§13.3.3).</li>
     *   <li>Rest element cannot carry a default initializer — {@code [...x = 1]}
     *       is invalid (rest target is a DestructuringAssignmentTarget, not an
     *       AssignmentExpression).</li>
     * </ul>
     * Called only when the surrounding {@code LIT_ARRAY} is in pattern context;
     * a spread {@code [...arr, b]} in a regular array literal stays valid.
     */
    private static void validateRestElementRules(Node litArray) {
        int lastArrayElemIdx = -1;
        for (int i = 0, n = litArray.size(); i < n; i++) {
            Node ch = litArray.get(i);
            if (!ch.isToken() && ch.type == NodeType.ARRAY_ELEM) {
                lastArrayElemIdx = i;
            }
        }
        for (int i = 0, n = litArray.size(); i < n; i++) {
            Node ch = litArray.get(i);
            if (ch.isToken() || ch.type != NodeType.ARRAY_ELEM) continue;
            if (!hasRestPrefix(ch)) continue;
            if (i != lastArrayElemIdx) {
                throw new ParserException("invalid destructuring: rest element must be the last binding element");
            }
            if (restHasInitializer(ch)) {
                throw new ParserException("invalid destructuring: rest element cannot have an initializer");
            }
        }
    }

    private static boolean hasRestPrefix(Node arrayElem) {
        if (arrayElem.size() == 0) return false;
        Node first = arrayElem.getFirst();
        return first.isToken() && first.token.type == DOT_DOT_DOT;
    }

    /**
     * For an ARRAY_ELEM that starts with {@code ...}, returns true iff the target
     * expression after the DOT_DOT_DOT carries a top-level assignment — i.e. the
     * rest target is being given a default value, which is invalid.
     */
    private static boolean restHasInitializer(Node arrayElem) {
        boolean sawDot = false;
        for (int i = 0, n = arrayElem.size(); i < n; i++) {
            Node ch = arrayElem.get(i);
            if (ch.isToken()) {
                if (ch.token.type == DOT_DOT_DOT) sawDot = true;
                continue;
            }
            if (!sawDot) continue;
            Node inner = ch;
            while (inner != null && inner.size() == 1
                    && (inner.type == NodeType.EXPR
                        || inner.type == NodeType.EXPR_LIST
                        || inner.type == NodeType.LIT_EXPR)) {
                Node next = inner.getFirst();
                if (next.isToken()) break;
                inner = next;
            }
            if (inner != null && inner.type == NodeType.ASSIGN_EXPR) return true;
        }
        return false;
    }

    /**
     * Pattern-context propagation rules. Top-down: a {@code LIT_OBJECT} (or
     * {@code LIT_ARRAY}) that's a destructuring target distributes pattern context
     * to its element values via {@code OBJECT_ELEM} / {@code ARRAY_ELEM}; the
     * default-initializer expression after {@code =} in a CoverInitializedName is
     * expression context, not pattern. Parens reset to expression — a
     * parenthesized destructuring pattern is invalid per spec.
     */
    private static boolean childInPatternContext(Node parent, int i, boolean inPattern) {
        switch (parent.type) {
            case ASSIGN_EXPR, VAR_DECL, FN_DECL_ARG -> {
                // LHS / binding position is at index 0.
                return i == 0;
            }
            case FOR_STMT -> {
                // for-of / for-in LHS — the non-token child immediately preceding
                // the OF / IN token. for-loops with semicolons stay false.
                for (int j = 0; j < parent.size(); j++) {
                    Node sib = parent.get(j);
                    if (sib.isToken() && (sib.token.type == OF || sib.token.type == IN)) {
                        return i == j - 1;
                    }
                }
                return false;
            }
            case TRY_STMT -> {
                // CatchParameter — the child between the CATCH-introduced L_PAREN
                // and its R_PAREN is a BindingPattern, so cover-init shorthand
                // (`catch ({a = 1})`) is legal there. The try/finally blocks stay false.
                for (int j = 1; j < parent.size(); j++) {
                    Node sib = parent.get(j);
                    if (sib.isToken() && sib.token.type == L_PAREN
                            && parent.get(j - 1).isToken() && parent.get(j - 1).token.type == CATCH) {
                        return i == j + 1;
                    }
                }
                return false;
            }
            case OBJECT_ELEM -> {
                // OBJECT_ELEM children: [key-bits..., (COLON | EQ), value/init].
                // Only propagate pattern context through the value of a colon form;
                // the initializer after `=` is always expression context.
                if (!inPattern) return false;
                return !hasCoverInitForm(parent);
            }
            case ARRAY_ELEM, LIT_OBJECT, LIT_ARRAY, LIT_EXPR, EXPR, EXPR_LIST -> {
                return inPattern;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Detect the CoverInitializedName shape inside an {@code OBJECT_ELEM}:
     * an {@code EQ} token appearing at the top of the element, not after a
     * {@code COLON} (which would make it a regular {@code key: value = expr}
     * where the inner {@code =} belongs to an AssignmentExpression value).
     */
    private static boolean hasCoverInitForm(Node objElem) {
        for (int i = 0, n = objElem.size(); i < n; i++) {
            Node ch = objElem.get(i);
            if (!ch.isToken()) continue;
            TokenType t = ch.token.type;
            if (t == EQ) return true;
            if (t == COLON) return false;
        }
        return false;
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
    private static void checkSimpleAssignmentTarget(Node lhs, String siteName, boolean noCallCarveOut) {
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
                // `this` lexes as IDENT (see JsLexer.keywordOrIdent) but per spec
                // the ThisExpression has AssignmentTargetType=invalid.
                if (n.size() >= 1 && n.getFirst().isToken()
                        && n.getFirst().token.type == IDENT
                        && "this".equals(n.getFirst().getText())) {
                    throw new ParserException("invalid " + siteName + ": this");
                }
            }
            case REF_DOT_EXPR, REF_BRACKET_EXPR -> {
                // Plain member access; a `?.` would have been caught earlier by
                // the optional-chain branch above with a more specific message.
                // `import.meta` is a meta-property whose AssignmentTargetType is
                // invalid; `import` is not a reserved word in our lexer so it
                // parses as a normal REF_DOT_EXPR — flag the literal shape here.
                if (n.type == NodeType.REF_DOT_EXPR && isImportMeta(n)) {
                    throw new ParserException("invalid " + siteName + ": import.meta");
                }
            }
            case FN_CALL_EXPR -> {
                // Web-compat carve-out (Annex B B.3.5): allow `f() = 1` in non-strict mode.
                // Logical-assignment operators (||=, &&=, ??=) are ES2021 and the carve-out
                // does NOT apply to them; the caller passes noCallCarveOut=true in that case.
                if (noCallCarveOut) {
                    throw new ParserException("invalid " + siteName + ": call expression");
                }
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
     * True iff {@code node} is the literal shape {@code import.meta}: a
     * {@code REF_DOT_EXPR} whose head is a bare {@code REF_EXPR(IDENT('import'))}
     * and whose property is the IDENT {@code meta}.
     */
    private static boolean isImportMeta(Node node) {
        if (node.type != NodeType.REF_DOT_EXPR || node.size() < 3) {
            return false;
        }
        Node head = node.getFirst();
        if (head.type != NodeType.REF_EXPR || head.size() != 1) {
            return false;
        }
        Node headIdent = head.getFirst();
        if (!headIdent.isToken() || headIdent.token.type != IDENT
                || !"import".equals(headIdent.getText())) {
            return false;
        }
        Node prop = node.get(2);
        return prop.isToken() && prop.token.type == IDENT && "meta".equals(prop.getText());
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
                || class_expr() // class declarations don't need eos (ASI), like function decls
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
            // CatchParameter → BindingIdentifier | BindingPattern. Reuse the same
            // binding-target trio as var_decl so `catch ([a,b])` / `catch ({e})`
            // parse through the destructuring cover-grammar, not just bare idents.
            if (consumeIf(L_PAREN) && (lit_array() || lit_object() || consumeIf(IDENT)) && consumeIf(R_PAREN) && block(true)) {
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
        boolean prevNoIn = noIn;
        noIn = true;
        try {
            if (!(peekIf(SEMI) || var_stmt(true) || expr_list(false))) {
                error(NodeType.VAR_STMT, NodeType.EXPR);
            }
        } finally {
            noIn = prevNoIn;
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
                || class_expr()
                || super_expr()
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
            } else if (priority < 8 && !noIn && enter(NodeType.IN_EXPR, IN)) {
                // ECMA relational `key in obj` — true iff `obj` (or its
                // prototype chain) has a property named `key`. Same precedence
                // as `instanceof`. The for-stmt parser checks T_FOR_IN_OF
                // before entering expression land for `for (var x in y)`
                // (no `=` initializer), so consuming IN here doesn't affect
                // that path. The pathological `for (var x = a in b; ...)`
                // form parses `(a in b)` as the initializer and then fails at
                // the missing `;`.
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
        // Track both () and {} depth so destructured object params (`({a}) =>`) and
        // default expressions containing braces don't trip the early bail.
        // A top-level SEMI is still a hard bail — arrow-param lists can't span statements.
        int parenDepth = 0;
        int curlyDepth = 0;
        int size = tokens.size();
        for (int i = getPosition(); i < size; i++) {
            TokenType t = tokens.get(i).type;
            if (t == L_PAREN) {
                parenDepth++;
            } else if (t == R_PAREN) {
                if (--parenDepth == 0 && curlyDepth == 0) {
                    return i + 1 < size && tokens.get(i + 1).type == EQ_GT;
                }
            } else if (t == L_CURLY) {
                curlyDepth++;
            } else if (t == R_CURLY) {
                curlyDepth--;
            } else if (t == EOF) {
                return false;
            } else if (t == SEMI && curlyDepth == 0) {
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

    // `super` primary — yields a bare SUPER_EXPR. The trailing `(args)`
    // (super-call) or `.x` / `[expr]` (super-property) is chained on top by
    // expr_rhs, producing FN_CALL_EXPR / REF_DOT_EXPR / REF_BRACKET_EXPR with
    // SUPER_EXPR as the base — the interpreter recognizes that base for the
    // home-object dispatch.
    private boolean super_expr() {
        if (!enter(NodeType.SUPER_EXPR, SUPER)) {
            return false;
        }
        return exit();
    }

    // Tokens that can begin a (non-computed) class member name. `get`/`set`/
    // `static`/`constructor` all lex as IDENT and are disambiguated in
    // class_element by whether another key token follows.
    private static final EnumSet<TokenType> T_CLASS_KEY_NAME =
            EnumSet.of(IDENT, S_STRING, D_STRING, NUMBER);

    // ES6 class declaration / expression. Phase 1: constructor + methods +
    // static methods + get/set accessors + computed keys. No `extends` / `super`
    // / fields yet (those parse-fail and are skip-listed). The CLASS token and
    // optional name (IDENT) become the leading children; member definitions
    // follow as CLASS_METHOD nodes; brace/semicolon tokens are interleaved and
    // ignored at eval time.
    private boolean class_expr() {
        if (!enter(NodeType.CLASS_EXPR, CLASS)) {
            return false;
        }
        consumeIf(IDENT); // optional class name
        if (consumeIf(EXTENDS)) { // heritage: `extends <LeftHandSideExpression>`
            expr(13, true); // priority 13 = LHS level (same as `new` operand)
        }
        if (!consumeIf(L_CURLY)) {
            error(L_CURLY);
            return exit(false, false);
        }
        while (true) {
            if (peekIf(R_CURLY) || peekIf(EOF)) {
                break;
            }
            if (consumeIf(SEMI)) { // empty class element — allowed and ignored
                continue;
            }
            if (!class_element()) {
                if (errorRecoveryEnabled) {
                    error("invalid class element");
                    recoverTo(R_CURLY, SEMI, EOF);
                    continue;
                }
                return exit(false, false);
            }
        }
        return exit(consumeIf(R_CURLY), true);
    }

    // One class member (CLASS_METHOD node used for methods AND fields):
    // [modifier-tokens...] <key> ( FN_EXPR | field-tail ). A leading
    // `static`/`get`/`set` IDENT is a modifier only when another key token
    // follows; otherwise it is the member name itself. The key is either a
    // single name token or a computed `[expr]` (L_BRACKET, EXPR, R_BRACKET).
    // A method body is a synthetic FN_EXPR (FN_DECL_ARGS + BLOCK), matching the
    // object-literal shorthand-method shape so evalFnExpr handles it directly.
    // A field has no FN_EXPR — an optional `= EXPR` initializer then ASI; eval
    // distinguishes the two by the presence of the trailing FN_EXPR.
    private boolean class_element() {
        enter(NodeType.CLASS_METHOD);
        while (true) {
            if (consumeIf(L_BRACKET)) { // computed key [expr] — always the final key
                expr(-1, true);
                if (!consumeIf(R_BRACKET)) {
                    error(R_BRACKET);
                    return exit(false, false);
                }
                break;
            }
            if (!peekAnyOf(T_CLASS_KEY_NAME)) {
                error("class member name");
                return exit(false, false);
            }
            consumeNext();
            if (peekIf(L_PAREN)) {
                break; // the just-consumed token is the method name (the key)
            }
            String text = lastConsumedText();
            if (("static".equals(text) || "get".equals(text) || "set".equals(text))
                    && (peekAnyOf(T_CLASS_KEY_NAME) || peekIf(L_BRACKET))) {
                continue; // it was a modifier — loop to consume the real key
            }
            // Not followed by `(` and not a modifier — a class field (`x` / `x = expr`).
            return class_field_tail();
        }
        if (!peekIf(L_PAREN)) {
            return class_field_tail(); // computed-key field: `[k]` / `[k] = expr`
        }
        enter(NodeType.FN_EXPR);
        fn_decl_args();
        block(true);
        exit();
        return exit();
    }

    // Public class field tail: optional `= AssignmentExpression`, ended by `;`
    // or ASI. No FN_EXPR child — that's how eval tells a field from a method.
    private boolean class_field_tail() {
        if (consumeIf(EQ)) {
            expr(-1, true);
        }
        consumeIf(SEMI); // optional; ASI otherwise (next element / `}` / newline)
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
        // Same NoIn reset as paren_expr — `for (var x = {k: a in b}; ...)` and
        // `for ([x = a in b] of ...)` should treat the nested `in` as relational.
        boolean prevNoIn = noIn;
        noIn = false;
        try {
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
        } finally {
            noIn = prevNoIn;
        }
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
            } else if (!anyOf(T_ACCESSOR_KEY_START)) {
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
            if (lastConsumed() == DOT_DOT_DOT) { // spread operator: {...AssignmentExpression}
                spread = true;
                expr(-1, true); // any expression — {...fn()}, {...obj.m()}, {...{x:1}}, not just a bare ident
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
        boolean prevNoIn = noIn;
        noIn = false;
        try {
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
        } finally {
            noIn = prevNoIn;
        }
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
        // Parens reset the no-in restriction — `for (var x = (a in b); ...)`
        // works as a relational `in` because the parens reopen IN_EXPR.
        boolean prevNoIn = noIn;
        noIn = false;
        try {
            expr_list(true);
        } finally {
            noIn = prevNoIn;
        }
        consumeSoft(R_PAREN);
        return exit();
    }

}
