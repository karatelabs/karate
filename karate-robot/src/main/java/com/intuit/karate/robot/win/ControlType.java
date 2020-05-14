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

    Button("UIA_ButtonControlTypeId", 50000),
    Calendar("UIA_CalendarControlTypeId", 50001),
    CheckBox("UIA_CheckBoxControlTypeId", 50002),
    ComboBox("UIA_ComboBoxControlTypeId", 50003),
    Edit("UIA_EditControlTypeId", 50004),
    Hyperlink("UIA_HyperlinkControlTypeId", 50005),
    Image("UIA_ImageControlTypeId", 50006),
    ListItem("UIA_ListItemControlTypeId", 50007),
    List("UIA_ListControlTypeId", 50008),
    Menu("UIA_MenuControlTypeId", 50009),
    MenuBar("UIA_MenuBarControlTypeId", 50010),
    MenuItem("UIA_MenuItemControlTypeId", 50011),
    ProgressBar("UIA_ProgressBarControlTypeId", 50012),
    RadioButton("UIA_RadioButtonControlTypeId", 50013),
    ScrollBar("UIA_ScrollBarControlTypeId", 50014),
    Slider("UIA_SliderControlTypeId", 50015),
    Spinner("UIA_SpinnerControlTypeId", 50016),
    StatusBar("UIA_StatusBarControlTypeId", 50017),
    Tab("UIA_TabControlTypeId", 50018),
    TabItem("UIA_TabItemControlTypeId", 50019),
    Text("UIA_TextControlTypeId", 50020),
    ToolBar("UIA_ToolBarControlTypeId", 50021),
    ToolTip("UIA_ToolTipControlTypeId", 50022),
    Tree("UIA_TreeControlTypeId", 50023),
    TreeItem("UIA_TreeItemControlTypeId", 50024),
    Custom("UIA_CustomControlTypeId", 50025),
    Group("UIA_GroupControlTypeId", 50026),
    Thumb("UIA_ThumbControlTypeId", 50027),
    DataGrid("UIA_DataGridControlTypeId", 50028),
    DataItem("UIA_DataItemControlTypeId", 50029),
    Document("UIA_DocumentControlTypeId", 50030),
    SplitButton("UIA_SplitButtonControlTypeId", 50031),
    Window("UIA_WindowControlTypeId", 50032),
    Pane("UIA_PaneControlTypeId", 50033),
    Header("UIA_HeaderControlTypeId", 50034),
    HeaderItem("UIA_HeaderItemControlTypeId", 50035),
    Table("UIA_TableControlTypeId", 50036),
    TitleBar("UIA_TitleBarControlTypeId", 50037),
    Separator("UIA_SeparatorControlTypeId", 50038),
    SemanticZoom("UIA_SemanticZoomControlTypeId", 50039),
    AppBar("UIA_AppBarControlTypeId", 50040);

    public final String key;
    public final int value;

    private ControlType(String key, int value) {
        this.key = key;
        this.value = value;
    }

    private final static Map<Integer, ControlType> FROM_VALUE;

    static {
        ControlType[] values = ControlType.values();
        FROM_VALUE = new HashMap(values.length);
        for (ControlType ct : values) {
            FROM_VALUE.put(ct.value, ct);
        }
    }

    public static ControlType fromValue(int value) {
        return FROM_VALUE.get(value);
    }

}
