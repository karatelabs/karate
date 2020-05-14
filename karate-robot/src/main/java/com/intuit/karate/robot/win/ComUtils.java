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

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.PointerByReference;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ComUtils {
    
    protected static final Logger logger = LoggerFactory.getLogger(ComUtils.class);
    
    private ComUtils() {
        // only static methods
    }
    
    public static final ComLibrary LIBRARY;
    public static final ComInterface AUTO_INTERFACE;
    public static final PointerByReference AUTO_REF;

    static {
        LIBRARY = new ComLibrary("{944DE083-8FB8-45CF-BCB7-C477ACB2F897}", 1, 0);
        ComInterface autoClass = LIBRARY.interfaces.get("CUIAutomation");
        AUTO_INTERFACE = LIBRARY.interfaces.get("IUIAutomation");
        Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_APARTMENTTHREADED);
        Guid.GUID CLSID = new Guid.GUID(autoClass.guid);
        Guid.IID IID = new Guid.IID(AUTO_INTERFACE.guid);
        AUTO_REF = new PointerByReference();
        WinNT.HRESULT hr = Ole32.INSTANCE.CoCreateInstance(CLSID, null, WTypes.CLSCTX_SERVER, IID, AUTO_REF);
        COMUtils.checkRC(hr);
        logger.debug("init: {}, interface id: {}", autoClass.guid, AUTO_INTERFACE.guid);
    } 
    
    public static int enumValue(String name, String key) {
        Map<String, Integer> map = LIBRARY.enumKeyValues.get(name);
        if (map == null) {
            throw new RuntimeException("no such enum: " + name);
        }
        Integer value = map.get(key);
        if (value == null) {
            throw new RuntimeException("enum: " + name + " does not contain key: " + key);
        }
        return value;
    }    
    
    public static String enumKey(String name, int value) {
        Map<Integer, String> map = LIBRARY.enumValueKeys.get(name);
        if (map == null) {
            throw new RuntimeException("no such enum: " + name);
        }
        String key = map.get(value);
        if (key == null) {
            throw new RuntimeException("enum: " + name + " does not contain value: " + value);
        }
        return key;
    }     
    
}
