/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
package io.karatelabs.js;

import io.karatelabs.parser.Node;
import io.karatelabs.parser.NodeType;
import io.karatelabs.parser.TokenType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-function symbol table: maps the function-scoped locals that can be
 * proven safe to a dense frame index, so a call frame stores them in a plain
 * {@code Object[]} instead of the name-keyed {@link BindingsStore}.
 *
 * <p>Computed lazily, once per function-definition {@link Node} (cached on
 * {@link Node#meta}; racing threads compute an identical table — the analysis
 * is deterministic — so the race is benign). Identifier occurrences that
 * statically resolve to a frame local get {@link Node#slot} stamped for the
 * interpreter's fast paths; every name-string path stays correct through the
 * routing in {@link CoreContext#get}/{@link CoreContext#update}/
 * {@link CoreContext#declare}/{@link CoreContext#hasKey}.
 *
 * <p>The hybrid boundary. Frame-eligible names split into two classes:
 * <ul>
 *   <li><b>Function-scoped</b> — simple params, {@code var}s, function
 *       declarations, and {@code let}/{@code const} declared directly in the
 *       function body block. Registered in {@link #indexOf} so name-string
 *       paths route to the slot.</li>
 *   <li><b>Scope-confined</b> — {@code let}/{@code const} declared directly
 *       in a nested block or a C-style for-init. They get slots and node
 *       annotations <i>inside their declaring subtree only</i>, and are
 *       deliberately absent from {@link #indexOf}: outside the subtree the
 *       name resolves exactly as today (the store never held it). Their
 *       declaring block carries a re-arm list ({@link Node#meta}, an
 *       {@code int[]}) resetting the slots to {@link #UNDECLARED} on entry,
 *       so loop iterations and pre-declaration reads behave as today.</li>
 * </ul>
 * A name is eligible only when it is <b>never referenced from a nested
 * function</b> (else closures must keep the {@link BindingsStore} cell
 * machinery, which is what they snapshot) and never rebound — {@code let} /
 * {@code const} additionally require a single declaration of that name in the
 * whole function, so shadowing stays dynamic. Frame locals are therefore
 * invisible to {@link CoreContext#resolve} by construction. Globals and
 * top-level scripts are untouched.
 *
 * <p>The frame preserves the engine's <i>dynamic</i> declaration semantics
 * exactly: a slot starts as {@link #UNDECLARED}, and reads/writes of an
 * undeclared slot fall back to the legacy name-keyed chain (which today
 * resolves the name in an outer scope or the globals, since the store lacks
 * the name until its statement runs). An uninitialized {@code let x;} holds
 * {@link #TDZ}: reads throw the same ReferenceError {@code readSlot} throws,
 * writes initialize.
 */
final class SlotTable {

    /** Kill switch: -Dkarate.js.slotFrames=false restores the pre-frame
     *  behavior bit-for-bit. Mutable (not final) so conformance tests can
     *  compare both modes in one JVM. */
    static volatile boolean ENABLED = !"false".equals(System.getProperty("karate.js.slotFrames"));

    /** Sentinel for a slot whose declaration statement has not executed yet
     *  (or whose scope was exited / not re-entered). Reads and writes fall
     *  back to the legacy name-keyed chain. */
    static final Object UNDECLARED = new Object() {
        @Override
        public String toString() {
            return "<undeclared>";
        }
    };

    /** Sentinel for a declared-but-uninitialized let/const ({@code let x;}).
     *  Reads throw; writes initialize. Reported as UNDEFINED in BindEvents,
     *  mirroring the store's value for an uninitialized binding. */
    static final Object TDZ = new Object() {
        @Override
        public String toString() {
            return "<tdz>";
        }
    };

    /** Cached "analysis declined" marker on {@link Node#meta}. */
    private static final Object BAIL = new Object();

    /** Cached "loop-free body, analysis deferred to the second call" marker on
     *  {@link Node#meta}. A loop-free body cannot amortize analysis within one
     *  call, so the cost is only paid once a function object proves hot; a
     *  function called once (the Large-1KB shape: many functions, one call
     *  each) never pays it. */
    private static final Object DEFERRED = new Object();

    static final byte KIND_PARAM = 0;
    static final byte KIND_VAR = 1;
    static final byte KIND_FN_DECL = 2;
    static final byte KIND_LET = 3;
    static final byte KIND_CONST = 4;

    final String[] names;
    final byte[] kinds;
    /** Param index (aligned with the function's argNodes) → slot, or -1 when
     *  that param is STORE-classified and binds through the legacy path. */
    final int[] paramSlots;
    /** Function-scoped names only — scope-confined lets are deliberately
     *  absent so name-keyed access outside their subtree resolves as today. */
    private final Map<String, Integer> byName;

    private SlotTable(String[] names, byte[] kinds, int[] paramSlots, Map<String, Integer> byName) {
        this.names = names;
        this.kinds = kinds;
        this.paramSlots = paramSlots;
        this.byName = byName;
    }

    int size() {
        return names.length;
    }

    int indexOf(String name) {
        Integer i = byName.get(name);
        return i == null ? -1 : i;
    }

    Object[] newFrame() {
        Object[] frame = new Object[names.length];
        java.util.Arrays.fill(frame, UNDECLARED);
        return frame;
    }

    /** Cold-path TDZ error, shared by the hot fast paths so their bytecode
     *  stays small enough to inline reliably. Same message as readSlot's. */
    static JsErrorException tdzError(String name) {
        return JsErrorException.referenceError("cannot access '" + name + "' before initialization");
    }

    /** Creation-time entry point — cached on the function-definition node.
     *  Analyzes eagerly only when the body contains a loop (the win then lives
     *  inside a single call — the Mixed shape); loop-free bodies return null
     *  and wait for {@link #forNodeForced} at the second call. */
    static SlotTable forNodeEager(Node fnNode, List<Node> argNodes, Node body) {
        Object cached = fnNode.meta;
        if (cached != null) {
            return cached == BAIL || cached == DEFERRED ? null : (SlotTable) cached;
        }
        if (!hasLoop(body)) {
            fnNode.meta = DEFERRED;
            return null;
        }
        SlotTable table = analyze(argNodes, body);
        fnNode.meta = table == null ? BAIL : table;
        return table;
    }

    /** Second-call entry point: analyze a deferred (or never-seen) body. */
    static SlotTable forNodeForced(Node fnNode, List<Node> argNodes, Node body) {
        Object cached = fnNode.meta;
        if (cached != null && cached != DEFERRED) {
            return cached == BAIL ? null : (SlotTable) cached;
        }
        SlotTable table = analyze(argNodes, body);
        fnNode.meta = table == null ? BAIL : table;
        return table;
    }

    /** Program-scope entry point — cached on the PROGRAM node. Only the
     *  scope-confined class exists at program scope: every declaration in the
     *  program body itself (var, function, top-level let/const) stays in the
     *  name-keyed store, because that store IS the {@code Engine.bindings}
     *  cross-eval host contract (getBindings visibility, REPL re-declaration
     *  via evalId, implicit-global interop). A let/const lexically confined to
     *  a nested block or C-style for-init is invisible to that contract — it
     *  never survives its block, and a nested-function reference forces it to
     *  STORE exactly as inside a function — so it gets a frame slot and the
     *  same re-arm machinery. {@code byName} stays empty, so every name-keyed
     *  path (host get/put, hoisting, indirect eval — always global-scoped
     *  here) routes to the store untouched.
     *  <p>
     *  Gated on a top-level loop for the same reason function analysis defers
     *  loop-free bodies: a loop-free program cannot amortize the walk within
     *  its one eval. There is no second-call hook for a program, so the gate
     *  is permanent per node (BAIL, not DEFERRED). */
    static SlotTable forProgram(Node program) {
        Object cached = program.meta;
        if (cached != null) {
            return cached == BAIL ? null : (SlotTable) cached;
        }
        if (!hasLoop(program)) {
            program.meta = BAIL;
            return null;
        }
        SlotTable table = analyze(List.of(), program, true);
        program.meta = table == null ? BAIL : table;
        return table;
    }

    /** True when the body contains a loop outside nested function/class
     *  subtrees — those belong to their own function's decision. */
    private static boolean hasLoop(Node node) {
        if (node.isToken()) {
            return false;
        }
        switch (node.type) {
            case FOR_STMT, WHILE_STMT, DO_WHILE_STMT -> {
                return true;
            }
            case FN_EXPR, FN_ARROW_EXPR, CLASS_EXPR -> {
                return false;
            }
            default -> {
                for (int i = 0, n = node.size(); i < n; i++) {
                    if (hasLoop(node.get(i))) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    //=== analysis =====================================================================================================

    private static final class LetDecl {
        final byte kind;
        final Node scopeNode;   // declaring BLOCK, FOR_STMT (C-style init), or the body block
        final Node bindingNode; // the IDENT token node — annotated for bindLeaf/evalAssign

        LetDecl(byte kind, Node scopeNode, Node bindingNode) {
            this.kind = kind;
            this.scopeNode = scopeNode;
            this.bindingNode = bindingNode;
        }
    }

    private static SlotTable analyze(List<Node> argNodes, Node body) {
        return analyze(argNodes, body, false);
    }

    /** {@code programScope}: the body is a PROGRAM node — the function-scoped
     *  class is forced empty (those names belong to the store per the host
     *  contract) and only scope-confined lets are framed. */
    private static SlotTable analyze(List<Node> argNodes, Node body, boolean programScope) {
        int paramCount = argNodes.size();
        String[] paramNames = new String[paramCount];
        for (int i = 0; i < paramCount; i++) {
            Node argNode = argNodes.get(i);
            Node first = argNode.getFirst();
            if (first.getFirstToken().type == TokenType.DOT_DOT_DOT) {
                return null; // rest param — legacy path for the whole function
            }
            if (first.type == NodeType.LIT_ARRAY || first.type == NodeType.LIT_OBJECT) {
                return null; // destructuring param — legacy path
            }
            String name = first.getText();
            for (int j = 0; j < i; j++) {
                if (name.equals(paramNames[j])) {
                    return null; // duplicate param name — legacy path
                }
            }
            paramNames[i] = name;
        }
        Walker w = new Walker();
        w.walk(body, body.type == NodeType.BLOCK ? body : null);
        // Default-value expressions run in the function context too — walk them
        // for captured/annotation coverage (they contain no declarations).
        for (Node argNode : argNodes) {
            Node defaultExpr = defaultExpr(argNode);
            if (defaultExpr != null) {
                w.walk(defaultExpr, null);
            }
        }
        // let/const eligibility: exactly one declaration of the name in the
        // whole function (no shadowing anywhere), and not otherwise bound.
        // An ineligible let/const forces EVERY declaration of that name to
        // STORE — otherwise a name-routed var slot would swallow the dynamic
        // let shadow that still runs through the legacy store.
        Set<String> paramSet = new LinkedHashSet<>(List.of(paramNames));
        for (Map.Entry<String, LetDecl> e : w.letConsts.entrySet()) {
            String name = e.getKey();
            if (w.declCount.getOrDefault(name, 0) != 1
                    || paramSet.contains(name) || w.vars.contains(name) || w.fnDecls.contains(name)) {
                w.storeForced.add(name);
            }
        }
        w.letConsts.keySet().removeIf(w.storeForced::contains);
        // Function-scoped frame names: params + vars + fn-decls, minus anything
        // captured by a nested function or rebound dynamically. At program
        // scope this class stays empty — those names are the store's.
        Set<String> functionScoped = new LinkedHashSet<>();
        if (!programScope) {
            for (String p : paramNames) {
                functionScoped.add(p);
            }
            functionScoped.addAll(w.vars);
            functionScoped.addAll(w.fnDecls);
            functionScoped.removeAll(w.storeForced);
        }
        // body-top-level let/const behave like function-scoped names (their
        // scope is the whole body, which ends when the frame does)
        List<String> scopedNames = new ArrayList<>();
        for (Map.Entry<String, LetDecl> e : w.letConsts.entrySet()) {
            if (w.storeForced.contains(e.getKey()) || w.captured.contains(e.getKey())) {
                continue;
            }
            if (!programScope && e.getValue().scopeNode == body) {
                functionScoped.add(e.getKey());
            } else {
                scopedNames.add(e.getKey());
            }
        }
        functionScoped.removeAll(w.captured);
        functionScoped.remove("arguments");
        functionScoped.remove("this");
        if (functionScoped.isEmpty() && scopedNames.isEmpty()) {
            return null; // nothing to gain — skip the frame entirely
        }
        if (functionScoped.size() + scopedNames.size() > Short.MAX_VALUE) {
            return null; // Node.slot is a short — an absurd shape, but bail cleanly
        }
        int total = functionScoped.size() + scopedNames.size();
        String[] names = new String[total];
        byte[] kinds = new byte[total];
        Map<String, Integer> byName = new HashMap<>(functionScoped.size() * 2);
        Map<String, Integer> allSlots = new HashMap<>(total * 2);
        int idx = 0;
        for (String name : functionScoped) {
            names[idx] = name;
            LetDecl let = w.letConsts.get(name);
            kinds[idx] = let != null ? let.kind
                    : w.fnDecls.contains(name) && !w.vars.contains(name) ? KIND_FN_DECL
                    : w.vars.contains(name) ? KIND_VAR
                    : KIND_PARAM;
            byName.put(name, idx);
            allSlots.put(name, idx);
            idx++;
        }
        // scope-confined lets: slots + subtree annotations, no byName entry;
        // group re-arm lists per declaring scope node
        Map<Node, List<Integer>> rearm = new LinkedHashMap<>();
        for (String name : scopedNames) {
            LetDecl let = w.letConsts.get(name);
            names[idx] = name;
            kinds[idx] = let.kind;
            allSlots.put(name, idx);
            rearm.computeIfAbsent(let.scopeNode, k -> new ArrayList<>()).add(idx);
            idx++;
        }
        int[] paramSlots = new int[paramCount];
        for (int i = 0; i < paramCount; i++) {
            Integer s = byName.get(paramNames[i]);
            paramSlots[i] = s == null ? -1 : s;
        }
        SlotTable table = new SlotTable(names, kinds, paramSlots, byName);
        // annotate: function-scoped names everywhere in the body (minus nested
        // functions); scope-confined names within their declaring subtree only
        if (!byName.isEmpty()) { // empty at program scope — skip the whole-body walk
            annotate(body, byName);
            for (Node argNode : argNodes) {
                Node defaultExpr = defaultExpr(argNode);
                if (defaultExpr != null) {
                    annotate(defaultExpr, byName);
                }
            }
        }
        for (String name : scopedNames) {
            LetDecl let = w.letConsts.get(name);
            Integer slot = allSlots.get(name);
            if (let.scopeNode.type == NodeType.SWITCH_STMT) {
                // the switch discriminant is OUTSIDE the CaseBlock environment —
                // annotate only the clauses so it keeps resolving outward
                for (int i = 0, n = let.scopeNode.size(); i < n; i++) {
                    Node child = let.scopeNode.get(i);
                    if (child.type == NodeType.CASE_BLOCK || child.type == NodeType.DEFAULT_BLOCK) {
                        annotate(child, Map.of(name, slot));
                    }
                }
            } else {
                annotate(let.scopeNode, Map.of(name, slot));
            }
        }
        // binding-node annotation for every slotted let/const, so the
        // declaration statement (bindLeaf / evalAssign) writes the slot
        for (Map.Entry<String, LetDecl> e : w.letConsts.entrySet()) {
            Integer slot = allSlots.get(e.getKey());
            if (slot != null) {
                e.getValue().bindingNode.slot = slot.shortValue();
            }
        }
        // publish re-arm lists last (volatile write per node)
        for (Map.Entry<Node, List<Integer>> e : rearm.entrySet()) {
            int[] slots = new int[e.getValue().size()];
            for (int i = 0; i < slots.length; i++) {
                slots[i] = e.getValue().get(i);
            }
            e.getKey().meta = slots;
        }
        return table;
    }

    /** Mirrors the default-param detection in JsFunctionNode.bindArgsAndExecute. */
    private static Node defaultExpr(Node argNode) {
        if (argNode.type == NodeType.FN_DECL_ARG
                && argNode.size() >= 3
                && argNode.get(1).isToken()
                && argNode.get(1).token.type == TokenType.EQ
                && argNode.get(2).type == NodeType.EXPR) {
            return argNode.get(2);
        }
        return null;
    }

    /** Collects declarations in the function's own scope; nested function /
     *  class subtrees are not descended into — every IDENT token below them
     *  is over-approximated as captured, which can only push a name to the
     *  STORE side (never the unsafe direction). The {@code letScope} argument
     *  is the node a directly-declared let/const would be scoped to: the
     *  enclosing BLOCK (via STATEMENT passthrough), a FOR_STMT for its
     *  C-style init, or null (→ STORE) everywhere else. */
    private static final class Walker {
        final Set<String> vars = new LinkedHashSet<>();
        final Set<String> fnDecls = new LinkedHashSet<>();
        final Set<String> storeForced = new LinkedHashSet<>();
        final Set<String> captured = new LinkedHashSet<>();
        final Map<String, LetDecl> letConsts = new LinkedHashMap<>();
        final Map<String, Integer> declCount = new HashMap<>();

        private void count(String name) {
            declCount.merge(name, 1, Integer::sum);
        }

        void walk(Node node, Node letScope) {
            if (node.isToken()) {
                return;
            }
            switch (node.type) {
                case FN_EXPR -> {
                    // evalFnExpr binds the name into the defining context for
                    // any named FN_EXPR (declaration or expression position).
                    if (node.size() > 1 && node.get(1).isToken() && node.get(1).token.type == TokenType.IDENT) {
                        fnDecls.add(node.get(1).getText());
                    }
                    collectCaptured(node);
                }
                case FN_ARROW_EXPR, CLASS_EXPR -> collectCaptured(node);
                case STATEMENT -> {
                    // a statement is transparent for let-scope: BLOCK → STATEMENT
                    // → VAR_STMT keeps the block as the declaring scope
                    for (int i = 0, n = node.size(); i < n; i++) {
                        walk(node.get(i), letScope);
                    }
                }
                case BLOCK -> {
                    for (int i = 0, n = node.size(); i < n; i++) {
                        walk(node.get(i), node);
                    }
                }
                case FOR_STMT -> {
                    // C-style init VAR_STMT is let-scoped to the FOR_STMT itself
                    boolean cStyle = node.size() > 3 && node.get(2).type == NodeType.VAR_STMT
                            && node.get(3).isToken() && node.get(3).token.type == TokenType.SEMI;
                    for (int i = 0, n = node.size(); i < n; i++) {
                        Node child = node.get(i);
                        walk(child, cStyle && i == 2 ? node : null);
                    }
                }
                case VAR_STMT -> {
                    boolean isVar = node.getFirstToken().type == TokenType.VAR;
                    byte kind = node.getFirstToken().type == TokenType.CONST ? KIND_CONST : KIND_LET;
                    for (int i = 0, n = node.size(); i < n; i++) {
                        Node child = node.get(i);
                        if (child.type != NodeType.VAR_DECL) {
                            continue;
                        }
                        Node binding = child.getFirst();
                        if (binding.isToken() && binding.token.type == TokenType.IDENT) {
                            String name = binding.getText();
                            count(name);
                            if (isVar) {
                                vars.add(name);
                            } else if (letScope == null) {
                                storeForced.add(name);
                            } else {
                                LetDecl prev = letConsts.put(name, new LetDecl(kind, letScope, binding));
                                if (prev != null) {
                                    storeForced.add(name); // declCount also catches this
                                }
                            }
                        } else {
                            // destructuring pattern — STORE for every bound name
                            for (Node ident : binding.findChildren(TokenType.IDENT)) {
                                storeForced.add(ident.getText());
                                count(ident.getText());
                            }
                        }
                        // walk the initializer (and any nested expressions)
                        for (int j = 1, m = child.size(); j < m; j++) {
                            walk(child.get(j), null);
                        }
                    }
                }
                case SWITCH_STMT -> {
                    // §14.12.11: clause-level let/const scope to the whole
                    // CaseBlock — the SWITCH_STMT carries their re-arm list
                    // (evalSwitchStmt enters one BLOCK scope and re-arms, so
                    // the bindings get TDZ from CaseBlock entry). The
                    // discriminant evaluates in the outer environment; the
                    // annotate pass skips it (see the scopedNames loop).
                    for (int i = 0, n = node.size(); i < n; i++) {
                        Node child = node.get(i);
                        if (child.type == NodeType.CASE_BLOCK || child.type == NodeType.DEFAULT_BLOCK) {
                            for (int j = 0, m = child.size(); j < m; j++) {
                                walk(child.get(j), node);
                            }
                        } else {
                            walk(child, null);
                        }
                    }
                }
                case TRY_STMT -> {
                    // catch (param) binds via context.put at function level;
                    // keep those names on the STORE side.
                    if (node.size() > 4 && node.get(2).isToken() && node.get(2).token.type == TokenType.CATCH
                            && node.get(3).isToken() && node.get(3).token.type == TokenType.L_PAREN) {
                        Node param = node.get(4);
                        if (param.isToken() && param.token.type == TokenType.IDENT) {
                            storeForced.add(param.getText());
                            count(param.getText());
                        } else {
                            for (Node ident : param.findChildren(TokenType.IDENT)) {
                                storeForced.add(ident.getText());
                                count(ident.getText());
                            }
                        }
                    }
                    for (int i = 0, n = node.size(); i < n; i++) {
                        walk(node.get(i), null);
                    }
                }
                default -> {
                    for (int i = 0, n = node.size(); i < n; i++) {
                        walk(node.get(i), null);
                    }
                }
            }
        }

        private void collectCaptured(Node node) {
            if (node.isToken()) {
                if (node.token.type == TokenType.IDENT) {
                    captured.add(node.getText());
                }
                return;
            }
            for (int i = 0, n = node.size(); i < n; i++) {
                collectCaptured(node.get(i));
            }
        }
    }

    /** Stamp {@link Node#slot} on identifier occurrences of {@code names}.
     *  Skips nested function / class subtrees — their occurrences belong to
     *  their own function's analysis. */
    private static void annotate(Node node, Map<String, Integer> names) {
        if (node.isToken()) {
            return;
        }
        switch (node.type) {
            case FN_EXPR, FN_ARROW_EXPR, CLASS_EXPR -> {
                // not ours
            }
            case REF_EXPR -> {
                Node first = node.getFirst();
                if (first.isToken() && first.token.type == TokenType.IDENT) {
                    Integer idx = names.get(node.getText());
                    if (idx != null) {
                        node.slot = idx.shortValue();
                    }
                } else {
                    // REF_EXPR wrapping an arrow (or other) expression
                    annotate(first, names);
                }
            }
            default -> {
                for (int i = 0, n = node.size(); i < n; i++) {
                    annotate(node.get(i), names);
                }
            }
        }
    }

}
