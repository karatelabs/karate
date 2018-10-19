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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author vmchukky
 */

// TestCase <-> Scenario ???
public class TestCase extends TestBase {
    List<TestCommand> commands;

    public TestCase(Map<String, Object> testJson) {
        super(testJson);
        this.commands = new ArrayList<>();
        List<Map<String, Object>> commandList = (List<Map<String, Object>>) testJson.get("commands");
        for (Map<String, Object> commandJson : commandList) {
            this.commands.add(new TestCommand(commandJson));
        }
    }

    public String convert(String url, HashMap<String, String> variables) {
        StringBuilder sb = new StringBuilder("\nScenario: ")
                .append(getIdentifierName());

        for (TestCommand command : commands) {
            sb.append(command.convert(url, variables));
        }

        return sb.toString();
    }
}
