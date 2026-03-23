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
package io.karatelabs.gherkin;

/**
 * Represents a parsed match expression.
 * <p>
 * Handles expressions like:
 * <ul>
 *   <li>"foo == bar"</li>
 *   <li>"foo contains { a: 1 }"</li>
 *   <li>"each item == '#number'"</li>
 *   <li>"foo == 'hello == world'" (quotes properly handled)</li>
 * </ul>
 */
public class MatchExpression {

    private final boolean each;
    private final String actualExpr;
    private final String operator;
    private final String expectedExpr;

    public MatchExpression(boolean each, String actualExpr, String operator, String expectedExpr) {
        this.each = each;
        this.actualExpr = actualExpr;
        this.operator = operator;
        this.expectedExpr = expectedExpr;
    }

    public boolean isEach() {
        return each;
    }

    public String getActualExpr() {
        return actualExpr;
    }

    public String getOperator() {
        return operator;
    }

    public String getExpectedExpr() {
        return expectedExpr;
    }

    /**
     * Converts operator string to Match.Type enum name.
     * E.g., "==" -> "EQUALS", "contains deep" -> "CONTAINS_DEEP"
     */
    public String getMatchTypeName() {
        String base = switch (operator) {
            case "==" -> "EQUALS";
            case "!=" -> "NOT_EQUALS";
            case "contains" -> "CONTAINS";
            case "!contains" -> "NOT_CONTAINS";
            case "contains deep" -> "CONTAINS_DEEP";
            case "contains only" -> "CONTAINS_ONLY";
            case "contains only deep" -> "CONTAINS_ONLY_DEEP";
            case "contains any" -> "CONTAINS_ANY";
            case "contains any deep" -> "CONTAINS_ANY_DEEP";
            case "within" -> "WITHIN";
            case "!within" -> "NOT_WITHIN";
            default -> throw new RuntimeException("Unknown operator: " + operator);
        };
        return each ? "EACH_" + base : base;
    }

    @Override
    public String toString() {
        return (each ? "each " : "") + actualExpr + " " + operator + " " + expectedExpr;
    }

}
