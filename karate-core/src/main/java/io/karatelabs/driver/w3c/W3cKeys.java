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
package io.karatelabs.driver.w3c;

import io.karatelabs.driver.Keys;

/**
 * W3C WebDriver keyboard input implementation.
 * Uses sendKeys on the active element for typing, and executeScript
 * for special key events.
 */
public class W3cKeys implements Keys {

    private final W3cSession session;

    W3cKeys(W3cSession session) {
        this.session = session;
    }

    @Override
    public Keys type(String text) {
        if (text == null || text.isEmpty()) {
            return this;
        }
        String activeElementId = findActiveElement();
        session.sendKeys(activeElementId, text);
        return this;
    }

    @Override
    public Keys press(String key) {
        String activeElementId = findActiveElement();
        session.sendKeys(activeElementId, key);
        return this;
    }

    @Override
    public Keys down(String key) {
        // W3C sendKeys doesn't support hold-down directly
        // Use executeScript to dispatch keydown event
        String js = "document.activeElement.dispatchEvent(new KeyboardEvent('keydown', "
                + "{key: '" + keyName(key) + "', bubbles: true}))";
        session.executeScript(js);
        return this;
    }

    @Override
    public Keys up(String key) {
        String js = "document.activeElement.dispatchEvent(new KeyboardEvent('keyup', "
                + "{key: '" + keyName(key) + "', bubbles: true}))";
        session.executeScript(js);
        return this;
    }

    @Override
    public Keys combo(String[] modifierKeys, String key) {
        // Hold modifiers, press key, release modifiers
        for (String mod : modifierKeys) {
            down(mod);
        }
        press(key);
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

    private String findActiveElement() {
        Object result = session.executeScript("return document.activeElement");
        if (W3cSession.isElementReference(result)) {
            return W3cSession.elementIdFrom(result);
        }
        throw new RuntimeException("Could not find active element for keyboard input");
    }

    /**
     * Convert W3C key codepoint to JS key name for dispatchEvent.
     */
    private static String keyName(String key) {
        if (key == null || key.isEmpty()) return "";
        // Map W3C codepoints to JS key names
        return switch (key) {
            case SHIFT -> "Shift";
            case CONTROL -> "Control";
            case ALT -> "Alt";
            case META -> "Meta";
            case ENTER -> "Enter";
            case TAB -> "Tab";
            case BACKSPACE -> "Backspace";
            case DELETE -> "Delete";
            case ESCAPE -> "Escape";
            case UP -> "ArrowUp";
            case DOWN -> "ArrowDown";
            case LEFT -> "ArrowLeft";
            case RIGHT -> "ArrowRight";
            case SPACE -> " ";
            default -> key;
        };
    }

}
