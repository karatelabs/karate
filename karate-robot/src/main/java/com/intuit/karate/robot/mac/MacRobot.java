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
package com.intuit.karate.robot.mac;

import com.intuit.karate.StringUtils;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.robot.Element;
import com.intuit.karate.robot.ImageElement;
import com.intuit.karate.robot.RobotBase;
import com.intuit.karate.robot.Window;
import com.intuit.karate.shell.Command;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 *
 * @author pthomas3
 */
public class MacRobot extends RobotBase {

    public MacRobot(ScenarioContext context, Map<String, Object> options) {
        super(context, options);
    }

    @Override
    public Map<String, Object> afterScenario() {
        return Collections.EMPTY_MAP;
    }

    private static final String MAC_GET_PROCS
            = "    tell application \"System Events\""
            + "\n    set procs to (processes whose background only is false)"
            + "\n    set results to {}"
            + "\n    repeat with n from 1 to the length of procs"
            + "\n      set p to item n of procs"
            + "\n      set entry to { name of p as text,\"|\"}"
            + "\n      set end of results to entry"
            + "\n    end repeat"
            + "\n  end tell"
            + "\n  results";

    public static List<String> getAppsMacOs() {
        String res = Command.exec(true, null, "osascript", "-e", MAC_GET_PROCS);
        res = res + ", ";
        res = res.replace(", |, ", "\n");
        return StringUtils.split(res, '\n', false);
    }

    @Override
    public Element windowInternal(String title) {
        Command.exec(true, null, "osascript", "-e", "tell app \"" + title + "\" to activate");
        return new ImageElement(screen); // TODO
    }

    @Override
    public Element windowInternal(Predicate<String> condition) {
        List<String> list = getAppsMacOs();
        for (String s : list) {
            if (condition.test(s)) {
                Command.exec(true, null, "osascript", "-e", "tell app \"" + s + "\" to activate");
                return new ImageElement(screen); // TODO
            }
        }
        return null;
    }

    @Override
    public List<Element> locateAllInternal(Element searchRoot, String locator) {
        throw new UnsupportedOperationException("not supported yet.");
    } 

    @Override
    public Element locateInternal(Element root, String locator) {
        throw new UnsupportedOperationException("not supported yet.");
    }

    @Override
    public Element getRoot() {
        return new ImageElement(screen);
    }

    @Override
    public Element getFocused() {
        return new ImageElement(screen);
    }

    @Override
    public List<Window> getAllWindows() {
        throw new UnsupportedOperationException("not supported yet.");
    }        

}
