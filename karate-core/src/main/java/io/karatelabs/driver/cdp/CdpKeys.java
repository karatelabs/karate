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
package io.karatelabs.driver.cdp;

import io.karatelabs.driver.Keys;

/**
 * CDP-based implementation of keyboard actions.
 * Uses CDP Input.dispatchKeyEvent for low-level keyboard control.
 */
public class CdpKeys implements Keys {

    private final CdpClient cdp;
    private int modifiers = 0;

    // Modifier flags
    private static final int ALT_FLAG = 1;
    private static final int CTRL_FLAG = 2;
    private static final int META_FLAG = 4;
    private static final int SHIFT_FLAG = 8;

    public CdpKeys(CdpClient cdp) {
        this.cdp = cdp;
    }

    @Override
    public Keys type(String text) {
        for (char c : text.toCharArray()) {
            String s = String.valueOf(c);
            if (isSpecialKey(s)) {
                pressSpecialKey(s);
            } else {
                typeChar(c);
            }
        }
        return this;
    }

    @Override
    public Keys press(String key) {
        // Support "+" notation like Playwright: "Control+a", "Shift+ArrowLeft", "Control+Shift+a"
        if (key.contains("+")) {
            return pressPlusNotation(key);
        }
        if (isSpecialKey(key)) {
            pressSpecialKey(key);
        } else {
            for (char c : key.toCharArray()) {
                typeChar(c);
            }
        }
        return this;
    }

    /**
     * Parse and execute "+" notation key combinations.
     * Examples: "Control+a", "Shift+ArrowLeft", "Control+Shift+ArrowLeft", "Meta+Shift+p"
     */
    private Keys pressPlusNotation(String combo) {
        String[] parts = combo.split("\\+");
        if (parts.length < 2) {
            // No actual combo, just press the key
            return press(parts[0]);
        }

        // All parts except the last are modifiers
        String[] modifiers = new String[parts.length - 1];
        for (int i = 0; i < parts.length - 1; i++) {
            modifiers[i] = resolveKeyName(parts[i]);
        }

        // Last part is the key to press
        String finalKey = resolveKeyName(parts[parts.length - 1]);

        return combo(modifiers, finalKey);
    }

    /**
     * Resolve a key name (e.g., "Control", "Shift", "ArrowLeft", "a") to its constant or character.
     */
    private String resolveKeyName(String name) {
        return switch (name) {
            // Modifiers
            case "Control", "Ctrl" -> CONTROL;
            case "Shift" -> SHIFT;
            case "Alt" -> ALT;
            case "Meta", "Command", "Cmd" -> META;
            // Arrow keys
            case "ArrowLeft", "Left" -> LEFT;
            case "ArrowRight", "Right" -> RIGHT;
            case "ArrowUp", "Up" -> UP;
            case "ArrowDown", "Down" -> DOWN;
            // Navigation
            case "Enter" -> ENTER;
            case "Tab" -> TAB;
            case "Backspace" -> BACKSPACE;
            case "Delete" -> DELETE;
            case "Escape", "Esc" -> ESCAPE;
            case "Home" -> HOME;
            case "End" -> END;
            case "PageUp" -> PAGE_UP;
            case "PageDown" -> PAGE_DOWN;
            case "Insert" -> INSERT;
            case "Space" -> SPACE;
            // Function keys
            case "F1" -> F1;
            case "F2" -> F2;
            case "F3" -> F3;
            case "F4" -> F4;
            case "F5" -> F5;
            case "F6" -> F6;
            case "F7" -> F7;
            case "F8" -> F8;
            case "F9" -> F9;
            case "F10" -> F10;
            case "F11" -> F11;
            case "F12" -> F12;
            // Default: treat as literal character(s)
            default -> name;
        };
    }

