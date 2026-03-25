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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * W3C WebDriver Actions API keyboard input implementation.
 *
 * <p>Uses the W3C Actions API (POST /session/{id}/actions) which properly supports
 * modifier keys (Ctrl, Shift, Alt, Meta) and key combinations. This is the correct
 * W3C approach — v1 also used the actions endpoint for WebDriver.</p>
 *
 * <p>For simple typing, uses sendKeys on the active element (more efficient).
 * For modifier combos and special keys, uses the Actions API.</p>
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
        // For simple text, sendKeys on active element is more efficient
        String activeElementId = findActiveElement();
        session.sendKeys(activeElementId, text);
        return this;
    }

    @Override
    public Keys press(String key) {
        // Support plus-notation: "Control+a", "Shift+ArrowLeft", "Control+Shift+ArrowLeft"
        if (key.contains("+")) {
            String[] parts = key.split("\\+");
            String[] modifiers = new String[parts.length - 1];
            for (int i = 0; i < modifiers.length; i++) {
                modifiers[i] = resolveKeyName(parts[i]);
            }
            String mainKey = resolveKeyName(parts[parts.length - 1]);
            return combo(modifiers, mainKey);
        }
        String resolved = resolveKeyName(key);
        performKeyActions(List.of(
                keyDown(resolved),
                keyUp(resolved)
        ));
        return this;
    }

    /**
     * Resolve human-readable key names to W3C key codepoints.
     * Maps "Control" → Keys.CONTROL, "Shift" → Keys.SHIFT, "ArrowLeft" → Keys.LEFT, etc.
     */
    private static String resolveKeyName(String name) {
        return switch (name) {
            case "Control" -> CONTROL;
            case "Shift" -> SHIFT;
            case "Alt" -> ALT;
            case "Meta", "Command" -> META;
            case "Enter" -> ENTER;
            case "Tab" -> TAB;
            case "Backspace" -> BACKSPACE;
            case "Delete" -> DELETE;
            case "Escape" -> ESCAPE;
            case "ArrowUp" -> UP;
            case "ArrowDown" -> DOWN;
            case "ArrowLeft" -> LEFT;
            case "ArrowRight" -> RIGHT;
            case "Home" -> HOME;
            case "End" -> END;
            case "PageUp" -> PAGE_UP;
            case "PageDown" -> PAGE_DOWN;
            case "Insert" -> INSERT;
            case "Space" -> SPACE;
            default -> name; // Single char or already a codepoint
        };
    }

    @Override
    public Keys down(String key) {
        performKeyActions(List.of(keyDown(key)));
        return this;
    }

    @Override
    public Keys up(String key) {
        performKeyActions(List.of(keyUp(key)));
        return this;
    }

    @Override
    public Keys combo(String[] modifierKeys, String key) {
        List<Map<String, Object>> actions = new ArrayList<>();
        // Press all modifiers
        for (String mod : modifierKeys) {
            actions.add(keyDown(mod));
        }
        // Press and release the key
        actions.add(keyDown(key));
        actions.add(keyUp(key));
        // Release modifiers in reverse order
        for (int i = modifierKeys.length - 1; i >= 0; i--) {
            actions.add(keyUp(modifierKeys[i]));
        }
        performKeyActions(actions);
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

    /**
     * Execute key actions via the W3C Actions API.
     * POST /session/{id}/actions with keyboard action sequence.
     */
    private void performKeyActions(List<Map<String, Object>> keyActions) {
        Map<String, Object> keyboard = new HashMap<>();
        keyboard.put("type", "key");
        keyboard.put("id", "keyboard");
        keyboard.put("actions", keyActions);
        session.performActions(List.of(keyboard));
    }

    private static Map<String, Object> keyDown(String value) {
        return Map.of("type", "keyDown", "value", value);
    }

    private static Map<String, Object> keyUp(String value) {
        return Map.of("type", "keyUp", "value", value);
    }

    private String findActiveElement() {
        Object result = session.executeScript("return document.activeElement");
        if (W3cSession.isElementReference(result)) {
            return W3cSession.elementIdFrom(result);
        }
        throw new RuntimeException("Could not find active element for keyboard input");
    }

}
