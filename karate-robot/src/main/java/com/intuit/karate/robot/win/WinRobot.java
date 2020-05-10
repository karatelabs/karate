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

import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.robot.Robot;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 *
 * @author pthomas3
 */
public class WinRobot extends Robot {
    
    public WinRobot(ScenarioContext context, Map<String, Object> options) {
        super(context, options);
    }

    private static void focusWinOs(WinDef.HWND hwnd) {
        User32.INSTANCE.ShowWindow(hwnd, 9); // SW_RESTORE
        User32.INSTANCE.SetForegroundWindow(hwnd);
    }  

    @Override
    protected boolean focusWindowInternal(String title) {      
        WinDef.HWND hwnd = User32.INSTANCE.FindWindow(null, title);
        if (hwnd == null) {
            return false;
        } else {
            focusWinOs(hwnd);
            return true;
        }
    }

    @Override
    public boolean focusWindow(Predicate<String> condition) {
        final AtomicBoolean found = new AtomicBoolean();
        User32.INSTANCE.EnumWindows((WinDef.HWND hwnd, com.sun.jna.Pointer p) -> {
            char[] windowText = new char[512];
            User32.INSTANCE.GetWindowText(hwnd, windowText, 512);
            String windowName = Native.toString(windowText);
            logger.debug("scanning window: {}", windowName);
            if (condition.test(windowName)) {
                found.set(true);
                focusWinOs(hwnd);
                return false;
            }
            return true;
        }, null);
        return found.get();
    }
    
}
