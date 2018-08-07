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
package com.intuit.karate.selenium.domain;

import java.util.Map;

/**
 * @author vmchukky
 */

// TestCommand <-> Step
public class TestCommand {
    String id; // we may not need this
    String comment;
    String command;
    String target;
    String value;

    public TestCommand(Map<String, Object> commandJson) {
        this.id = (String) commandJson.get("id");
        this.comment = (String) commandJson.get("comment");
        this.command = (String) commandJson.get("command");
        this.target = (String) commandJson.get("target");
        this.value = (String) commandJson.get("value");
    }

    public String convert() {
        //till we add support for individual commands lets use toString
        //TODO needs fix
        return toString();
    }

    @Override
    public String toString() {
        return "TestCommand{" +
                "id='" + id + '\'' +
                ", comment='" + comment + '\'' +
                ", command='" + command + '\'' +
                ", target='" + target + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
