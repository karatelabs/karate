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

import com.intuit.karate.StringUtils;
import com.intuit.karate.core.AutoDef;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.robot.Element;
import com.intuit.karate.robot.RobotBase;
import com.intuit.karate.robot.StringMatcher;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;
import java.util.Collections;
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
                return toElement(child).focus();
            }
        }
        logger.warn("failed to find window: {}", condition);
        return null;
    }

    private IUIAutomationCondition by(Property property, String value) {
        return UIA.createPropertyCondition(property, value);
    }

    protected WinElement toElement(IUIAutomationElement element) {
        if (element == null || element.isNull()) {
            return null;
        }
        return new WinElement(this, element);
    }

    @Override
    public Element locateElementInternal(Element root, String locator) {
        IUIAutomationElement parent = root.<IUIAutomationElement>toNative();
        IUIAutomationCondition condition;
        if (locator.startsWith("{")) {
            IUIAutomationElement found = walkAndFind(new SearchOptions(locator), UIA.getControlViewWalker(), parent, 0);
            return toElement(found);
        } else if (locator.startsWith("#")) {
            condition = by(Property.AutomationId, locator.substring(1));
        } else {
            condition = by(Property.Name, locator);
        }
        IUIAutomationElement found = parent.findFirst(TreeScope.Descendants, condition);
        return toElement(found);
    }

    @Override
    public Element getDesktop() {
        return toElement(UIA.getRootElement());
    }

    @AutoDef
    @Override
    public Element locateFocus() {
        return toElement(UIA.getFocusedElement());
    }

    private IUIAutomationElement walkAndFind(SearchOptions search, IUIAutomationTreeWalker walker, IUIAutomationElement e, int depth) {
        IUIAutomationElement child = walker.getFirstChildElement(e);
        while (!child.isNull()) {
            if (logger.isTraceEnabled()) {
                String indent = StringUtils.repeat(' ', depth * 2);
                logger.trace("{}{}.{} - '{}'", indent, child.getControlType(), child.getClassName(), child.getCurrentName());
            }
            if (search.matches(child)) {
                logger.debug("found {} at depth: {}", search, depth);
                return child;
            }
            IUIAutomationElement found = walkAndFind(search, walker, child, depth + 1);
            if (found != null) {
                return found;
            }
            child = walker.getNextSiblingElement(child);
        }
        return null;
    }

}
