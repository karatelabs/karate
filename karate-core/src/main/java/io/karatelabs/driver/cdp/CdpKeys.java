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

import java.util.function.Consumer;

/**
 * CDP-based implementation of keyboard actions.
 * Uses CDP Input.dispatchKeyEvent for low-level keyboard control.
 */
public class CdpKeys implements Keys {

    private final CdpClient cdp;
    private final Consumer<CdpMessage> sender;
    private int modifiers = 0;

    // Modifier flags
    private static final int ALT_FLAG = 1;
    private static final int CTRL_FLAG = 2;
    private static final int META_FLAG = 4;
    private static final int SHIFT_FLAG = 8;

    public CdpKeys(CdpClient cdp) {
        this(cdp, CdpMessage::send);
    }

    CdpKeys(CdpClient cdp, Consumer<CdpMessage> sender) {
        this.cdp = cdp;
        this.sender = sender;
    }

    @Override
    public Keys type(String text) {
        for (char c : text.toCharArray()) {
            String s = String.valueOf(c);
            if (isSpecialKey(s)) {
                KeyInfo info = keyInfo(s);
                if (info != null) {
                    pressKeyInfo(info);
                }
            } else {
                typeChar(c);
            }
        }
        return this;
    }

    /**
     * Press a WebDriver key constant, a key name ("Enter", "F9", "Numpad0", "PrintScreen"), a character,
     * or a "+" chord like Playwright's ("Control+a", "Shift+F5", "Control+Shift+ArrowLeft"). Text that is
     * none of these is typed.
     */
    @Override
    public Keys press(String key) {
        if (key.length() > 1 && key.contains("+")) {
            return pressPlusNotation(key);
        }
        KeyInfo info = keyInfo(key);
        if (info != null) {
            pressKeyInfo(info);
        } else if (!isSpecialKey(key)) {
            for (char c : key.toCharArray()) {
                typeChar(c);
            }
        }
        return this;
    }

    /**
     * All parts but the last are held as modifiers while the last is pressed.
     */
    private Keys pressPlusNotation(String combo) {
        String[] parts = combo.split("\\+");
        if (parts.length < 2) {
            return press(parts[0]);
        }
        String[] modifierKeys = new String[parts.length - 1];
        System.arraycopy(parts, 0, modifierKeys, 0, modifierKeys.length);
        return combo(modifierKeys, parts[parts.length - 1]);
    }

    @Override
    public Keys down(String key) {
        KeyInfo info = keyInfoOrChar(key);
        modifiers |= modifierFlag(info.key);
        // Send keyDown for any key, not just modifiers (matches Puppeteer)
        dispatchRawKeyDown(info);
        return this;
    }

