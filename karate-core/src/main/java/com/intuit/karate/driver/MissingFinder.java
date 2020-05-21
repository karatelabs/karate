/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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

/**
 *
 * @author pthomas3
 */
public class MissingFinder implements Finder {
    
    private final MissingElement element;
    
    public MissingFinder(MissingElement element) {
        this.element = element;
    }

    @Override
    public Element input(String value) {
        return element;
    }

    @Override
    public Element select(String value) {
        return element;
    }

    @Override
    public Element select(int index) {
        return element;
    }

    @Override
    public Element click() {
        return element;
    }

    @Override
    public Element clear() {
        return element;
    }

    @Override
    public Element find() {
        return element;
    }

    @Override
    public Element find(String tag) {
        return element;
    }

    @Override
    public Element highlight() {
        return element;
    }

    @Override
    public Element retry() {
        return element;
    }

    @Override
    public Element retry(int count) {
        return element;
    }

    @Override
    public Element retry(Integer count, Integer interval) {
        return element;
    }
    
}
