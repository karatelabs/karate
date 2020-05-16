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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class DriverMouse implements Mouse {

    private final Driver driver;

    public DriverMouse(Driver driver) {
        this.driver = driver;
    }

    private Integer duration;
    private final List<Map<String, Object>> actions = new ArrayList();
    private Number x, y;

    private Map<String, Object> moveAction(int x, int y) {
        // {"type":"pointer","id":"1","actions":[{"type":"pointerMove","x":250,"y":250}]}        
        Map<String, Object> map = new HashMap();
        map.put("type", "pointerMove");
        map.put("x", x);
        map.put("y", y);
        if (duration != null) {
            map.put("duration", duration);
        }
        return map;
    }

    @Override
    public DriverMouse duration(Integer duration) {
        this.duration = duration;
        return this;
    }

    @Override
    public DriverMouse move(String locator) {
        Map<String, Object> map = driver.position(locator);
        Number x = (Number) map.get("x");
        Number y = (Number) map.get("y");
        return move(x, y);
    }

    @Override
    public DriverMouse move(Number x, Number y) {
        this.x = x == null ? 0 : x;
        this.y = y == null ? 0 : y;
        Map<String, Object> action = moveAction(this.x.intValue(), this.y.intValue());
        actions.add(action);
        return this;
    }

    @Override
    public DriverMouse down() {
        Map<String, Object> map = new HashMap();
        map.put("type", "pointerDown");
        map.put("button", 0);
        actions.add(map);
        return this;
    }

    @Override
    public DriverMouse up() {
        Map<String, Object> up = new HashMap();
        up.put("type", "pointerUp");
        up.put("button", 0);
        actions.add(up);
        return go();
    }

    @Override
    public DriverMouse submit() {
        driver.submit();
        return this;
    }

    @Override
    public DriverMouse click() {
        return down().up();
    }

    @Override
    public DriverMouse doubleClick() {
        String js = "document.elementFromPoint(" + x + "," + y + ").dispatchEvent(new MouseEvent('dblclick'))";
        driver.script(js);
        return this;
    }

    @Override
    public DriverMouse go() {
        Map<String, Object> map = new HashMap();
        map.put("type", "pointer");
        map.put("id", "1");
        map.put("actions", actions);
        driver.actions(Collections.singletonList(map));
        actions.clear();
        return this;
    }

}