    @Override
    public Keys up(String key) {
        KeyInfo info = keyInfoOrChar(key);
        modifiers &= ~modifierFlag(info.key);
        // Send keyUp for any key, not just modifiers (matches Puppeteer)
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
        // A space goes as a keyboard's char event, its keypress before the text: a terminal (xterm.js) takes a key with no
        // printable key code from its keypress, and ignores inserted text once it has seen the keydown.
        if ((modifiers & ~SHIFT_FLAG) == 0 && effectiveChar == ' ') {
            dispatchChar(info);
        } else if ((modifiers & ~SHIFT_FLAG) == 0) {
            sender.accept(cdp.method("Input.insertText")
                    .param("text", String.valueOf(effectiveChar)));
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

    private void pressKeyInfo(KeyInfo info) {
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

        sender.accept(message);
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

        sender.accept(message);
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
        sender.accept(message);
    }

    private boolean isSpecialKey(String key) {
        if (key == null || key.isEmpty()) return false;
        char c = key.charAt(0);
        return c >= '\uE000' && c <= '\uE03D';
    }

    private static int modifierFlag(String key) {
        if (key == null) return 0;
        return switch (key) {
            case "Shift" -> SHIFT_FLAG;
            case "Control" -> CTRL_FLAG;
            case "Alt" -> ALT_FLAG;
            case "Meta" -> META_FLAG;
            default -> 0;
        };
    }

    private KeyInfo keyInfoOrChar(String key) {
        KeyInfo info = keyInfo(key);
        return info != null ? info : getKeyInfoForChar(key.charAt(0));
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

    /**
     * The key a WebDriver constant or a key name stands for, or null when it is neither.
     */
    private static KeyInfo keyInfo(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return named(key.length() == 1 ? constantName(key.charAt(0)) : key);
    }

    private static String constantName(char c) {
        if (c >= '\uE01A' && c <= '\uE023') return "Numpad" + (c - '\uE01A');
        if (c >= '\uE031' && c <= '\uE03C') return "F" + (c - '\uE030');
        return switch (c) {
            case '\uE003' -> "Backspace";
            case '\uE004' -> "Tab";
            case '\uE006', '\uE007' -> "Enter";
            case '\uE008' -> "Shift";
            case '\uE009' -> "Control";
            case '\uE00A' -> "Alt";
            case '\uE00B' -> "Pause";
            case '\uE00C' -> "Escape";
            case '\uE00D' -> "Space";
            case '\uE00E' -> "PageUp";
            case '\uE00F' -> "PageDown";
            case '\uE010' -> "End";
            case '\uE011' -> "Home";
            case '\uE012' -> "ArrowLeft";
            case '\uE013' -> "ArrowUp";
            case '\uE014' -> "ArrowRight";
            case '\uE015' -> "ArrowDown";
            case '\uE016' -> "Insert";
            case '\uE017' -> "Delete";
            case '\uE024' -> "NumpadMultiply";
            case '\uE025' -> "NumpadAdd";
            case '\uE026' -> "NumpadComma";
            case '\uE027' -> "NumpadSubtract";
            case '\uE028' -> "NumpadDecimal";
            case '\uE029' -> "NumpadDivide";
            case '\uE03D' -> "Meta";
            default -> null;
        };
    }

    /**
     * DOM key names (with Playwright's aliases), their codes and Windows virtual key codes.
     */
    private static KeyInfo named(String name) {
        if (name == null) {
            return null;
        }
        if (name.matches("F([1-9]|1[0-9]|2[0-4])")) {
            return key(name, name, 111 + Integer.parseInt(name.substring(1)));
        }
        if (name.matches("Numpad[0-9]")) {
            String digit = name.substring(6);
            return numpad(digit, name, 96 + Integer.parseInt(digit));
        }
        return switch (name) {
            case "Backspace" -> key("Backspace", "Backspace", 8);
            case "Tab" -> key("Tab", "Tab", 9);
            case "Enter", "Return" -> withText(key("Enter", "Enter", 13), "\r");
            case "Shift" -> left(key("Shift", "ShiftLeft", 16));
            case "Control", "Ctrl" -> left(key("Control", "ControlLeft", 17));
            case "Alt" -> left(key("Alt", "AltLeft", 18));
            case "Meta", "Command", "Cmd" -> left(key("Meta", "MetaLeft", 91));
            case "Pause" -> key("Pause", "Pause", 19);
            case "CapsLock" -> key("CapsLock", "CapsLock", 20);
            case "Escape", "Esc" -> key("Escape", "Escape", 27);
            case "Space" -> withText(key(" ", "Space", 32), " ");
            case "PageUp" -> key("PageUp", "PageUp", 33);
            case "PageDown" -> key("PageDown", "PageDown", 34);
            case "End" -> key("End", "End", 35);
            case "Home" -> key("Home", "Home", 36);
            case "ArrowLeft", "Left" -> key("ArrowLeft", "ArrowLeft", 37);
            case "ArrowUp", "Up" -> key("ArrowUp", "ArrowUp", 38);
            case "ArrowRight", "Right" -> key("ArrowRight", "ArrowRight", 39);
            case "ArrowDown", "Down" -> key("ArrowDown", "ArrowDown", 40);
            case "PrintScreen" -> key("PrintScreen", "PrintScreen", 44);
            case "Insert" -> key("Insert", "Insert", 45);
            case "Delete" -> key("Delete", "Delete", 46);
            case "ContextMenu" -> key("ContextMenu", "ContextMenu", 93);
            case "NumpadMultiply" -> numpad("*", name, 106);
            case "NumpadAdd" -> numpad("+", name, 107);
            case "NumpadComma" -> numpad(",", name, 108);
            case "NumpadSubtract" -> numpad("-", name, 109);
            case "NumpadDecimal" -> numpad(".", name, 110);
            case "NumpadDivide" -> numpad("/", name, 111);
            case "NumpadEnter" -> withText(numpad("Enter", name, 13), "\r");
            case "NumLock" -> key("NumLock", "NumLock", 144);
            case "ScrollLock" -> key("ScrollLock", "ScrollLock", 145);
            default -> null;
        };
    }

    private static KeyInfo key(String key, String code, int windowsVirtualKeyCode) {
        KeyInfo info = new KeyInfo();
        info.key = key;
        info.code = code;
        info.text = "";
        info.windowsVirtualKeyCode = windowsVirtualKeyCode;
        return info;
    }

    private static KeyInfo numpad(String key, String code, int windowsVirtualKeyCode) {
        KeyInfo info = withText(key(key, code, windowsVirtualKeyCode), key.length() == 1 ? key : "");
        info.location = 3;
        return info;
    }

    private static KeyInfo withText(KeyInfo info, String text) {
        info.text = text;
        return info;
    }

    private static KeyInfo left(KeyInfo info) {
        info.location = 1;
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
