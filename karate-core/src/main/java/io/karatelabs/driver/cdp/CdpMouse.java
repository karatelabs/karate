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

import io.karatelabs.driver.Mouse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * CDP-based implementation of mouse actions.
 * Uses CDP Input.dispatchMouseEvent for low-level mouse control.
 * Coordinates are viewport-relative.
 */
public class CdpMouse implements Mouse {

    private static final Logger logger = LoggerFactory.getLogger(CdpMouse.class);
    private final CdpClient cdp;
    private double x;
    private double y;

    public CdpMouse(CdpClient cdp) {
        this.cdp = cdp;
        this.x = 0;
        this.y = 0;
    }

    public CdpMouse(CdpClient cdp, double x, double y) {
        this.cdp = cdp;
        this.x = x;
        this.y = y;
    }

    @Override
    public Mouse move(double x, double y) {
        this.x = x;
        this.y = y;
        dispatchEvent("mouseMoved");
        return this;
    }

    @Override
    public Mouse offset(double dx, double dy) {
        return move(this.x + dx, this.y + dy);
    }

    @Override
    public Mouse click() {
        // First move to the position (like v1's batched actions)
        // This ensures the browser properly targets the element
        dispatchEvent("mouseMoved");
        // Small delays between events ensure Chrome processes them in order.
        // Without this, rapid-fire events can be dropped or processed incorrectly,
        // causing flaky tests where click events don't register (0 DOM events captured).
        sleep(5);
        down();
        sleep(5);
        up();
        return this;
    }

    @Override
    public Mouse doubleClick() {
        click();
        sleep(5);
        dispatchEvent("mousePressed", Map.of("clickCount", 2));
        sleep(5);
        dispatchEvent("mouseReleased", Map.of("clickCount", 2));
        return this;
    }

    @Override
    public Mouse rightClick() {
        dispatchEvent("mousePressed", Map.of("button", "right"));
        sleep(5);
        dispatchEvent("mouseReleased", Map.of("button", "right"));
        return this;
    }

    @Override
    public Mouse down() {
        dispatchEvent("mousePressed");
        return this;
    }

    @Override
    public Mouse up() {
        dispatchEvent("mouseReleased");
        return this;
    }

    @Override
    public Mouse wheel(double deltaX, double deltaY) {
        cdp.method("Input.dispatchMouseEvent")
                .param("type", "mouseWheel")
                .param("x", x)
                .param("y", y)
                .param("deltaX", deltaX)
                .param("deltaY", deltaY)
                .sendWithoutWaiting();
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
        down();
        sleep(5);
        move(targetX, targetY);
        sleep(5);
        up();
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

    private void dispatchEvent(String type) {
        dispatchEvent(type, Map.of());
    }

    private void dispatchEvent(String type, Map<String, Object> extra) {
        CdpMessage message = cdp.method("Input.dispatchMouseEvent")
                .param("type", type)
                .param("x", x)
                .param("y", y);

        // Only set button for press/release events (not for mouseMoved)
        if (type.equals("mousePressed")) {
            if (!extra.containsKey("button")) {
                message.param("button", "left");
            }
            if (!extra.containsKey("clickCount")) {
                message.param("clickCount", 1);
            }
            // buttons bitmask: 1 = left button pressed
            if (!extra.containsKey("buttons")) {
                message.param("buttons", 1);
            }
        } else if (type.equals("mouseReleased")) {
            if (!extra.containsKey("button")) {
                message.param("button", "left");
            }
            if (!extra.containsKey("clickCount")) {
                message.param("clickCount", 1);
            }
            // buttons bitmask: 0 = no buttons pressed after release
            if (!extra.containsKey("buttons")) {
                message.param("buttons", 0);
            }
        } else {
            // For mouseMoved, button should be "none"
            message.param("button", "none");
            message.param("buttons", 0);
        }

        // Add any extra parameters
        extra.forEach(message::param);

        // Use fire-and-forget for mouse events - they don't need responses
        // and blocking can cause issues when the click triggers a dialog
        // (the dialog blocks Chrome from sending the CDP response)
        message.sendWithoutWaiting();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