    @Override
    public Keys down(String key) {
        int flag = getModifierFlag(key);
        if (flag > 0) {
            modifiers |= flag;
        }
        // Send keyDown for any key, not just modifiers (matches Puppeteer)
        KeyInfo info = isSpecialKey(key) ? getKeyInfo(key) : getKeyInfoForChar(key.charAt(0));
        dispatchRawKeyDown(info);
        return this;
    }

    @Override
    public Keys up(String key) {
        int flag = getModifierFlag(key);
        if (flag > 0) {
            modifiers &= ~flag;
        }
        // Send keyUp for any key, not just modifiers (matches Puppeteer)
        KeyInfo info = isSpecialKey(key) ? getKeyInfo(key) : getKeyInfoForChar(key.charAt(0));
        dispatchKeyUp(info);
        return this;
    }

    @Override
    public Keys combo(String[] modifierKeys, String key) {
        // Press modifiers
        for (String mod : modifierKeys) {
            down(mod);
        }

        // Press the key
        press(key);

        // Release modifiers in reverse order
        for (int i = modifierKeys.length - 1; i >= 0; i--) {
            up(modifierKeys[i]);
        }

        return this;
    }

    @Override
    public Keys ctrl(String key) {
        return combo(new String[]{CONTROL}, key);
    }

    @Override
    public Keys alt(String key) {
        return combo(new String[]{ALT}, key);
    }

    @Override
    public Keys shift(String key) {
        return combo(new String[]{SHIFT}, key);
    }

    @Override
    public Keys meta(String key) {
        return combo(new String[]{META}, key);
    }

    private void typeChar(char c) {
        // Apply Shift modifier to character if Shift is held
        boolean shiftHeld = (modifiers & SHIFT_FLAG) != 0;
        char effectiveChar = shiftHeld ? applyShift(c) : c;

        KeyInfo info = getKeyInfoForChar(effectiveChar);
        // Keep the original code (physical key) but use shifted text
        if (shiftHeld && c >= 'a' && c <= 'z') {
            info.code = "Key" + Character.toUpperCase(c);
        }

        dispatchRawKeyDown(info);
        // Use Input.insertText for the actual text insertion — bypasses keyboard
        // layout issues (e.g., '!' being misinterpreted without proper Shift+Digit1)
        // while still firing keydown/keyup for framework compatibility
        if ((modifiers & ~SHIFT_FLAG) == 0) {
            cdp.method("Input.insertText")
                    .param("text", String.valueOf(effectiveChar))
                    .send();
        }
        dispatchKeyUp(info);
    }

    /**
     * Apply Shift modifier to a character (like pressing Shift+key).
     */
    private char applyShift(char c) {
        // Letters become uppercase
        if (c >= 'a' && c <= 'z') {
            return Character.toUpperCase(c);
        }
        // Number row shifted characters
        return switch (c) {
            case '1' -> '!';
            case '2' -> '@';
            case '3' -> '#';
            case '4' -> '$';
            case '5' -> '%';
            case '6' -> '^';
            case '7' -> '&';
            case '8' -> '*';
            case '9' -> '(';
            case '0' -> ')';
            case '-' -> '_';
            case '=' -> '+';
            case '[' -> '{';
            case ']' -> '}';
            case '\\' -> '|';
            case ';' -> ':';
            case '\'' -> '"';
            case ',' -> '<';
            case '.' -> '>';
            case '/' -> '?';
            case '`' -> '~';
            default -> c;
        };
    }

    private void pressSpecialKey(String key) {
        KeyInfo info = getKeyInfo(key);
        // Special keys need rawKeyDown + char (with proper text) + keyUp
        dispatchRawKeyDown(info);
        // Enter key needs char event with \r to trigger form submit
        if (info.text != null && !info.text.isEmpty()) {
            dispatchChar(info);
        }
        dispatchKeyUp(info);
    }

