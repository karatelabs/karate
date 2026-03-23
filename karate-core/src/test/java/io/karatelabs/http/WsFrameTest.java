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
package io.karatelabs.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WsFrameTest {

    @Test
    void testTextFrame() {
        WsFrame frame = WsFrame.text("hello");
        assertTrue(frame.isText());
        assertFalse(frame.isBinary());
        assertFalse(frame.isPing());
        assertFalse(frame.isPong());
        assertFalse(frame.isClose());
        assertEquals("hello", frame.getText());
        assertNull(frame.getBytes());
        assertEquals(WsFrame.Type.TEXT, frame.getType());
    }

    @Test
    void testBinaryFrame() {
        byte[] data = new byte[]{1, 2, 3};
        WsFrame frame = WsFrame.binary(data);
        assertTrue(frame.isBinary());
        assertFalse(frame.isText());
        assertNull(frame.getText());
        assertArrayEquals(data, frame.getBytes());
        assertEquals(WsFrame.Type.BINARY, frame.getType());
    }

    @Test
    void testBinaryFrameDefensiveCopy() {
        byte[] data = new byte[]{1, 2, 3};
        WsFrame frame = WsFrame.binary(data);
        data[0] = 99; // modify original
        byte[] retrieved = frame.getBytes();
        assertEquals(1, retrieved[0]); // frame should have original value
        retrieved[0] = 88; // modify retrieved copy
        assertEquals(1, frame.getBytes()[0]); // frame should still have original
    }

    @Test
    void testPingFrame() {
        WsFrame frame = WsFrame.ping();
        assertTrue(frame.isPing());
        assertFalse(frame.isText());
        assertEquals(WsFrame.Type.PING, frame.getType());
    }

    @Test
    void testPongFrame() {
        WsFrame frame = WsFrame.pong();
        assertTrue(frame.isPong());
        assertFalse(frame.isText());
        assertEquals(WsFrame.Type.PONG, frame.getType());
    }

    @Test
    void testCloseFrameDefault() {
        WsFrame frame = WsFrame.close();
        assertTrue(frame.isClose());
        assertEquals(1000, frame.getCloseCode());
        assertNull(frame.getCloseReason());
        assertEquals(WsFrame.Type.CLOSE, frame.getType());
    }

    @Test
    void testCloseFrameWithCodeAndReason() {
        WsFrame frame = WsFrame.close(1001, "going away");
        assertTrue(frame.isClose());
        assertEquals(1001, frame.getCloseCode());
        assertEquals("going away", frame.getCloseReason());
    }

    @Test
    void testToString() {
        assertEquals("WsFrame[TEXT: hello]", WsFrame.text("hello").toString());
        assertEquals("WsFrame[BINARY: 3 bytes]", WsFrame.binary(new byte[3]).toString());
        assertEquals("WsFrame[PING]", WsFrame.ping().toString());
        assertEquals("WsFrame[PONG]", WsFrame.pong().toString());
        assertEquals("WsFrame[CLOSE: 1000]", WsFrame.close().toString());
        assertEquals("WsFrame[CLOSE: 1001 bye]", WsFrame.close(1001, "bye").toString());
    }

    @Test
    void testToStringTruncatesLongText() {
        String longText = "a".repeat(150);
        String result = WsFrame.text(longText).toString();
        assertTrue(result.contains("..."));
        assertTrue(result.length() < 150);
    }

}
