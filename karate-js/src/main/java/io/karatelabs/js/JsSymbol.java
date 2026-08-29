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

/**
 * A symbol value: identity plus an optional description. Two kinds, stored
 * differently on purpose.
 * <p>
 * A <b>minted</b> symbol ({@code Symbol(desc)}) has no string key at all. It
 * addresses a separate symbol-keyed store on {@link JsObject}, keyed by this
 * object's identity — the same shape ES2022 private names already use. Because
 * a minted symbol never enters the string key space, every string-key surface
 * ({@code Object.keys} / {@code getOwnPropertyNames} / {@code for...in} /
 * {@code JSON.stringify}) skips it <i>by construction</i>, with no predicate
 * that could misclassify a customer payload's key.
 * <p>
 * A <b>well-known</b> symbol ({@code Symbol.iterator} and friends) is a string
 * key — {@code "@@iterator"} — because the engine's iteration, ToPrimitive and
 * toStringTag dispatch is built on those strings. It coerces to that key, so it
 * stays visible to the string-key surfaces. That is a pre-existing deviation,
 * documented in {@code docs/JS_ENGINE.md}.
 * <p>
 * {@code typeof} reports {@code "symbol"} for both. Still an object underneath
 * (identity equality, {@code Object.prototype} methods).
 */
class JsSymbol extends JsObject {

    /** The well-known symbols, in the order {@code ContextRoot} installs them on
     *  the {@code Symbol} global. */
    static final String[] WELL_KNOWN = {
            "iterator", "asyncIterator", "toPrimitive", "toStringTag", "hasInstance",
            "isConcatSpreadable", "species", "match", "matchAll", "replace",
            "search", "split", "unscopables"
    };

    static String keyOf(String wellKnownName) {
        return "@@" + wellKnownName;
    }

    /** Non-null only for a well-known symbol: the engine-internal string key it
     *  coerces to. A minted symbol has none — it is addressed by identity. */
    final String wellKnownKey;

    private final String description;

    /** A well-known symbol, over the engine-internal key it coerces to. */
    JsSymbol(String wellKnownKey) {
        this.wellKnownKey = wellKnownKey;
        this.description = wellKnownKey;
    }

    private JsSymbol(String description, boolean minted) {
        this.wellKnownKey = null;
        this.description = description;
    }

    /** {@code Symbol(desc)} — a fresh identity, distinct from every other. */
    static JsSymbol mint(String description) {
        return new JsSymbol(description, true);
    }

    /**
     * The symbol that addresses a symbol-keyed store, or {@code null} when this
     * key is not one — a well-known symbol routes to its string key instead.
     * The single seam every property site uses to decide which store to hit.
     */
    static JsSymbol keyedBy(Object key) {
        return key instanceof JsSymbol s && s.wellKnownKey == null ? s : null;
    }

    /** {@code SymbolDescriptiveString} for a minted symbol, the engine-internal
     *  key for a well-known one. Reached by {@code String(sym)} and console
     *  logging only — implicit ToString / ToPrimitive throws a TypeError, which
     *  is what the spec requires. */
    @Override
    public String toString() {
        return wellKnownKey != null ? wellKnownKey : "Symbol(" + description + ")";
    }

}
