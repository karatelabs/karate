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

import java.math.BigInteger;

/**
 * Singleton prototype for BigInt instances.
 * Contains instance methods toString(radix) and valueOf.
 * Inherits from JsObjectPrototype.
 */
class JsBigIntPrototype extends Prototype {

    static final JsBigIntPrototype INSTANCE = new JsBigIntPrototype();

    private JsBigIntPrototype() {
        super(JsObjectPrototype.INSTANCE);
        install("toString", 0, this::toStringMethod);
        install("valueOf", 0, this::valueOf);
        install("toLocaleString", 0, this::toStringMethod);
    }

    private Object toStringMethod(Context context, Object[] args) {
        BigInteger n = asBigInt(context);
        // Spec: only `undefined` (or absent) defaults to radix 10; `null` runs through
        // ToInteger → 0 → RangeError. Same shape as Number.prototype.toString.
        if (args.length > 0 && args[0] != Terms.UNDEFINED) {
            Object radixArg = args[0];
            // Object → ToPrimitive (hint "number"). If valueOf/toString are non-callable
            // ToPrimitive throws TypeError (per spec); error flows through context for
            // user-thrown valueOf bodies.
            if (radixArg instanceof ObjectLike && context instanceof CoreContext cc) {
                radixArg = Terms.toPrimitive(radixArg, "number", cc);
                if (cc.isError()) return Terms.UNDEFINED;
            }
            // ToIntegerOrInfinity rejects BigInt — spec mandates TypeError before RangeError.
            if (radixArg instanceof BigInteger) {
                throw JsErrorException.typeError("Cannot convert a BigInt to a number");
            }
            int radix = Terms.objectToNumber(radixArg).intValue();
            if (radix < 2 || radix > 36) {
                throw JsErrorException.rangeError("toString() radix must be between 2 and 36");
            }
            return n.toString(radix);
        }
        return n.toString();
    }

    private Object valueOf(Context context, Object[] args) {
        return asBigInt(context);
    }

    private static BigInteger asBigInt(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsBigInt jb) {
            return jb.value;
        }
        if (thisObj instanceof BigInteger bi) {
            return bi;
        }
        throw JsErrorException.typeError("BigInt.prototype method called on non-BigInt");
    }

}
