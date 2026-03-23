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
 * Mouse actions interface for browser automation.
 * Coordinates are viewport-relative.
 * <p>
 * Phase 8: Extracted from CdpMouse to enable multi-backend support.
 */
public interface Mouse {

    /**
     * Move mouse to specified coordinates.
     *
     * @param x the x coordinate (viewport-relative)
     * @param y the y coordinate (viewport-relative)
     * @return this for chaining
     */
    Mouse move(double x, double y);

    /**
     * Move mouse by offset from current position.
     *
     * @param dx the x offset
     * @param dy the y offset
     * @return this for chaining
     */
    Mouse offset(double dx, double dy);

    /**
     * Click at the current position.
     *
     * @return this for chaining
     */
    Mouse click();

    /**
     * Double-click at the current position.
     *
     * @return this for chaining
     */
    Mouse doubleClick();

    /**
     * Right-click at the current position.
     *
     * @return this for chaining
     */
    Mouse rightClick();

    /**
     * Press the mouse button down at the current position.
     *
     * @return this for chaining
     */
    Mouse down();

    /**
     * Release the mouse button at the current position.
     *
     * @return this for chaining
     */
    Mouse up();

    /**
     * Perform a mouse wheel scroll.
     *
     * @param deltaX horizontal scroll amount (positive = right)
     * @param deltaY vertical scroll amount (positive = down)
     * @return this for chaining
     */
    Mouse wheel(double deltaX, double deltaY);

    /**
     * Scroll down at the current position.
     *
     * @param amount the scroll amount in pixels
     * @return this for chaining
     */
    Mouse scrollDown(double amount);

    /**
     * Scroll up at the current position.
     *
     * @param amount the scroll amount in pixels
     * @return this for chaining
     */
    Mouse scrollUp(double amount);

    /**
     * Scroll right at the current position.
     *
     * @param amount the scroll amount in pixels
     * @return this for chaining
     */
    Mouse scrollRight(double amount);

    /**
     * Scroll left at the current position.
     *
     * @param amount the scroll amount in pixels
     * @return this for chaining
     */
    Mouse scrollLeft(double amount);

    /**
     * Drag from current position to target position.
     *
     * @param targetX the target x coordinate
     * @param targetY the target y coordinate
     * @return this for chaining
     */
    Mouse dragTo(double targetX, double targetY);

    /**
     * Get current x coordinate.
     */
    double getX();

    /**
     * Get current y coordinate.
     */
    double getY();

}
