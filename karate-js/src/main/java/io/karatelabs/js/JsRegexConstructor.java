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
 * JavaScript RegExp constructor function. Mirrors the
 * {@link JsNumberConstructor} / {@link JsBooleanConstructor} pattern so
 * {@code RegExp.prototype} resolves the {@link JsRegexPrototype} singleton
 * instead of undefined. {@link JsRegex} stays as the instance type returned
 * by {@code new RegExp(pattern, flags)} and {@code /foo/} literals.
 */
class JsRegexConstructor extends JsFunction {

    static final JsRegexConstructor INSTANCE = new JsRegexConstructor();

    private static final byte METHOD_ATTRS = WRITABLE | CONFIGURABLE | PropertySlot.INTRINSIC;

    private JsRegexConstructor() {
        this.name = "RegExp";
        this.length = 2;
        installIntrinsics();
        registerForEngineReset();
    }

    private void installIntrinsics() {
        defineOwn("escape", new JsBuiltinMethod("escape", 1, (JsInvokable) this::escape), METHOD_ATTRS);
        defineOwn("prototype", JsRegexPrototype.INSTANCE, PropertySlot.INTRINSIC);
    }

    @Override
    protected void clearEngineState() {
        super.clearEngineState();
        installIntrinsics();
    }

    @Override
    public Object call(Context context, Object... args) {
        if (args.length == 0) {
            return new JsRegex();
        }
        String pattern = args[0].toString();
        String flags = args.length > 1 ? args[1].toString() : "";
        return new JsRegex(pattern, flags);
    }

    // RegExp.escape (ES2025). Spec §22.2.7.1: TypeError on non-String input;
    // walks code points, prefixes leading digit/letter with \x##, otherwise
    // delegates each code point to EncodeForRegExpEscape.
    private Object escape(Object[] args) {
        Object s = args.length > 0 ? args[0] : Terms.UNDEFINED;
        if (!(s instanceof String str)) {
            throw JsErrorException.typeError("RegExp.escape called on non-string");
        }
        StringBuilder out = new StringBuilder(str.length() + 8);
        for (int i = 0; i < str.length(); ) {
            int cp = str.codePointAt(i);
            if (out.length() == 0 && isAsciiDigitOrLetter(cp)) {
                out.append("\\x").append(toHexLowerPad(cp, 2));
            } else {
                encodeForRegExpEscape(out, cp);
            }
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    private static boolean isAsciiDigitOrLetter(int cp) {
        return (cp >= '0' && cp <= '9') || (cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z');
    }

    // Spec EncodeForRegExpEscape (§22.2.7.2). SyntaxCharacter | "/" -> "\" + char.
    // ControlEscape (\t \n \v \f \r) -> "\" + letter.
    // otherPunctuators | WhiteSpace | LineTerminator | lone-surrogate -> hex/unicode escape.
    // Else -> raw UTF-16 code units.
    private static void encodeForRegExpEscape(StringBuilder out, int cp) {
        if (isSyntaxCharacterOrSolidus(cp)) {
            out.append('\\').appendCodePoint(cp);
            return;
        }
        char controlEscape = controlEscapeLetter(cp);
        if (controlEscape != 0) {
            out.append('\\').append(controlEscape);
            return;
        }
        if (isOtherPunctuator(cp) || isWhiteSpace(cp) || isLineTerminator(cp) || isLoneSurrogate(cp)) {
            if (cp <= 0xFF) {
                out.append("\\x").append(toHexLowerPad(cp, 2));
            } else if (cp <= 0xFFFF) {
                out.append("\\u").append(toHexLowerPad(cp, 4));
            } else {
                // Supplementary: emit both code units as backslash-u-XXXX
                int high = 0xD800 + ((cp - 0x10000) >> 10);
                int low = 0xDC00 + ((cp - 0x10000) & 0x3FF);
                out.append("\\u").append(toHexLowerPad(high, 4));
                out.append("\\u").append(toHexLowerPad(low, 4));
            }
            return;
        }
        out.appendCodePoint(cp);
    }

    private static boolean isSyntaxCharacterOrSolidus(int cp) {
        return switch (cp) {
            case '^', '$', '\\', '.', '*', '+', '?', '(', ')', '[', ']', '{', '}', '|', '/' -> true;
            default -> false;
        };
    }

    private static char controlEscapeLetter(int cp) {
        return switch (cp) {
            case 0x09 -> 't';
            case 0x0A -> 'n';
            case 0x0B -> 'v';
            case 0x0C -> 'f';
            case 0x0D -> 'r';
            default -> 0;
        };
    }

    private static boolean isOtherPunctuator(int cp) {
        // ",-=<>#&!%:;@~'`" and 0x0022 (quotation mark)
        return switch (cp) {
            case ',', '-', '=', '<', '>', '#', '&', '!', '%', ':', ';', '@', '~', '\'', '`', '"' -> true;
            default -> false;
        };
    }

    private static boolean isWhiteSpace(int cp) {
        // Spec WhiteSpace minus the ones covered by ControlEscape (HT/VT/FF):
        // SPACE, NBSP, BOM, plus USP. Match the spec-listed values precisely.
        if (cp == 0x0020 || cp == 0x00A0 || cp == 0xFEFF) return true;
        if (cp == 0x1680) return true; // OGHAM SPACE MARK
        if (cp >= 0x2000 && cp <= 0x200A) return true;
        return cp == 0x202F || cp == 0x205F || cp == 0x3000;
    }

    private static boolean isLineTerminator(int cp) {
        // LF/CR already handled by ControlEscape. LS/PS only.
        return cp == 0x2028 || cp == 0x2029;
    }

    private static boolean isLoneSurrogate(int cp) {
        return cp >= 0xD800 && cp <= 0xDFFF;
    }

    private static String toHexLowerPad(int value, int width) {
        String hex = Integer.toHexString(value);
        if (hex.length() >= width) return hex;
        return "0".repeat(width - hex.length()) + hex;
    }

}
