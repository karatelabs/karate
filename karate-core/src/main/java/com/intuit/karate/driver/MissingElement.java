/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
package com.intuit.karate.driver;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class MissingElement implements Element {

    private final Driver driver;
    private final String locator;

    public MissingElement(Driver driver, String locator) {
        this.driver = driver;
        this.locator = locator;
    }

    @Override
    public String getLocator() {
        return locator;
    }

    @Override
    public boolean isPresent() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public Map<String, Object> getPosition() {
        return null;
    }

    @Override
    public byte[] screenshot() {
        return null;
    }

    @Override
    public Element highlight() {
        return this;
    }

    @Override
    public Element focus() {
        return this;
    }

    @Override
    public Element clear() {
        return this;
    }

    @Override
    public Element click() {
        return this;
    }

    @Override
    public Element submit() {
        return this;
    }

    @Override
    public Mouse mouse() {
        return null;
    }

    @Override
    public Element input(String text) {
        return this;
    }

    @Override
    public Element input(String[] values) {
        return this;
    }

    @Override
    public Element input(String[] values, int delay) {
        return this;
    }

    @Override
    public Element select(String text) {
        return this;
    }

    @Override
    public Element select(int index) {
        return this;
    }

    @Override
    public Element switchFrame() {
        return this;
    }

    @Override
    public Element delay(int millis) {
        driver.delay(millis);
        return this;
    }

    @Override
    public Element retry() {
        return this;
    }

    @Override
    public Element retry(int count) {
        return this;
    }

    @Override
    public Element retry(Integer count, Integer interval) {
        return this;
    }

    @Override
    public Element waitFor() {
        return this;
    }

    @Override
    public Element waitForText(String text) {
        return this;
    }

    @Override
    public Element waitUntil(String expression) {
        return this;
    }

    @Override
    public Object script(String expression) {
        return null;
    }

    @Override
    public Element optional(String locator) {
        return this;
    }

    @Override
    public boolean exists(String locator) {
        return false;
    }

    @Override
    public Element locate(String locator) {
        return this;
    }

    @Override
    public List<Element> locateAll(String locator) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public String attribute(String name) {
        return null;
    }

    @Override
    public String property(String name) {
        return null;
    }

    @Override
    public String getHtml() {
        return null;
    }

    @Override
    public void setHtml(String html) {

    }

    @Override
    public String getText() {
        return null;
    }

    @Override
    public void setText(String text) {

    }

    @Override
    public String getValue() {
        return null;
    }

    @Override
    public void setValue(String value) {

    }

    @Override
    public Element getParent() {
        return this;
    }

    @Override
    public List<Element> getChildren() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Element getFirstChild() {
        return this;
    }

    @Override
    public Element getLastChild() {
        return this;
    }

    @Override
    public Element getPreviousSibling() {
        return this;
    }

    @Override
    public Element getNextSibling() {
        return this;
    }

    @Override
    public Finder rightOf() {
        return new MissingFinder(this);
    }

    @Override
    public Finder leftOf() {
        return new MissingFinder(this);
    }

    @Override
    public Finder above() {
        return new MissingFinder(this);
    }

    @Override
    public Finder below() {
        return new MissingFinder(this);
    }

    @Override
    public Finder near() {
        return new MissingFinder(this);
    }

}