    private void dispatchRawKeyDown(KeyInfo info) {
        CdpMessage message = cdp.method("Input.dispatchKeyEvent")
                .param("type", "rawKeyDown")  // rawKeyDown works better than keyDown
                .param("modifiers", modifiers);

        if (info.key != null) message.param("key", info.key);
        if (info.code != null) message.param("code", info.code);
        if (info.text != null) message.param("text", info.text);
        if (info.windowsVirtualKeyCode > 0) message.param("windowsVirtualKeyCode", info.windowsVirtualKeyCode);
        if (info.nativeVirtualKeyCode > 0) message.param("nativeVirtualKeyCode", info.nativeVirtualKeyCode);
        if (info.location > 0) message.param("location", info.location);

        message.send();
    }

    private void dispatchKeyUp(KeyInfo info) {
        CdpMessage message = cdp.method("Input.dispatchKeyEvent")
                .param("type", "keyUp")
                .param("modifiers", modifiers);

        if (info.key != null) message.param("key", info.key);
        if (info.code != null) message.param("code", info.code);
        if (info.windowsVirtualKeyCode > 0) message.param("windowsVirtualKeyCode", info.windowsVirtualKeyCode);
        if (info.nativeVirtualKeyCode > 0) message.param("nativeVirtualKeyCode", info.nativeVirtualKeyCode);
        if (info.location > 0) message.param("location", info.location);

        message.send();
    }

    private void dispatchChar(KeyInfo info) {
        if (info.text == null || info.text.isEmpty()) {
            return;
        }
        CdpMessage message = cdp.method("Input.dispatchKeyEvent")
                .param("type", "char")
                .param("modifiers", modifiers)
                .param("text", info.text);
        if (info.windowsVirtualKeyCode > 0) message.param("windowsVirtualKeyCode", info.windowsVirtualKeyCode);
        message.send();
    }

    private boolean isSpecialKey(String key) {
        if (key == null || key.isEmpty()) return false;
        char c = key.charAt(0);
        return c >= '\uE000' && c <= '\uE03D';
    }

    private int getModifierFlag(String key) {
        if (key == null || key.isEmpty()) return 0;
        return switch (key) {
            case SHIFT -> SHIFT_FLAG;
            case CONTROL -> CTRL_FLAG;
            case ALT -> ALT_FLAG;
            case META -> META_FLAG;  // COMMAND is alias for META
            default -> 0;
        };
    }

    private KeyInfo getKeyInfoForChar(char c) {
        KeyInfo info = new KeyInfo();
        info.text = String.valueOf(c);
        info.key = info.text;

        // Determine key code
        if (c >= 'a' && c <= 'z') {
            info.code = "Key" + Character.toUpperCase(c);
            info.windowsVirtualKeyCode = Character.toUpperCase(c);
        } else if (c >= 'A' && c <= 'Z') {
            info.code = "Key" + c;
            info.windowsVirtualKeyCode = c;
        } else if (c >= '0' && c <= '9') {
            info.code = "Digit" + c;
            info.windowsVirtualKeyCode = c;
        } else if (c == ' ') {
            info.key = " ";
            info.code = "Space";
            info.windowsVirtualKeyCode = 32;
        } else {
            // Punctuation and special characters - use proper key codes
            switch (c) {
                case '.' -> { info.code = "Period"; info.windowsVirtualKeyCode = 190; }
                case ',' -> { info.code = "Comma"; info.windowsVirtualKeyCode = 188; }
                case ';' -> { info.code = "Semicolon"; info.windowsVirtualKeyCode = 186; }
                case '\'' -> { info.code = "Quote"; info.windowsVirtualKeyCode = 222; }
                case '[' -> { info.code = "BracketLeft"; info.windowsVirtualKeyCode = 219; }
                case ']' -> { info.code = "BracketRight"; info.windowsVirtualKeyCode = 221; }
                case '\\' -> { info.code = "Backslash"; info.windowsVirtualKeyCode = 220; }
                case '/' -> { info.code = "Slash"; info.windowsVirtualKeyCode = 191; }
                case '`' -> { info.code = "Backquote"; info.windowsVirtualKeyCode = 192; }
                case '-' -> { info.code = "Minus"; info.windowsVirtualKeyCode = 189; }
                case '=' -> { info.code = "Equal"; info.windowsVirtualKeyCode = 187; }
                default -> info.windowsVirtualKeyCode = 0; // Let text handle it
            }
        }

        return info;
    }

