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
package io.karatelabs.driver;

/**
 * Keyboard actions interface for browser automation.
 * <p>
 * Phase 8: Extracted from CdpKeys to enable multi-backend support.
 */
public interface Keys {

    // Special key constants (WebDriver unicode values)
    String NULL = "\uE000";
    String CANCEL = "\uE001";
    String HELP = "\uE002";
    String BACKSPACE = "\uE003";
    String TAB = "\uE004";
    String CLEAR = "\uE005";
    String RETURN = "\uE006";
    String ENTER = "\uE007";
    String SHIFT = "\uE008";
    String CONTROL = "\uE009";
    String ALT = "\uE00A";
    String PAUSE = "\uE00B";
    String ESCAPE = "\uE00C";
    String SPACE = "\uE00D";
    String PAGE_UP = "\uE00E";
    String PAGE_DOWN = "\uE00F";
    String END = "\uE010";
    String HOME = "\uE011";
    String LEFT = "\uE012";
    String UP = "\uE013";
    String RIGHT = "\uE014";
    String DOWN = "\uE015";
    String INSERT = "\uE016";
    String DELETE = "\uE017";
    String SEMICOLON = "\uE018";
    String EQUALS = "\uE019";

    // Numpad keys
    String NUMPAD0 = "\uE01A";
    String NUMPAD1 = "\uE01B";
    String NUMPAD2 = "\uE01C";
    String NUMPAD3 = "\uE01D";
    String NUMPAD4 = "\uE01E";
    String NUMPAD5 = "\uE01F";
    String NUMPAD6 = "\uE020";
    String NUMPAD7 = "\uE021";
    String NUMPAD8 = "\uE022";
    String NUMPAD9 = "\uE023";
    String MULTIPLY = "\uE024";
    String ADD = "\uE025";
    String SEPARATOR = "\uE026";
    String SUBTRACT = "\uE027";
    String DECIMAL = "\uE028";
    String DIVIDE = "\uE029";

    // Function keys
    String F1 = "\uE031";
    String F2 = "\uE032";
    String F3 = "\uE033";
    String F4 = "\uE034";
    String F5 = "\uE035";
    String F6 = "\uE036";
    String F7 = "\uE037";
    String F8 = "\uE038";
    String F9 = "\uE039";
    String F10 = "\uE03A";
    String F11 = "\uE03B";
    String F12 = "\uE03C";

    // Meta/Command key
    String META = "\uE03D";
    String COMMAND = META;

    /**
     * Type a sequence of characters.
     *
     * @param text the text to type
     * @return this for chaining
     */
    Keys type(String text);

    /**
     * Press and release a key.
     *
     * @param key the key to press (can be a special key constant or single character)
     * @return this for chaining
     */
    Keys press(String key);

    /**
     * Hold down a modifier key.
     *
     * @param key the modifier key (SHIFT, CONTROL, ALT, META)
     * @return this for chaining
     */
    Keys down(String key);

    /**
     * Release a modifier key.
     *
     * @param key the modifier key (SHIFT, CONTROL, ALT, META)
     * @return this for chaining
     */
    Keys up(String key);

    /**
     * Press a key combination (e.g., Ctrl+A, Shift+Tab).
     *
     * @param modifierKeys the modifier keys to hold
     * @param key the key to press
     * @return this for chaining
     */
    Keys combo(String[] modifierKeys, String key);

    /**
     * Press Ctrl+key combination.
     */
    Keys ctrl(String key);

    /**
     * Press Alt+key combination.
     */
    Keys alt(String key);

    /**
     * Press Shift+key combination.
     */
    Keys shift(String key);

    /**
     * Press Meta/Command+key combination.
     */
    Keys meta(String key);

}
