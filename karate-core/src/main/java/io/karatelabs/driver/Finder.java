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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Positional element finder for relative locators.
 * Allows finding elements based on their position relative to a reference element.
 */
public class Finder {

    private final Driver driver;
    private final String referenceLocator;
    private final Position position;
    private final double tolerance;

    // Default tolerance in pixels for "near" matching
    private static final double DEFAULT_TOLERANCE = 50.0;

    public enum Position {
        RIGHT_OF,
        LEFT_OF,
        ABOVE,
        BELOW,
        NEAR
    }

    public Finder(Driver driver, String referenceLocator, Position position) {
        this(driver, referenceLocator, position, DEFAULT_TOLERANCE);
    }

    Finder(Driver driver, String referenceLocator, Position position, double tolerance) {
        this.driver = driver;
        this.referenceLocator = referenceLocator;
        this.position = position;
        this.tolerance = tolerance;
    }

    /**
     * Set the tolerance for "near" matching.
     *
     * @param pixels the tolerance in pixels
     * @return a new Finder with the specified tolerance
     */
    public Finder within(double pixels) {
        return new Finder(driver, referenceLocator, position, pixels);
    }

    /**
     * Find the first element matching the locator that satisfies the positional constraint.
     *
     * @param locator the locator for candidate elements
     * @return the first matching element
     * @throws DriverException if no matching element is found
     */
    public Element find(String locator) {
        List<Element> matches = findAll(locator);
        if (matches.isEmpty()) {
            throw new DriverException("no element found " + positionDescription() + " " + referenceLocator + " matching: " + locator);
        }
        return matches.get(0);
    }

    /**
     * Find all elements matching the locator that satisfy the positional constraint.
     *
     * @param locator the locator for candidate elements
     * @return list of matching elements, sorted by distance to reference
     */
    @SuppressWarnings("unchecked")
    public List<Element> findAll(String locator) {
        // Get reference element position
        Map<String, Object> refPos = driver.position(referenceLocator, true);
        double refX = ((Number) refPos.get("x")).doubleValue();
        double refY = ((Number) refPos.get("y")).doubleValue();
        double refWidth = ((Number) refPos.get("width")).doubleValue();
        double refHeight = ((Number) refPos.get("height")).doubleValue();

        // Reference center and edges
        double refCenterX = refX + refWidth / 2;
        double refCenterY = refY + refHeight / 2;
        double refRight = refX + refWidth;
        double refBottom = refY + refHeight;

        // Get all candidate elements
        List<Element> candidates = driver.locateAll(locator);
        List<ElementWithDistance> matches = new ArrayList<>();

        for (Element candidate : candidates) {
            try {
                Map<String, Object> pos = candidate.position(true);
                if (pos == null) continue;

                double x = ((Number) pos.get("x")).doubleValue();
                double y = ((Number) pos.get("y")).doubleValue();
                double width = ((Number) pos.get("width")).doubleValue();
                double height = ((Number) pos.get("height")).doubleValue();

                double centerX = x + width / 2;
                double centerY = y + height / 2;
                double right = x + width;
                double bottom = y + height;

                boolean match = false;
                double distance = 0;

                switch (position) {
                    case RIGHT_OF:
                        // Element should be to the right of reference
                        if (x >= refRight - tolerance) {
                            // Check vertical overlap or nearness
                            if (hasVerticalOverlap(refY, refBottom, y, bottom, tolerance)) {
                                match = true;
                                distance = x - refRight;
                            }
                        }
                        break;

                    case LEFT_OF:
                        // Element should be to the left of reference
                        if (right <= refX + tolerance) {
                            if (hasVerticalOverlap(refY, refBottom, y, bottom, tolerance)) {
                                match = true;
                                distance = refX - right;
                            }
                        }
                        break;

                    case ABOVE:
                        // Element should be above reference
                        if (bottom <= refY + tolerance) {
                            if (hasHorizontalOverlap(refX, refRight, x, right, tolerance)) {
                                match = true;
                                distance = refY - bottom;
                            }
                        }
                        break;

                    case BELOW:
                        // Element should be below reference
                        if (y >= refBottom - tolerance) {
                            if (hasHorizontalOverlap(refX, refRight, x, right, tolerance)) {
                                match = true;
                                distance = y - refBottom;
                            }
                        }
                        break;

                    case NEAR:
                        // Element should be within tolerance distance
                        double dx = centerX - refCenterX;
                        double dy = centerY - refCenterY;
                        distance = Math.sqrt(dx * dx + dy * dy);
                        if (distance <= tolerance) {
                            match = true;
                        }
                        break;
                }

                if (match) {
                    matches.add(new ElementWithDistance(candidate, distance));
                }
            } catch (Exception e) {
                // Element may have become stale, skip it
            }
        }

        // Sort by distance (closest first)
        matches.sort((a, b) -> Double.compare(a.distance, b.distance));

        List<Element> result = new ArrayList<>();
        for (ElementWithDistance ewd : matches) {
            result.add(ewd.element);
        }
        return result;
    }

    /**
     * Click the first matching element.
     */
    public Element click(String locator) {
        Element element = find(locator);
        element.click();
        return element;
    }

    /**
     * Check if the element exists.
     */
    public boolean exists(String locator) {
        return !findAll(locator).isEmpty();
    }

    private boolean hasVerticalOverlap(double y1, double bottom1, double y2, double bottom2, double tolerance) {
        // Check if the vertical ranges overlap (with tolerance)
        return bottom1 + tolerance >= y2 && bottom2 + tolerance >= y1;
    }

    private boolean hasHorizontalOverlap(double x1, double right1, double x2, double right2, double tolerance) {
        // Check if the horizontal ranges overlap (with tolerance)
        return right1 + tolerance >= x2 && right2 + tolerance >= x1;
    }

    private String positionDescription() {
        return switch (position) {
            case RIGHT_OF -> "to the right of";
            case LEFT_OF -> "to the left of";
            case ABOVE -> "above";
            case BELOW -> "below";
            case NEAR -> "near";
        };
    }

    private static class ElementWithDistance {
        final Element element;
        final double distance;

        ElementWithDistance(Element element, double distance) {
            this.element = element;
            this.distance = distance;
        }
    }

}
