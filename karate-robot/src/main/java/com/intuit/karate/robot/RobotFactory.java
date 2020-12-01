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
package com.intuit.karate.robot;

import com.intuit.karate.FileUtils;
import com.intuit.karate.robot.linux.LinuxRobot;
import com.intuit.karate.robot.mac.MacRobot;
import com.intuit.karate.robot.win.WinRobot;
import java.util.Map;
import com.intuit.karate.core.PluginFactory;
import com.intuit.karate.core.ScenarioRuntime;
import java.util.HashMap;

/**
 *
 * @author pthomas3
 */
public class RobotFactory implements PluginFactory {
    
    private static final FileUtils.OsType OS_TYPE = FileUtils.getOsType();    

    @Override
    public Robot create(ScenarioRuntime runtime, Map<String, Object> options) {
        if (options == null) {
            options = new HashMap();
        }
        switch (OS_TYPE) {
            case LINUX:
                return new LinuxRobot(runtime, options);
            case MACOSX: 
                return new MacRobot(runtime, options);
            case WINDOWS: 
                return new WinRobot(runtime, options);
            default:
                throw new RuntimeException("os not supported: " + OS_TYPE);
        }
    }        
   
}
