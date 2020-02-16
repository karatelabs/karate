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
package com.intuit.karate.driver;

import com.intuit.karate.StringUtils;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class ElementFinder implements Finder {

    public static enum Type {
        RIGHT,
        LEFT,
        ABOVE,
        BELOW,
        NEAR
    }

    private final Driver driver;
    private final String fromLocator;
    private final Type type;

    private String tag = "INPUT";

    public ElementFinder(Driver driver, String fromLocator, Type type) {
        this.driver = driver;
        this.fromLocator = fromLocator;
        this.type = type;
    }

    private static String forLoopChunk(ElementFinder.Type type) {
        switch (type) {
            case RIGHT:
                return "x += s;";
            case BELOW:
                return "y += s;";
            case LEFT:
                return "x -= s;";
            case ABOVE:
                return "y -= s;";
            default: // NEAR
                return " var a = 0.381966 * i; var x = (s + a) * Math.cos(a); var y = (s + a) * Math.sin(a);";
        }
    }        
    
    public static String exitCondition(String findTag) {
        int pos = findTag.indexOf('}');
        if (pos == -1) {
            return "e.tagName == '" + findTag.toUpperCase() + "'";
        }
        int caretPos = findTag.indexOf('^');        
        boolean contains = caretPos != -1 && caretPos < pos;
        if (!contains) {
            caretPos = 0;
        }
        String tagName = StringUtils.trimToNull(findTag.substring(caretPos + 1, pos));
        String suffix = tagName == null ? "" : " && e.tagName == '" + tagName.toUpperCase() + "'";
        String findText = findTag.substring(pos + 1);        
        if (contains) {
            return "e.textContent.trim().includes('" + findText + "')" + suffix;
        } else {
            return "e.textContent.trim() == '" + findText + "'" + suffix;
        }
    }

    private static String findScript(Driver driver, String locator, ElementFinder.Type type, String findTag) {
        Map<String, Object> pos = driver.position(locator);
        Number xNum = (Number) pos.get("x");
        Number yNum = (Number) pos.get("y");
        Number width = (Number) pos.get("width");
        Number height = (Number) pos.get("height");
        // get center point
        int x = xNum.intValue() + width.intValue() / 2;
        int y = yNum.intValue() + height.intValue() / 2;
        // o: origin, a: angle, s: step
        String fun = "var gen = " + DriverOptions.KARATE_REF_GENERATOR + ";"
                + " var o = { x: " + x + ", y: " + y + "}; var s = 10; var x = 0; var y = 0;"
                + " for (var i = 0; i < 200; i++) {"
                + forLoopChunk(type)
                + " var e = document.elementFromPoint(o.x + x, o.y + y);"
                // + " console.log(o.x +':' + o.y + ' ' + x + ':' + y + ' ' + e.tagName + ':' + e.textContent);"
                + " if (e && " + exitCondition(findTag) + ") return gen(e); "
                + " } return null";
        return DriverOptions.wrapInFunctionInvoke(fun);
    }

    private String getDebugString() {
        return fromLocator + ", " + type + ", " + tag;
    }

    @Override
    public Element find() {
        String js = findScript(driver, fromLocator, type, tag);
        String karateRef = (String) driver.script(js);
        if (karateRef == null) {
            throw new RuntimeException("unable to find: " + getDebugString());
        }
        return DriverElement.locatorExists(driver, DriverOptions.karateLocator(karateRef));
    }

    @Override
    public Element find(String tag) {
        this.tag = tag;
        return find();
    }

    @Override
    public Element clear() {
        return find().clear();
    }

    @Override
    public Element input(String value) {
        return find().input(value);
    }

    @Override
    public Element select(String value) {
        return find("select").select(value);
    }   
    
    @Override
    public Element select(int index) {
        return find("select").select(index);
    }    

    @Override
    public Element click() {
        return find().click();
    }

    @Override
    public Element highlight() {
        return find().highlight();
    }     

    @Override
    public Element retry() {
        return find().retry();
    }

    @Override
    public Element retry(int count) {
        return find().retry(count);
    }

    @Override
    public Element retry(Integer count, Integer interval) {
        return find().retry(count, interval);
    }    

}
