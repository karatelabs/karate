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

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public enum ControlType {

    Button(50000),
    Calendar(50001),
    CheckBox(50002),
    ComboBox(50003),
    Edit(50004),
    Hyperlink(50005),
    Image(50006),
    ListItem(50007),
    List(50008),
    Menu(50009),
    MenuBar(50010),
    MenuItem(50011),
    ProgressBar(50012),
    RadioButton(50013),
    ScrollBar(50014),
    Slider(50015),
    Spinner(50016),
    StatusBar(50017),
    Tab(50018),
    TabItem(50019),
    Text(50020),
    ToolBar(50021),
    ToolTip(50022),
    Tree(50023),
    TreeItem(50024),
    Custom(50025),
    Group(50026),
    Thumb(50027),
    DataGrid(50028),
    DataItem(50029),
    Document(50030),
    SplitButton(50031),
    Window(50032),
    Pane(50033),
    Header(50034),
    HeaderItem(50035),
    Table(50036),
    TitleBar(50037),
    Separator(50038),
    SemanticZoom(50039),
    AppBar(50040);

    public final int value;

    private ControlType(int value) {
        this.value = value;
    }

    private final static Map<Integer, ControlType> FROM_VALUE;
    private final static Map<String, ControlType> FROM_NAME;

    static {
        ControlType[] values = ControlType.values();
        FROM_VALUE = new HashMap(values.length);
        FROM_NAME = new HashMap(values.length);
        for (ControlType ct : values) {
            FROM_VALUE.put(ct.value, ct);
            FROM_NAME.put(ct.name().toLowerCase(), ct);
        }
    }

    public static ControlType fromValue(int value) {
        return FROM_VALUE.get(value);
    }
    
    public static ControlType fromName(String name) {
        return FROM_NAME.get(name.toLowerCase());
    }    

}
