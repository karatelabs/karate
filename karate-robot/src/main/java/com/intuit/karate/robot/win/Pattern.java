/*
 * The MIT License
 *
 * Copyright 2020 pthomas3.
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

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public enum Pattern {

    Invoke(10000, IUIAutomationInvokePattern.class),
    Selection(10001),
    Value(10002, IUIAutomationValuePattern.class),
    RangeValue(10003),
    Scroll(10004),
    ExpandCollapse(10005),
    Grid(10006),
    GridItem(10007),
    MultipleView(10008),
    Window(10009, IUIAutomationWindowPattern.class),
    SelectionItem(10010, IUIAutomationSelectionItemPattern.class),
    Dock(10011),
    Table(10012),
    TableItem(10013),
    Text(10014),
    Toggle(10015),
    Transform(10016),
    ScrollItem(10017),
    LegacyIAccessible(10018),
    ItemContainer(10019),
    VirtualizedItem(10020),
    SynchronizedInput(10021),
    ObjectModel(10022),
    Annotation(10023),
    Text2(10024),
    Styles(10025),
    Spreadsheet(10026),
    SpreadsheetItem(10027),
    Transform2(10028),
    TextChild(10029),
    Drag(10030),
    DropTarget(10031),
    TextEdit(10032),
    CustomNavigation(10033);

    public final int value;
    public final Class type;

    private Pattern(int value) {
        this(value, null);
    }

    private Pattern(int value, Class type) {
        this.value = value;
        this.type = type;
    }

    private static final Map<String, Pattern> FROM_CLASS;
    private static final Map<String, Pattern> FROM_NAME;

    static {
        Pattern[] values = Pattern.values();
        FROM_CLASS = new HashMap(values.length);
        FROM_NAME = new HashMap(values.length);
        for (Pattern p : values) {
            if (p.type != null) {
                FROM_CLASS.put(p.type.getSimpleName(), p);
            }
            FROM_NAME.put(p.name().toLowerCase(), p);
        }
    }

    public static Pattern fromType(Class type) {
        return FROM_CLASS.get(type.getSimpleName());
    }
    
    public static Pattern fromName(String name) {
        return FROM_NAME.get(name.toLowerCase());
    }

}
