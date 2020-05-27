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
package com.intuit.karate.robot.win;

import com.intuit.karate.core.AutoDef;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.robot.Element;
import com.intuit.karate.robot.RobotBase;
import com.intuit.karate.robot.StringMatcher;
import com.intuit.karate.robot.Window;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 *
 * @author pthomas3
 */
public class WinRobot extends RobotBase {

    protected static final IUIAutomation UIA = IUIAutomation.INSTANCE;

    public WinRobot(ScenarioContext context, Map<String, Object> options) {
        super(context, options);
    }

    @Override
    public Map<String, Object> afterScenario() {
        logger.debug("after scenario, current window: {}", currentWindow);
        if (autoClose && command != null && currentWindow != null) {
            logger.debug("will attempt to close window for: {}", currentWindow.getName());
            WinUser.HWND hwnd = currentWindow.<IUIAutomationElement>toNative().getCurrentNativeWindowHandle();
            User32.INSTANCE.PostMessage(hwnd, WinUser.WM_QUIT, null, null);
            command.close(false);
        }
        return Collections.EMPTY_MAP;
    }

    @Override
    public List<Window> getAllWindows() {
        IUIAutomationCondition isWindow = UIA.createPropertyCondition(Property.ControlType, ControlType.Window.value);
        IUIAutomationElementArray array = UIA.getRootElement().findAll(TreeScope.Descendants, isWindow);
        int count = array.getLength();
        List<Window> list = new ArrayList(count);
        for (int i = 0; i < count; i++) {
            IUIAutomationElement e = array.getElement(i);
            if (e.isValid()) {
                list.add(new WinWindow(this, e));
            }
        }
        return list;        
    }        

    @Override
    protected Element windowInternal(String title) {
        return windowInternal(new StringMatcher(title));
    }

    @Override
    protected Element windowInternal(Predicate<String> condition) {
        IUIAutomationCondition isWindow = UIA.createPropertyCondition(Property.ControlType, ControlType.Window.value);
        IUIAutomationElementArray windows = UIA.getRootElement().findAll(TreeScope.Descendants, isWindow);
        int count = windows.getLength();
        for (int i = 0; i < count; i++) {
            IUIAutomationElement child = windows.getElement(i);
            String name = child.getCurrentName();
            if (logger.isTraceEnabled()) {
                logger.trace("scanning window: {}", name);
            }
            if (condition.test(name)) {
                logger.debug("found window: {}", name);
                return new WinWindow(this, child).focus();
            }
        }
        logger.warn("failed to find window: {}", condition);
        return null;
    }

    private IUIAutomationCondition by(Property property, String value) {
        return UIA.createPropertyCondition(property, value);
    }

    protected List<Element> toElements(IUIAutomationElementArray array) {
        int count = array.getLength();
        List<Element> list = new ArrayList(count);
        for (int i = 0; i < count; i++) {
            IUIAutomationElement e = array.getElement(i);
            if (e.isValid()) {
                list.add(new WinElement(this, e));
            }
        }
        return list;
    }

    @Override
    public List<Element> locateAllInternal(Element root, String locator) {
        IUIAutomationElement parent = root.<IUIAutomationElement>toNative();
        IUIAutomationCondition condition;
        if (PathSearch.isWildcard(locator)) {
            locator = "//*{" + locator + "}";
        }
        if (locator.startsWith("/")) {
            List<Element> searchResults = new ArrayList();
            PathSearch search = new PathSearch(locator, true);
            walkPathAndFind(searchResults, search, UIA.getControlViewWalker(), parent, 0);
            return searchResults;
        } else if (locator.startsWith("#")) {
            condition = by(Property.AutomationId, locator.substring(1));
        } else {
            condition = by(Property.Name, locator);
        }
        IUIAutomationElementArray found = parent.findAll(TreeScope.Descendants, condition);
        return toElements(found);
    }

    @Override
    public Element locateInternal(Element root, String locator) {
        IUIAutomationElement parent = root.<IUIAutomationElement>toNative();
        IUIAutomationCondition condition;
        if (PathSearch.isWildcard(locator)) {
            locator = "//*{" + locator + "}";
        }
        if (locator.startsWith("/")) {
            List<Element> searchResults = new ArrayList();
            PathSearch search = new PathSearch(locator, false);
            walkPathAndFind(searchResults, search, UIA.getControlViewWalker(), parent, 0);
            if (searchResults.isEmpty()) {
                return null;
            } else {
                return searchResults.get(0);
            }
        } else if (locator.startsWith("#")) {
            condition = by(Property.AutomationId, locator.substring(1));
        } else {
            condition = by(Property.Name, locator);
        }
        IUIAutomationElement found = parent.findFirst(TreeScope.Descendants, condition);
        if (!found.isValid()) { // important in this case
            return null;
        }
        return new WinElement(this, found);
    }

    @Override
    public Element getDesktop() {
        return new WinElement(this, UIA.getRootElement());
    }

    @AutoDef
    @Override
    public Element getFocused() {
        return new WinElement(this, UIA.getFocusedElement());
    }

    private void walkPathAndFind(List<Element> searchResults, PathSearch search,
            IUIAutomationTreeWalker walker, IUIAutomationElement e, int depth) {
        PathSearch.Chunk chunk = search.chunks.get(depth);
        IUIAutomationCondition condition;
        ControlType controlType;
        if (chunk.controlType == null || "*".equals(chunk.controlType)) {
            condition = UIA.getControlViewCondition();
            controlType = null;
        } else {
            controlType = ControlType.fromName(chunk.controlType);
            condition = UIA.createPropertyCondition(Property.ControlType, controlType.value);
        }
        IUIAutomationElementArray array = e.findAll(chunk.anyDepth ? TreeScope.Descendants : TreeScope.Children, condition);
        if (!array.isValid()) { // the tree can be unstable
            return;
        }
        int count = array.getLength();
        boolean leaf = depth == search.chunks.size() - 1;
        for (int i = 0; i < count; i++) {
            if (chunk.index != -1 && chunk.index != i) {
                continue;
            }
            IUIAutomationElement child = array.getElement(i);
            if (!child.isValid()) { // the tree can be unstable
                continue;
            }
            if (chunk.nameCondition != null) {
                String name = child.getCurrentName();
                if (!chunk.nameCondition.test(name)) {
                    continue;
                }
            }
            if (chunk.className != null) {
                String className = child.getClassName();
                if (!chunk.className.equalsIgnoreCase(className)) {
                    continue;
                }
            }
            if (leaf) {
                // already filtered to content-type, so we have a match !
                searchResults.add(new WinElement(this, child));
                if (!search.findAll) {
                    return; // exit early
                }
            } else {
                walkPathAndFind(searchResults, search, walker, child, depth + 1);
            }
        }
    }

}
