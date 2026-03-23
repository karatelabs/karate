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

/**
 * Immutable wrapper for WebSocket frames (text, binary, control frames).
 */
public class WsFrame {

    public enum Type {
        TEXT, BINARY, PING, PONG, CLOSE
    }

    private final Type type;
    private final String text;
    private final byte[] bytes;
    private final int closeCode;
    private final String closeReason;

    private WsFrame(Type type, String text, byte[] bytes, int closeCode, String closeReason) {
        this.type = type;
        this.text = text;
        this.bytes = bytes;
        this.closeCode = closeCode;
        this.closeReason = closeReason;
    }

    // Factory methods

    public static WsFrame text(String content) {
        return new WsFrame(Type.TEXT, content, null, 0, null);
    }

    public static WsFrame binary(byte[] content) {
        return new WsFrame(Type.BINARY, null, content != null ? content.clone() : null, 0, null);
    }

    public static WsFrame ping() {
        return new WsFrame(Type.PING, null, null, 0, null);
    }

    public static WsFrame pong() {
        return new WsFrame(Type.PONG, null, null, 0, null);
    }

    public static WsFrame close() {
        return new WsFrame(Type.CLOSE, null, null, 1000, null);
    }

    public static WsFrame close(int code, String reason) {
        return new WsFrame(Type.CLOSE, null, null, code, reason);
    }

    // Type checks

    public Type getType() {
        return type;
    }

    public boolean isText() {
        return type == Type.TEXT;
    }

    public boolean isBinary() {
        return type == Type.BINARY;
    }

    public boolean isPing() {
        return type == Type.PING;
    }

    public boolean isPong() {
        return type == Type.PONG;
    }

    public boolean isClose() {
        return type == Type.CLOSE;
    }

    // Content access

    public String getText() {
        return text;
    }

    public byte[] getBytes() {
        return bytes != null ? bytes.clone() : null;
    }

    public int getCloseCode() {
        return closeCode;
    }

    public String getCloseReason() {
        return closeReason;
    }

    @Override
    public String toString() {
        return switch (type) {
            case TEXT -> "WsFrame[TEXT: " + truncate(text, 100) + "]";
            case BINARY -> "WsFrame[BINARY: " + (bytes != null ? bytes.length : 0) + " bytes]";
            case PING -> "WsFrame[PING]";
            case PONG -> "WsFrame[PONG]";
            case CLOSE -> "WsFrame[CLOSE: " + closeCode + (closeReason != null ? " " + closeReason : "") + "]";
        };
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "null";
        }
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "...";
    }

}
