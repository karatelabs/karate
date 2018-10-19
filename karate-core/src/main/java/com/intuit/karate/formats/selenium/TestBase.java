/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.formats.selenium;

import java.util.Map;

/**
 * @author vmchukky
 */
public abstract class TestBase {
    static final String PAGE_TITLE_VAR = "pageTitle";
    static final String DRIVER_ELEMENT_ID_VAR = "webdriverElementId";

    static final String DRIVER = "webDriver";
    static final String DRIVER_URL = DRIVER + '.' + "url";
    static final String DRIVER_BROWSER = DRIVER + '.' + "browser";
    static final String DRIVER_SESSION = "session";
    static final String DRIVER_SESSION_ID = DRIVER_SESSION + '.' + "id";
    static final String DRIVER_SESSION_URL = DRIVER_SESSION + '.' + "url";


    protected final String id;
    protected final String name;

    public TestBase(String id, String name) {
        this.id = id;
        this.name = name.trim().replace(" ", "_");
    }

    public TestBase(Map<String, Object> json) {
        this.id = (String) json.get("id");
        this.name = (String) json.get("name");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    // used to name the feature file / scenario name etc
    public String getIdentifierName() {
        return name.trim() + '-' + id.trim();
    }

}
