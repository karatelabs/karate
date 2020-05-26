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

import com.intuit.karate.robot.Window;

/**
 *
 * @author pthomas3
 */
public class WinWindow extends WinElement implements Window {
    
    private final IUIAutomationWindowPattern window;
    
    public WinWindow(WinRobot robot, IUIAutomationElement e) {
        super(robot, e);
        window = e.getCurrentPattern(IUIAutomationWindowPattern.class);
    }

    @Override
    public void close() {
        window.close();
    }

    @Override
    public void restore() {
        window.restore();
    }

    @Override
    public void minimize() {
        window.minimize();
    }

    @Override
    public void maximize() {
        window.maximize();
    }        
    
}
