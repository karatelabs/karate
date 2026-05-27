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

import io.karatelabs.driver.Mouse;
import java.util.List;
import java.util.Map;

/**
 * W3C-based implementation of mouse actions.
 * Uses the W3C Actions API (POST /session/{id}/actions).
 */
public class W3cMouse implements Mouse {

    private final W3cSession session;
    private double x;
    private double y;

    public W3cMouse(W3cSession session) {
        this.session = session;
        this.x = 0;
        this.y = 0;
    }

    public W3cMouse(W3cSession session, double x, double y) {
        this.session = session;
        move(x, y);
    }

    @Override
    public Mouse move(double x, double y) {
        this.x = x;
        this.y = y;
        dispatchPointerAction(Map.of(
                "type", "pointerMove",
                "duration", 100, // Small duration prevents instantaneous teleportation issues
                "x", (int) x,
                "y", (int) y,
                "origin", "viewport"
        ));
        return this;
    }

    @Override
    public Mouse offset(double dx, double dy) {
        this.x += dx;
        this.y += dy;
        // W3C has native support for relative movement via origin: "pointer"
        dispatchPointerAction(Map.of(
                "type", "pointerMove",
                "duration", 100,
                "x", (int) dx,
                "y", (int) dy,
                "origin", "pointer"
        ));
        return this;
    }

    @Override
    public Mouse click() {
        dispatchPointerActions(List.of(
                Map.of("type", "pointerDown", "button", 0), // 0 = Left Click
                Map.of("type", "pointerUp", "button", 0)
        ));
        return this;
    }

    @Override
    public Mouse doubleClick() {
        dispatchPointerActions(List.of(
                Map.of("type", "pointerDown", "button", 0),
                Map.of("type", "pointerUp", "button", 0),
                Map.of("type", "pointerDown", "button", 0),
                Map.of("type", "pointerUp", "button", 0)
        ));
        return this;
    }

    @Override
    public Mouse rightClick() {
        dispatchPointerActions(List.of(
                Map.of("type", "pointerDown", "button", 2), // 2 = Right Click in W3C
                Map.of("type", "pointerUp", "button", 2)
        ));
        return this;
    }

    @Override
    public Mouse down() {
        dispatchPointerAction(Map.of("type", "pointerDown", "button", 0));
        return this;
    }

    @Override
    public Mouse up() {
        dispatchPointerAction(Map.of("type", "pointerUp", "button", 0));
        return this;
    }

    @Override
    public Mouse wheel(double deltaX, double deltaY) {
        // Scrolling uses a separate input source type: "wheel"
        Map<String, Object> wheelInput = Map.of(
                "type", "wheel",
                "id", "wheel",
                "actions", List.of(Map.of(
                        "type", "scroll",
                        "x", (int) this.x,
                        "y", (int) this.y,
                        "deltaX", (int) deltaX,
                        "deltaY", (int) deltaY,
                        "duration", 100
                ))
        );
        session.performActions(List.of(wheelInput));
        return this;
    }

    @Override
    public Mouse scrollDown(double amount) {
        return wheel(0, amount);
    }

    @Override
    public Mouse scrollUp(double amount) {
        return wheel(0, -amount);
    }

    @Override
    public Mouse scrollRight(double amount) {
        return wheel(amount, 0);
    }

    @Override
    public Mouse scrollLeft(double amount) {
        return wheel(-amount, 0);
    }

    @Override
    public Mouse dragTo(double targetX, double targetY) {
        // W3C handles complex dragging elegantly by batching them in one request
        dispatchPointerActions(List.of(
                Map.of("type", "pointerDown", "button", 0),
                Map.of("type", "pointerMove", "duration", 500, "x", (int) targetX, "y", (int) targetY, "origin", "viewport"),
                Map.of("type", "pointerUp", "button", 0)
        ));
        this.x = targetX;
        this.y = targetY;
        return this;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    private void dispatchPointerAction(Map<String, Object> action) {
        dispatchPointerActions(List.of(action));
    }

    private void dispatchPointerActions(List<Map<String, Object>> actions) {
        Map<String, Object> pointerInput = Map.of(
                "type", "pointer",
                "id", "mouse", // ID maps the continuous state of the device across requests
                "parameters", Map.of("pointerType", "mouse"),
                "actions", actions
        );
        session.performActions(List.of(pointerInput));
    }
}
