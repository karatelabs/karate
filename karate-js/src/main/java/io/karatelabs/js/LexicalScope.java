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

import io.karatelabs.parser.JsParser;
import io.karatelabs.parser.Node;
import io.karatelabs.parser.NodeType;
import io.karatelabs.parser.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * The lexically declared names of one scope node — the {@code let} / {@code const}
 * / {@code class} bindings it must create <i>uninitialized</i> on entry, so that
 * every read or write before the declaration runs throws (the temporal dead
 * zone). Spec: BlockDeclarationInstantiation (§14.2.3) for a Block and a switch
 * CaseBlock, FunctionDeclarationInstantiation step 34 for a function body,
 * GlobalDeclarationInstantiation for a Script, and the TDZ environment of a
 * {@code for} head (§14.7.4.2 / §14.7.5.6 ForIn/OfHeadEvaluation).
 *
 * <p>Instantiating up front is what makes the binding exist before its
 * declaration: a name read in the TDZ no longer resolves to an outer binding of
 * the same name, an assignment no longer lands on the outer binding or an
 * implicit global, and a function hoisted in the scope captures the very
 * binding cell the declaration later initializes ({@link CoreContext#declare}
 * initializes the pre-instantiated cell in place rather than pushing a new one).
 *
 * <p>Computed once per scope node and cached on {@link Node#meta} (BLOCK,
 * FOR_STMT, SWITCH_STMT — the PROGRAM node's meta belongs to its
 * {@link SlotTable}, and a program is entered once per eval, so its names are
 * computed per entry instead). Scopes that declare nothing share {@link #NONE},
 * so the per-entry cost for them is one volatile read and a compare — what the
 * slot-frame re-arm read cost before. Benign race per the {@link Node} contract:
 * every thread computes the same value.
 *
 * <p>Storage split: a name the running frame's {@link SlotTable} holds as a
 * let/const slot is armed to {@link SlotTable#TDZ} in the frame; every other
 * name gets an uninitialized {@link BindingSlot} in the store at the scope's
 * level. The slot lookup is cached per (node, table).
 */
final class LexicalScope {

    static final LexicalScope NONE = new LexicalScope(new String[0], new BindScope[0]);

    final String[] names;
    final BindScope[] scopes;

    /** (table, slots) pair for the last frame table this scope ran under —
     *  one immutable holder so a racing reader never sees a torn pair. */
    private volatile FrameSlots frameSlots;

    private record FrameSlots(SlotTable table, int[] slots) {
    }

    private LexicalScope(String[] names, BindScope[] scopes) {
        this.names = names;
        this.scopes = scopes;
    }

    /** The cached instance for a BLOCK / FOR_STMT / SWITCH_STMT node. */
    static LexicalScope of(Node node) {
        Object m = node.meta;
        if (m instanceof LexicalScope ls) {
            return ls;
        }
        LexicalScope ls = compute(node);
        node.meta = ls;
        return ls;
    }

    /** Uncached — for the PROGRAM node (see the class doc). */
    static LexicalScope compute(Node node) {
        List<String> names = new ArrayList<>(4);
        List<Boolean> consts = new ArrayList<>(4);
        switch (node.type) {
            case BLOCK, PROGRAM -> JsParser.lexicalDeclarations(node, names, consts);
            case SWITCH_STMT -> {
                // §14.12: every clause's StatementList shares the one CaseBlock scope
                for (int i = 0, n = node.size(); i < n; i++) {
                    Node child = node.get(i);
                    if (child.type == NodeType.CASE_BLOCK || child.type == NodeType.DEFAULT_BLOCK) {
                        JsParser.lexicalDeclarations(child, names, consts);
                    }
                }
            }
            case FOR_STMT -> {
                // the ForDeclaration / LexicalDeclaration of the head (C-style
                // init, or the binding of for-in / for-of)
                Node head = node.size() > 2 ? node.get(2) : null;
                if (head != null && head.type == NodeType.VAR_STMT
                        && head.getFirstToken().type != TokenType.VAR) {
                    boolean isConst = head.getFirstToken().type == TokenType.CONST;
                    for (int i = 0, n = head.size(); i < n; i++) {
                        Node declarator = head.get(i);
                        if (declarator.type == NodeType.VAR_DECL) {
                            int before = names.size();
                            JsParser.boundNames(declarator, names);
                            for (int j = before; j < names.size(); j++) {
                                consts.add(isConst);
                            }
                        }
                    }
                }
            }
            default -> {
            }
        }
        if (names.isEmpty()) {
            return NONE;
        }
        BindScope[] scopes = new BindScope[names.size()];
        for (int i = 0; i < scopes.length; i++) {
            scopes[i] = consts.get(i) ? BindScope.CONST : BindScope.LET;
        }
        return new LexicalScope(names.toArray(new String[0]), scopes);
    }

    /** Create this scope's bindings in their TDZ. Call right after
     *  {@link CoreContext#enterScope} (the store bindings take its level) and
     *  before function declarations are hoisted, so hoisted closures capture
     *  the uninitialized cells. Never call on {@link #NONE} (callers test). */
    void instantiate(CoreContext context) {
        Object[] frame = context.frame;
        int[] slots = frame == null ? null : frameSlots(context.frameTable);
        for (int i = 0; i < names.length; i++) {
            int s = slots == null ? -1 : slots[i];
            if (s >= 0) {
                frame[s] = SlotTable.TDZ;
            } else {
                context.declareUninitialized(names[i], scopes[i]);
            }
        }
    }

    /** The frame slot of each of {@link #names} under {@code table}, -1 for a name
     *  kept in the store. Also read by {@link CoreContext#getLocalVariables}. */
    int[] frameSlots(SlotTable table) {
        FrameSlots fs = frameSlots;
        if (fs == null || fs.table != table) {
            fs = new FrameSlots(table, table.lexicalSlots(names));
            frameSlots = fs;
        }
        return fs.slots;
    }

    /** Script top level: the store only (top-level names are never framed),
     *  and a name already lexically bound at this level by an EARLIER eval on
     *  the same Engine is left as it is. Spec-wise re-declaring it is a
     *  SyntaxError; karate-js deliberately allows it (REPL semantics, see
     *  {@link CoreContext#declare}), and the re-declaration then re-initializes
     *  that cell in place — so it keeps its value, and closures from the
     *  earlier eval keep working, until the new declaration runs. */
    void instantiateGlobal(CoreContext context) {
        for (int i = 0; i < names.length; i++) {
            BindingSlot existing = context.bindings == null ? null : context.bindings.getSlot(names[i]);
            if (existing != null && existing.scope != null && existing.level == context.currentLevel) {
                continue;
            }
            context.declareUninitialized(names[i], scopes[i]);
        }
    }

}
