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

import com.intuit.karate.ScriptValue;
import com.intuit.karate.validator.RegexValidator;

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

    private static final RegexValidator urlValidator = new RegexValidator("^(https?|file)://*");

    //static final

    public TestCommand(Map<String, Object> commandJson) {
        this.id = getLower("id", commandJson);
        this.comment = getLower("comment", commandJson);
        this.command = getLower("command", commandJson);
        this.target = getLower("target", commandJson);
        this.value = getLower("value", commandJson);
    }

    // https://github.com/SeleniumHQ/selenium-ide/blob/master/packages/selianize/src/command.js
    public String convert(String url) {
        StringBuffer sb = new StringBuffer("\n# ").append(id).append("\n");
        if ("open".equals(command)) {
            String commandUrl = url;
            if (urlValidator.validate(new ScriptValue(target)).isPass()) {
                commandUrl = target;
            } else {
                commandUrl = getUrlFromBaseAndPath(commandUrl, target);
            }
            appendOpenRequest(sb, commandUrl);

        } else if ("clickat".equals(command) || "click".equals(command) || "clickandwait".equals(command)) {
            getFetchElementId(sb, target);
            appendclickElementRequest(sb).append("And request {}\n");
        } else if ("echo".equals(command)) {
            // TODO handle variable substitution and colors?#!@%^X
            sb.append("* print '").append(target).append("' + '").append(value).append("'\n");
        } else {
            //till we incrementally add support for all commands
            //TODO needs fix
            sb.append(toString());
        }

        appendFooter(sb);
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

    private String getLower(String key, Map<String, Object> map) {
        String val = (String) map.get(key);
        if (val != null) {
            val = val.trim().toLowerCase();
        }
        return val;
    }

    private StringBuffer appendFooter(StringBuffer sb) {
        return sb.append("When method POST\nThen status 200\n")
                .append("And assert response.status == 0\n");
    }

    private StringBuffer appendOpenRequest(StringBuffer sb, String url) {
        return appendRequestParams(appendGivenUrl(sb, "url"), "url", url);
    }

    private StringBuffer appendclickElementRequest(StringBuffer sb) {
        return appendGivenUrl(sb,
                "element/' + " + TestBase.DRIVER_ELEMENT_ID_VAR + " + '/click");
    }

    private StringBuffer appendElementRequest(StringBuffer sb, String using, String value) {
        return appendRequestParams(appendGivenUrl(sb, "element"),
                "using", using, "value", value);
    }

    private StringBuffer appendGivenUrl(StringBuffer sb, String path) {
        return sb.append("Given url ").append(TestBase.DRIVER_SESSION_URL_VAR)
                .append(" + '/").append(path).append("'\n");
    }

    private StringBuffer appendRequestParams(StringBuffer sb, String key, String value) {
        value = value.replace("'", "\"");
        return sb.append("And request {").append(key).append(":'")
                .append(value).append("'}\n");
    }

    private StringBuffer appendRequestParams(StringBuffer sb, String key1, String value1,
                                             String key2, String value2) {
        value1 = value1.replace("'", "\"");
        value2 = value2.replace("'", "\"");
        return sb.append("And request {")
                .append(key1).append(":'").append(value1).append("', ")
                .append(key2).append(":'").append(value2).append("'}\n");
    }

    private StringBuffer getFetchElementId(StringBuffer sb, String target) {
        String[] tokens = target.split("=");
        String using = tokens[0];
        String value = tokens[1];
        // TODO: xpath is failing (can't find matching element(s)) as of now, need to debug
        if (target.startsWith("//")) {
            using = "xpath";
            value = target;
        }
        appendFooter(appendElementRequest(sb, using, value));

        return sb.append("* def ").append(TestBase.DRIVER_ELEMENT_ID_VAR)
                .append(" = response.value.ELEMENT\n")
                .append("* print 'Element ID is '" + TestBase.DRIVER_ELEMENT_ID_VAR).append("\n");
    }

    // just to avoid the base//path (lets add it to HttpUtil)
    private String getUrlFromBaseAndPath(String base, String path) {
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return (path.startsWith("/") ? (base + path) : (base + '/' + path));
    }
}
