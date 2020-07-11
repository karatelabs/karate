/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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

import com.intuit.karate.core.AutoDef;
import com.intuit.karate.core.Plugin;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 *
 * @author pthomas3
 */
public interface Robot extends Plugin {

    static final List<String> METHOD_NAMES = Plugin.methodNames(Robot.class);

    @Override
    default List<String> methodNames() {
        return METHOD_NAMES;
    }

    @AutoDef
    Robot retry();

    @AutoDef
    Robot retry(int count);

    @AutoDef
    Robot retry(Integer count, Integer interval);

    @AutoDef
    Robot delay(int millis);

    @AutoDef
    Robot click();

    @AutoDef
    Robot click(int num);

    @AutoDef
    Robot doubleClick();
    
    @AutoDef
    Robot rightClick();    

    @AutoDef
    Robot press();

    @AutoDef
    Robot release();

    @AutoDef
    Robot input(String[] values);
    
    @AutoDef
    Robot input(String[] values, int delay);  
    
    @AutoDef
    Robot input(String chars, int delay);

    @AutoDef
    Robot input(String value);

    @AutoDef
    Element input(String locator, String value);

    @AutoDef
    byte[] screenshot();

    @AutoDef
    byte[] screenshotActive();
    
    @AutoDef
    Robot move(int x, int y);

    @AutoDef
    Robot click(int x, int y);

    @AutoDef
    Element highlight(String locator);
    
    @AutoDef
    List<Element> highlightAll(String locator);    

    @AutoDef
    Element locate(String locator);
    
    @AutoDef
    List<Element> locateAll(String locator);

    @AutoDef
    Element optional(String locator);

    @AutoDef
    boolean exists(String locator);

    @AutoDef
    Element move(String locator);
    
    @AutoDef
    Element focus(String locator);    

    @AutoDef
    Element click(String locator);
    
    @AutoDef
    Element select(String locator);

    @AutoDef
    Element press(String locator);

    @AutoDef
    Element release(String locator);

    @AutoDef
    Element window(String title);

    @AutoDef
    Element window(Predicate<String> condition);
    
    @AutoDef
    boolean windowExists(String locator);

    @AutoDef
    Element windowOptional(String locator);   
    
    @AutoDef
    Element waitForWindowOptional(String locator);     

    @AutoDef
    Object waitUntil(Supplier<Object> condition);
    
    @AutoDef
    Object waitUntilOptional(Supplier<Object> condition);    

    @AutoDef
    Element waitFor(String locator);
    
    @AutoDef
    Element waitForOptional(String locator);    

    @AutoDef
    Element waitForAny(String locator1, String locator2);

    @AutoDef
    Element waitForAny(String[] locators);
    
    @AutoDef
    Element activate(String locator);
    
    List<Window> getAllWindows(); // purely for debug convenience        
    
    Element getActive(); // getter
    
    Robot setActive(Element e); // setter    

    Element getRoot(); // getter

    Element getFocused(); // getter

    String getClipboard(); // getter
    
    Location getLocation(); // getter
    
}
