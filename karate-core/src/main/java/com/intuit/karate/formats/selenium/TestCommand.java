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

import com.intuit.karate.ScriptValue;
import com.intuit.karate.validator.RegexValidator;

import java.util.HashMap;
import java.util.Map;

/**
 * @author vmchukky
 */

// TestCommand <-> Step
public class TestCommand {
    private String id;
    private String comment;
    private String command;
    private String target;
    private String value;

    private static final RegexValidator urlValidator = new RegexValidator("^(https?|file)://*");

    TestCommand(Map<String, Object> commandJson) {
        this.id = get("id", commandJson);
        this.comment = get("comment", commandJson);
        this.command = get("command", commandJson);
        this.target = get("target", commandJson);
        this.value = get("value", commandJson);
    }

    // https://github.com/SeleniumHQ/selenium-ide/blob/master/packages/selianize/src/command.js
    public String convert(String url, HashMap<String, String> variables) {
        target = preProcess(target, variables);
        value = preProcess(value, variables);
        StringBuilder sb = new StringBuilder("\n# ").append(toString()).append("\n");
        if ("open".equals(command)) {
            String commandUrl = url;
            if (urlValidator.validate(new ScriptValue(target)).isPass()) {
                commandUrl = target;
            } else {
                commandUrl = getUrlFromBaseAndPath(commandUrl, target);
            }
            emitOpen(sb, commandUrl);
        } else if ("clickAt".equals(command) || "click".equals(command) || "clickAndWait".equals(command)) {
            emitClick(sb);
        } else if ("verifyText".equals(command) || "assertText".equals(command)) {
            emitVerifyText(sb);
        } else if ("verifyTitle".equals(command) || "assertTitle".equals(command)) {
            emitVerifyTitle(sb);
        }  else if ("type".equals(command) || "sendKeys".equals(command)) {
            emitSendKeys(sb);
        } else if ("store".equals(command)) {
            variables.put("${" + value + '}', target);
        } else if ("echo".equals(command)) {
            emitEcho(sb);
        } else if ("pause".equals(command)) {
            emitPause(sb);
        } else {
            //till we incrementally add support for all commands
            //TODO needs fix
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "# TestCommand{" +
                "id='" + id + '\'' +
                ", comment='" + comment + '\'' +
                ", command='" + command + '\'' +
                ", target='" + target + '\'' +
                ", value='" + value + '\'' +
                '}';
    }

    private String get(String key, Map<String, Object> map) {
        String val = (String) map.get(key);
        if (val != null) {
            val = val.trim(); //.toLowerCase();
        }
        return val;
    }

    // just to avoid the base//path (lets add it to HttpUtil)
    private String getUrlFromBaseAndPath(String base, String path) {
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return (path.startsWith("/") ? (base + path) : (base + '/' + path));
    }

    private void emitOpen(StringBuilder sb, String url) {
        appendGivenUrl(sb, "url");
        appendRequestParams(sb, "url", url);
        appendWhenThen(sb);
    }

    private void emitEcho(StringBuilder sb) {
        sb.append("* print ").append(target).append('\n');
    }

    private void emitPause(StringBuilder sb) {
        sb.append("* def sleep = function(pause){ java.lang.Thread.sleep(pause) }\n")
                .append("* sleep(").append(target).append(")\n");
    }

    private void emitClick(StringBuilder sb) {
        emitFindElement(sb);
        appendGivenUrl(sb, "element/' + " + TestBase.DRIVER_ELEMENT_ID_VAR + " + '/click");
        sb.append("And request {}\n");
        appendWhenThen(sb);
    }

    private void emitVerifyText(StringBuilder sb) {
        emitFindElement(sb);
        appendGivenUrl(sb, "element/' + " + TestBase.DRIVER_ELEMENT_ID_VAR + " + '/text");
        appendWhenThen(sb, "GET");
        sb.append("* def ").append(TestBase.PAGE_TITLE_VAR)
                .append(" = response.value\n")
                .append("* print 'Page Title is '" + TestBase.PAGE_TITLE_VAR).append('\n')
                .append(getAssertion(TestBase.PAGE_TITLE_VAR, target));
    }

    private void emitVerifyTitle(StringBuilder sb) {
        appendGivenUrl(sb, "title");
        appendWhenThen(sb, "GET");
        sb.append("* def ").append(TestBase.PAGE_TITLE_VAR)
                .append(" = response.value\n")
                .append("* print 'Page Title is '" + TestBase.PAGE_TITLE_VAR).append('\n')
                .append(getAssertion(TestBase.PAGE_TITLE_VAR,target));
    }

    private void emitSendKeys(StringBuilder sb) {
        emitFindElement(sb);
        appendGivenUrl(sb, "element/' + " + TestBase.DRIVER_ELEMENT_ID_VAR + " + '/value");
        appendRequestParamsAsArray(sb,"value", value);
        appendWhenThen(sb);
    }

    private void emitFindElement(StringBuilder sb) {
        String[] tokens = target.split("=");
        String using = tokens[0];
        String value = tokens[1];
        // TODO: xpath is failing (can't find matching element(s)) as of now, need to debug
        if (target.startsWith("//")) {
            using = "xpath";
            value = target;
        } else {
            using = getLocateBy(using);
        }
        appendGivenUrl(sb, "element");
        appendRequestParams(sb, "using", using, "value", value);
        appendWhenThen(sb);

        sb.append("* def ").append(TestBase.DRIVER_ELEMENT_ID_VAR)
                .append(" = response.value.ELEMENT\n")
                .append("* print 'Element ID is '" + TestBase.DRIVER_ELEMENT_ID_VAR).append("\n");
    }

    private void appendGivenUrl(StringBuilder sb, String path) {
        sb.append("Given url ").append(TestBase.DRIVER_SESSION_URL)
                .append(" + '/").append(path).append("'\n");
    }

    private void appendWhenThen(StringBuilder sb) {
        appendWhenThen(sb, "POST"); // default is POST
    }

    private void appendWhenThen(StringBuilder sb, String method) {
        sb.append("When method ").append(method).append("\nThen status 200\n")
                .append("And assert response.status == 0\n");
    }

    private void appendRequestParams(StringBuilder sb, String key, String value) {
        value = value.replace("'", "\"");
        sb.append("And request {").append(key).append(":'")
                .append(value).append("'}\n");
    }

    private void appendRequestParamsAsArray(StringBuilder sb, String key, String value) {
        value = value.replace("'", "\"");
        sb.append("And request {").append(key).append(":['")
                .append(value).append("']}\n");
    }

    private void appendRequestParams(StringBuilder sb, String key1, String value1,
                                     String key2, String value2) {
        value1 = value1.replace("'", "\"");
        value2 = value2.replace("'", "\"");
        sb.append("And request {")
                .append(key1).append(":'").append(value1).append("', ")
                .append(key2).append(":'").append(value2).append("'}\n");
    }

    private String preProcess(String param, HashMap<String, String> variables) {
        if (param != null) {
            int index;
            StringBuilder sb = new StringBuilder(param);
            for (String var : variables.keySet()) {
                if ((index = sb.indexOf(var)) >= 0) {
                    sb.replace(index, index + var.length(), variables.get(var));
                }
            }
            param = sb.toString();
        }
        return param;
    }

    // should consider glob:, regexp: and exact:
    private String getAssertion(String varName, String expectedValue) {
        return "* assert " + varName + " == " + expectedValue;
    }

    private String getLocateBy(String locator) {
        return locator; // TODO: css -> css selector etc
    }

}
