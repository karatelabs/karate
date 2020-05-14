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

    Invoke("UIA_InvokePatternId", 10000),
    Selection("UIA_SelectionPatternId", 10001),
    Value("UIA_ValuePatternId", 10002, IUIAutomationValuePattern.class),
    RangeValue("UIA_RangeValuePatternId", 10003),
    Scroll("UIA_ScrollPatternId", 10004),
    ExpandCollapse("UIA_ExpandCollapsePatternId", 10005),
    Grid("UIA_GridPatternId", 10006),
    GridItem("UIA_GridItemPatternId", 10007),
    MultipleView("UIA_MultipleViewPatternId", 10008),
    Window("UIA_WindowPatternId", 10009),
    SelectionItem("UIA_SelectionItemPatternId", 10010),
    Dock("UIA_DockPatternId", 10011),
    Table("UIA_TablePatternId", 10012),
    TableItem("UIA_TableItemPatternId", 10013),
    Text("UIA_TextPatternId", 10014),
    Toggle("UIA_TogglePatternId", 10015),
    Transform("UIA_TransformPatternId", 10016),
    ScrollItem("UIA_ScrollItemPatternId", 10017),
    LegacyIAccessible("UIA_LegacyIAccessiblePatternId", 10018),
    ItemContainer("UIA_ItemContainerPatternId", 10019),
    VirtualizedItem("UIA_VirtualizedItemPatternId", 10020),
    SynchronizedInput("UIA_SynchronizedInputPatternId", 10021),
    ObjectModel("UIA_ObjectModelPatternId", 10022),
    Annotation("UIA_AnnotationPatternId", 10023),
    Text2("UIA_TextPattern2Id", 10024),
    Styles("UIA_StylesPatternId", 10025),
    Spreadsheet("UIA_SpreadsheetPatternId", 10026),
    SpreadsheetItem("UIA_SpreadsheetItemPatternId", 10027),
    Transform2("UIA_TransformPattern2Id", 10028),
    TextChild("UIA_TextChildPatternId", 10029),
    Drag("UIA_DragPatternId", 10030),
    DropTarget("UIA_DropTargetPatternId", 10031),
    TextEdit("UIA_TextEditPatternId", 10032),
    CustomNavigation("UIA_CustomNavigationPatternId", 10033);

    public final String key;
    public final int value;
    public final Class type;

    private Pattern(String key, int value) {
        this(key, value, null);
    }

    private Pattern(String key, int value, Class type) {
        this.key = key;
        this.value = value;
        this.type = type;
    }

    private static final Map<String, Pattern> FROM_CLASS;

    static {
        Pattern[] patterns = Pattern.values();
        FROM_CLASS = new HashMap(patterns.length);
        for (Pattern p : patterns) {
            if (p.type != null) {
                FROM_CLASS.put(p.type.getSimpleName(), p);
            }
        }
    }

    public static Pattern fromType(Class type) {
        return FROM_CLASS.get(type.getSimpleName());
    }

}