    private KeyInfo getKeyInfo(String specialKey) {
        KeyInfo info = new KeyInfo();
        info.text = "";

        if (specialKey == null || specialKey.isEmpty()) {
            return info;
        }

        char c = specialKey.charAt(0);
        switch (c) {
            case '\uE003' -> { info.key = "Backspace"; info.code = "Backspace"; info.windowsVirtualKeyCode = 8; }
            case '\uE004' -> { info.key = "Tab"; info.code = "Tab"; info.windowsVirtualKeyCode = 9; }
            case '\uE006', '\uE007' -> { info.key = "Enter"; info.code = "Enter"; info.windowsVirtualKeyCode = 13; info.text = "\r"; }
            case '\uE008' -> { info.key = "Shift"; info.code = "ShiftLeft"; info.windowsVirtualKeyCode = 16; info.location = 1; }
            case '\uE009' -> { info.key = "Control"; info.code = "ControlLeft"; info.windowsVirtualKeyCode = 17; info.location = 1; }
            case '\uE00A' -> { info.key = "Alt"; info.code = "AltLeft"; info.windowsVirtualKeyCode = 18; info.location = 1; }
            case '\uE00C' -> { info.key = "Escape"; info.code = "Escape"; info.windowsVirtualKeyCode = 27; }
            case '\uE00D' -> { info.key = " "; info.code = "Space"; info.windowsVirtualKeyCode = 32; info.text = " "; }
            case '\uE00E' -> { info.key = "PageUp"; info.code = "PageUp"; info.windowsVirtualKeyCode = 33; }
            case '\uE00F' -> { info.key = "PageDown"; info.code = "PageDown"; info.windowsVirtualKeyCode = 34; }
            case '\uE010' -> { info.key = "End"; info.code = "End"; info.windowsVirtualKeyCode = 35; }
            case '\uE011' -> { info.key = "Home"; info.code = "Home"; info.windowsVirtualKeyCode = 36; }
            case '\uE012' -> { info.key = "ArrowLeft"; info.code = "ArrowLeft"; info.windowsVirtualKeyCode = 37; }
            case '\uE013' -> { info.key = "ArrowUp"; info.code = "ArrowUp"; info.windowsVirtualKeyCode = 38; }
            case '\uE014' -> { info.key = "ArrowRight"; info.code = "ArrowRight"; info.windowsVirtualKeyCode = 39; }
            case '\uE015' -> { info.key = "ArrowDown"; info.code = "ArrowDown"; info.windowsVirtualKeyCode = 40; }
            case '\uE016' -> { info.key = "Insert"; info.code = "Insert"; info.windowsVirtualKeyCode = 45; }
            case '\uE017' -> { info.key = "Delete"; info.code = "Delete"; info.windowsVirtualKeyCode = 46; }
            // Numpad keys
            case '\uE01A' -> { info.key = "0"; info.code = "Numpad0"; info.windowsVirtualKeyCode = 96; info.text = "0"; info.location = 3; }
            case '\uE01B' -> { info.key = "1"; info.code = "Numpad1"; info.windowsVirtualKeyCode = 97; info.text = "1"; info.location = 3; }
            case '\uE01C' -> { info.key = "2"; info.code = "Numpad2"; info.windowsVirtualKeyCode = 98; info.text = "2"; info.location = 3; }
            case '\uE01D' -> { info.key = "3"; info.code = "Numpad3"; info.windowsVirtualKeyCode = 99; info.text = "3"; info.location = 3; }
            case '\uE01E' -> { info.key = "4"; info.code = "Numpad4"; info.windowsVirtualKeyCode = 100; info.text = "4"; info.location = 3; }
            case '\uE01F' -> { info.key = "5"; info.code = "Numpad5"; info.windowsVirtualKeyCode = 101; info.text = "5"; info.location = 3; }
            case '\uE020' -> { info.key = "6"; info.code = "Numpad6"; info.windowsVirtualKeyCode = 102; info.text = "6"; info.location = 3; }
            case '\uE021' -> { info.key = "7"; info.code = "Numpad7"; info.windowsVirtualKeyCode = 103; info.text = "7"; info.location = 3; }
            case '\uE022' -> { info.key = "8"; info.code = "Numpad8"; info.windowsVirtualKeyCode = 104; info.text = "8"; info.location = 3; }
            case '\uE023' -> { info.key = "9"; info.code = "Numpad9"; info.windowsVirtualKeyCode = 105; info.text = "9"; info.location = 3; }
            case '\uE024' -> { info.key = "*"; info.code = "NumpadMultiply"; info.windowsVirtualKeyCode = 106; info.text = "*"; info.location = 3; }
            case '\uE025' -> { info.key = "+"; info.code = "NumpadAdd"; info.windowsVirtualKeyCode = 107; info.text = "+"; info.location = 3; }
            case '\uE026' -> { info.key = ","; info.code = "NumpadComma"; info.windowsVirtualKeyCode = 108; info.text = ","; info.location = 3; }
            case '\uE027' -> { info.key = "-"; info.code = "NumpadSubtract"; info.windowsVirtualKeyCode = 109; info.text = "-"; info.location = 3; }
            case '\uE028' -> { info.key = "."; info.code = "NumpadDecimal"; info.windowsVirtualKeyCode = 110; info.text = "."; info.location = 3; }
            case '\uE029' -> { info.key = "/"; info.code = "NumpadDivide"; info.windowsVirtualKeyCode = 111; info.text = "/"; info.location = 3; }
            // Function keys
            case '\uE031' -> { info.key = "F1"; info.code = "F1"; info.windowsVirtualKeyCode = 112; }
            case '\uE032' -> { info.key = "F2"; info.code = "F2"; info.windowsVirtualKeyCode = 113; }
            case '\uE033' -> { info.key = "F3"; info.code = "F3"; info.windowsVirtualKeyCode = 114; }
            case '\uE034' -> { info.key = "F4"; info.code = "F4"; info.windowsVirtualKeyCode = 115; }
            case '\uE035' -> { info.key = "F5"; info.code = "F5"; info.windowsVirtualKeyCode = 116; }
            case '\uE036' -> { info.key = "F6"; info.code = "F6"; info.windowsVirtualKeyCode = 117; }
            case '\uE037' -> { info.key = "F7"; info.code = "F7"; info.windowsVirtualKeyCode = 118; }
            case '\uE038' -> { info.key = "F8"; info.code = "F8"; info.windowsVirtualKeyCode = 119; }
            case '\uE039' -> { info.key = "F9"; info.code = "F9"; info.windowsVirtualKeyCode = 120; }
            case '\uE03A' -> { info.key = "F10"; info.code = "F10"; info.windowsVirtualKeyCode = 121; }
            case '\uE03B' -> { info.key = "F11"; info.code = "F11"; info.windowsVirtualKeyCode = 122; }
            case '\uE03C' -> { info.key = "F12"; info.code = "F12"; info.windowsVirtualKeyCode = 123; }
            case '\uE03D' -> { info.key = "Meta"; info.code = "MetaLeft"; info.windowsVirtualKeyCode = 91; info.location = 1; }
            default -> { info.key = ""; info.code = ""; }
        }

        return info;
    }

    private static class KeyInfo {
        String key;
        String code;
        String text;
        int windowsVirtualKeyCode;
        int nativeVirtualKeyCode;
        int location; // 0=standard, 1=left, 2=right, 3=numpad
    }

}
