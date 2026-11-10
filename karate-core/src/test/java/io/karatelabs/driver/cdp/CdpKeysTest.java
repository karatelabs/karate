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
package io.karatelabs.driver.cdp;

import io.karatelabs.driver.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CdpKeysTest {

    private final CdpClient client = CdpClient.detached(Duration.ofSeconds(5));
    private final List<CdpMessage> sent = new ArrayList<>();
    private final CdpKeys keys = new CdpKeys(client, sent::add);

    @AfterEach
    void close() {
        client.close();
    }

    private List<Map<String, Object>> keyEvents() {
        return sent.stream().filter(m -> m.getMethod().equals("Input.dispatchKeyEvent")).map(CdpMessage::getParams).toList();
    }

    private void assertOneKey(String key, String code, int virtualKey) {
        List<Map<String, Object>> events = keyEvents();
        assertEquals(List.of("rawKeyDown", "keyUp"), events.stream().map(e -> e.get("type")).toList(), sent.toString());
        for (Map<String, Object> e : events) {
            assertEquals(key, e.get("key"));
            assertEquals(code, e.get("code"));
            assertEquals(virtualKey, e.get("windowsVirtualKeyCode"));
        }
        assertTrue(sent.stream().noneMatch(m -> m.getMethod().equals("Input.insertText")), sent.toString());
    }

    @Test
    void aBareFunctionKeyNameIsPressedNotTyped() {
        keys.press("F9");
        assertOneKey("F9", "F9", 120);
    }

    @Test
    void functionKeysRunToF24() {
        keys.press("F24");
        assertOneKey("F24", "F24", 135);
    }

    @Test
    void theConstantAndTheNameAreOneKey() {
        keys.press(Keys.F9);
        assertOneKey("F9", "F9", 120);
    }

    @Test
    void namedKeysWithoutAConstant() {
        keys.press("PrintScreen");
        assertOneKey("PrintScreen", "PrintScreen", 44);
        sent.clear();
        keys.press("ContextMenu");
        assertOneKey("ContextMenu", "ContextMenu", 93);
        sent.clear();
        keys.press("CapsLock");
        assertOneKey("CapsLock", "CapsLock", 20);
    }

    @Test
    void aChordHoldsItsModifiersAroundAFunctionKey() {
        keys.press("Shift+F13");
        List<Map<String, Object>> events = keyEvents();
        assertEquals(4, events.size(), sent.toString());
        assertEquals("Shift", events.get(0).get("key"));
        assertEquals("F13", events.get(1).get("key"));
        assertEquals(124, events.get(1).get("windowsVirtualKeyCode"));
        assertEquals(8, events.get(1).get("modifiers"));
        assertEquals("Shift", events.get(3).get("key"));
        assertEquals(0, events.get(3).get("modifiers"));
    }

    @Test
    void aNumpadDigitTypesItsDigit() {
        keys.press("Numpad7");
        List<Map<String, Object>> events = keyEvents();
        assertEquals(List.of("rawKeyDown", "char", "keyUp"), events.stream().map(e -> e.get("type")).toList());
        assertEquals("Numpad7", events.get(0).get("code"));
        assertEquals(103, events.get(0).get("windowsVirtualKeyCode"));
        assertEquals(3, events.get(0).get("location"));
        assertEquals("7", events.get(1).get("text"));
    }

    @Test
    void textThatNamesNoKeyIsTyped() {
        keys.press("Fx");
        assertEquals(2, sent.stream().filter(m -> m.getMethod().equals("Input.insertText")).count(), sent.toString());
    }

    @Test
    void aShiftedCharacterIsShiftAndItsBaseKeyWithItsText() {
        keys.type("|%{\"$_");
        List<Map<String, Object>> downs = keyEvents().stream().filter(e -> e.get("type").equals("rawKeyDown")).toList();
        assertEquals(List.of("|", "%", "{", "\"", "$", "_"), downs.stream().map(e -> e.get("key")).toList());
        assertEquals(List.of("Backslash", "Digit5", "BracketLeft", "Quote", "Digit4", "Minus"), downs.stream().map(e -> e.get("code")).toList());
        assertEquals(List.of(220, 53, 219, 222, 52, 189), downs.stream().map(e -> e.get("windowsVirtualKeyCode")).toList());
        // a terminal takes a key from its keydown only with a key code; the modifiers say Shift, as a keyboard's would
        assertTrue(keyEvents().stream().allMatch(e -> e.get("modifiers").equals(8)), keyEvents().toString());
        assertEquals(List.of("|", "%", "{", "\"", "$", "_"), sent.stream().filter(m -> m.getMethod().equals("Input.insertText"))
                .map(m -> m.getParams().get("text")).toList());
    }

    @Test
    void anUnshiftedCharacterHoldsNoModifierAfterAShiftedOne() {
        keys.type("|a");
        List<Map<String, Object>> events = keyEvents();
        assertEquals(List.of(8, 8, 0, 0), events.stream().map(e -> e.get("modifiers")).toList(), events.toString());
        assertEquals(65, events.get(2).get("windowsVirtualKeyCode"));
    }

}
