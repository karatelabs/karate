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

/**
 *
 * @author pthomas3
 */
public enum Property {

    RuntimeId("UIA_RuntimeIdPropertyId", 30000),
    BoundingRectangle("UIA_BoundingRectanglePropertyId", 30001),
    ProcessId("UIA_ProcessIdPropertyId", 30002),
    ControlType("UIA_ControlTypePropertyId", 30003),
    LocalizedControlType("UIA_LocalizedControlTypePropertyId", 30004),
    Name("UIA_NamePropertyId", 30005),
    AcceleratorKey("UIA_AcceleratorKeyPropertyId", 30006),
    AccessKey("UIA_AccessKeyPropertyId", 30007),
    HasKeyboardFocus("UIA_HasKeyboardFocusPropertyId", 30008),
    IsKeyboardFocusable("UIA_IsKeyboardFocusablePropertyId", 30009),
    IsEnabled("UIA_IsEnabledPropertyId", 30010),
    AutomationId("UIA_AutomationIdPropertyId", 30011),
    ClassName("UIA_ClassNamePropertyId", 30012),
    HelpText("UIA_HelpTextPropertyId", 30013),
    ClickablePoint("UIA_ClickablePointPropertyId", 30014),
    Culture("UIA_CulturePropertyId", 30015),
    IsControlElement("UIA_IsControlElementPropertyId", 30016),
    IsContentElement("UIA_IsContentElementPropertyId", 30017),
    LabeledBy("UIA_LabeledByPropertyId", 30018),
    IsPassword("UIA_IsPasswordPropertyId", 30019),
    NativeWindowHandle("UIA_NativeWindowHandlePropertyId", 30020),
    ItemType("UIA_ItemTypePropertyId", 30021),
    IsOffscreen("UIA_IsOffscreenPropertyId", 30022),
    Orientation("UIA_OrientationPropertyId", 30023),
    FrameworkId("UIA_FrameworkIdPropertyId", 30024),
    IsRequiredForForm("UIA_IsRequiredForFormPropertyId", 30025),
    ItemStatus("UIA_ItemStatusPropertyId", 30026),
    IsDockPatternAvailable("UIA_IsDockPatternAvailablePropertyId", 30027),
    IsExpandCollapsePatternAvailable("UIA_IsExpandCollapsePatternAvailablePropertyId", 30028),
    IsGridItemPatternAvailable("UIA_IsGridItemPatternAvailablePropertyId", 30029),
    IsGridPatternAvailable("UIA_IsGridPatternAvailablePropertyId", 30030),
    IsInvokePatternAvailable("UIA_IsInvokePatternAvailablePropertyId", 30031),
    IsMultipleViewPatternAvailable("UIA_IsMultipleViewPatternAvailablePropertyId", 30032),
    IsRangeValuePatternAvailable("UIA_IsRangeValuePatternAvailablePropertyId", 30033),
    IsScrollPatternAvailable("UIA_IsScrollPatternAvailablePropertyId", 30034),
    IsScrollItemPatternAvailable("UIA_IsScrollItemPatternAvailablePropertyId", 30035),
    IsSelectionItemPatternAvailable("UIA_IsSelectionItemPatternAvailablePropertyId", 30036),
    IsSelectionPatternAvailable("UIA_IsSelectionPatternAvailablePropertyId", 30037),
    IsTablePatternAvailable("UIA_IsTablePatternAvailablePropertyId", 30038),
    IsTableItemPatternAvailable("UIA_IsTableItemPatternAvailablePropertyId", 30039),
    IsTextPatternAvailable("UIA_IsTextPatternAvailablePropertyId", 30040),
    IsTogglePatternAvailable("UIA_IsTogglePatternAvailablePropertyId", 30041),
    IsTransformPatternAvailable("UIA_IsTransformPatternAvailablePropertyId", 30042),
    IsValuePatternAvailable("UIA_IsValuePatternAvailablePropertyId", 30043),
    IsWindowPatternAvailable("UIA_IsWindowPatternAvailablePropertyId", 30044),
    ValueValue("UIA_ValueValuePropertyId", 30045),
    ValueIsReadOnly("UIA_ValueIsReadOnlyPropertyId", 30046),
    RangeValueValue("UIA_RangeValueValuePropertyId", 30047),
    RangeValueIsReadOnly("UIA_RangeValueIsReadOnlyPropertyId", 30048),
    RangeValueMinimum("UIA_RangeValueMinimumPropertyId", 30049),
    RangeValueMaximum("UIA_RangeValueMaximumPropertyId", 30050),
    RangeValueLargeChange("UIA_RangeValueLargeChangePropertyId", 30051),
    RangeValueSmallChange("UIA_RangeValueSmallChangePropertyId", 30052),
    ScrollHorizontalScrollPercent("UIA_ScrollHorizontalScrollPercentPropertyId", 30053),
    ScrollHorizontalViewSize("UIA_ScrollHorizontalViewSizePropertyId", 30054),
    ScrollVerticalScrollPercent("UIA_ScrollVerticalScrollPercentPropertyId", 30055),
    ScrollVerticalViewSize("UIA_ScrollVerticalViewSizePropertyId", 30056),
    ScrollHorizontallyScrollable("UIA_ScrollHorizontallyScrollablePropertyId", 30057),
    ScrollVerticallyScrollable("UIA_ScrollVerticallyScrollablePropertyId", 30058),
    SelectionSelection("UIA_SelectionSelectionPropertyId", 30059),
    SelectionCanSelectMultiple("UIA_SelectionCanSelectMultiplePropertyId", 30060),
    SelectionIsSelectionRequired("UIA_SelectionIsSelectionRequiredPropertyId", 30061),
    GridRowCount("UIA_GridRowCountPropertyId", 30062),
    GridColumnCount("UIA_GridColumnCountPropertyId", 30063),
    GridItemRow("UIA_GridItemRowPropertyId", 30064),
    GridItemColumn("UIA_GridItemColumnPropertyId", 30065),
    GridItemRowSpan("UIA_GridItemRowSpanPropertyId", 30066),
    GridItemColumnSpan("UIA_GridItemColumnSpanPropertyId", 30067),
    GridItemContainingGrid("UIA_GridItemContainingGridPropertyId", 30068),
    DockDockPosition("UIA_DockDockPositionPropertyId", 30069),
    ExpandCollapseExpandCollapseState("UIA_ExpandCollapseExpandCollapseStatePropertyId", 30070),
    MultipleViewCurrentView("UIA_MultipleViewCurrentViewPropertyId", 30071),
    MultipleViewSupportedViews("UIA_MultipleViewSupportedViewsPropertyId", 30072),
    WindowCanMaximize("UIA_WindowCanMaximizePropertyId", 30073),
    WindowCanMinimize("UIA_WindowCanMinimizePropertyId", 30074),
    WindowWindowVisualState("UIA_WindowWindowVisualStatePropertyId", 30075),
    WindowWindowInteractionState("UIA_WindowWindowInteractionStatePropertyId", 30076),
    WindowIsModal("UIA_WindowIsModalPropertyId", 30077),
    WindowIsTopmost("UIA_WindowIsTopmostPropertyId", 30078),
    SelectionItemIsSelected("UIA_SelectionItemIsSelectedPropertyId", 30079),
    SelectionItemSelectionContainer("UIA_SelectionItemSelectionContainerPropertyId", 30080),
    TableRowHeaders("UIA_TableRowHeadersPropertyId", 30081),
    TableColumnHeaders("UIA_TableColumnHeadersPropertyId", 30082),
    TableRowOrColumnMajor("UIA_TableRowOrColumnMajorPropertyId", 30083),
    TableItemRowHeaderItems("UIA_TableItemRowHeaderItemsPropertyId", 30084),
    TableItemColumnHeaderItems("UIA_TableItemColumnHeaderItemsPropertyId", 30085),
    ToggleToggleState("UIA_ToggleToggleStatePropertyId", 30086),
    TransformCanMove("UIA_TransformCanMovePropertyId", 30087),
    TransformCanResize("UIA_TransformCanResizePropertyId", 30088),
    TransformCanRotate("UIA_TransformCanRotatePropertyId", 30089),
    IsLegacyIAccessiblePatternAvailable("UIA_IsLegacyIAccessiblePatternAvailablePropertyId", 30090),
    LegacyIAccessibleChildId("UIA_LegacyIAccessibleChildIdPropertyId", 30091),
    LegacyIAccessibleName("UIA_LegacyIAccessibleNamePropertyId", 30092),
    LegacyIAccessibleValue("UIA_LegacyIAccessibleValuePropertyId", 30093),
    LegacyIAccessibleDescription("UIA_LegacyIAccessibleDescriptionPropertyId", 30094),
    LegacyIAccessibleRole("UIA_LegacyIAccessibleRolePropertyId", 30095),
    LegacyIAccessibleState("UIA_LegacyIAccessibleStatePropertyId", 30096),
    LegacyIAccessibleHelp("UIA_LegacyIAccessibleHelpPropertyId", 30097),
    LegacyIAccessibleKeyboardShortcut("UIA_LegacyIAccessibleKeyboardShortcutPropertyId", 30098),
    LegacyIAccessibleSelection("UIA_LegacyIAccessibleSelectionPropertyId", 30099),
    LegacyIAccessibleDefaultAction("UIA_LegacyIAccessibleDefaultActionPropertyId", 30100),
    AriaRole("UIA_AriaRolePropertyId", 30101),
    AriaProperties("UIA_AriaPropertiesPropertyId", 30102),
    IsDataValidForForm("UIA_IsDataValidForFormPropertyId", 30103),
    ControllerFor("UIA_ControllerForPropertyId", 30104),
    DescribedBy("UIA_DescribedByPropertyId", 30105),
    FlowsTo("UIA_FlowsToPropertyId", 30106),
    ProviderDescription("UIA_ProviderDescriptionPropertyId", 30107),
    IsItemContainerPatternAvailable("UIA_IsItemContainerPatternAvailablePropertyId", 30108),
    IsVirtualizedItemPatternAvailable("UIA_IsVirtualizedItemPatternAvailablePropertyId", 30109),
    IsSynchronizedInputPatternAvailable("UIA_IsSynchronizedInputPatternAvailablePropertyId", 30110),
    OptimizeForVisualContent("UIA_OptimizeForVisualContentPropertyId", 30111),
    IsObjectModelPatternAvailable("UIA_IsObjectModelPatternAvailablePropertyId", 30112),
    AnnotationAnnotationTypeId("UIA_AnnotationAnnotationTypeIdPropertyId", 30113),
    AnnotationAnnotationTypeName("UIA_AnnotationAnnotationTypeNamePropertyId", 30114),
    AnnotationAuthor("UIA_AnnotationAuthorPropertyId", 30115),
    AnnotationDateTime("UIA_AnnotationDateTimePropertyId", 30116),
    AnnotationTarget("UIA_AnnotationTargetPropertyId", 30117),
    IsAnnotationPatternAvailable("UIA_IsAnnotationPatternAvailablePropertyId", 30118),
    IsTextPattern2Available("UIA_IsTextPattern2AvailablePropertyId", 30119),
    StylesStyleId("UIA_StylesStyleIdPropertyId", 30120),
    StylesStyleName("UIA_StylesStyleNamePropertyId", 30121),
    StylesFillColor("UIA_StylesFillColorPropertyId", 30122),
    StylesFillPatternStyle("UIA_StylesFillPatternStylePropertyId", 30123),
    StylesShape("UIA_StylesShapePropertyId", 30124),
    StylesFillPatternColor("UIA_StylesFillPatternColorPropertyId", 30125),
    StylesExtendedProperties("UIA_StylesExtendedPropertiesPropertyId", 30126),
    IsStylesPatternAvailable("UIA_IsStylesPatternAvailablePropertyId", 30127),
    IsSpreadsheetPatternAvailable("UIA_IsSpreadsheetPatternAvailablePropertyId", 30128),
    SpreadsheetItemFormula("UIA_SpreadsheetItemFormulaPropertyId", 30129),
    SpreadsheetItemAnnotationObjects("UIA_SpreadsheetItemAnnotationObjectsPropertyId", 30130),
    SpreadsheetItemAnnotationTypes("UIA_SpreadsheetItemAnnotationTypesPropertyId", 30131),
    IsSpreadsheetItemPatternAvailable("UIA_IsSpreadsheetItemPatternAvailablePropertyId", 30132),
    Transform2CanZoom("UIA_Transform2CanZoomPropertyId", 30133),
    IsTransformPattern2Available("UIA_IsTransformPattern2AvailablePropertyId", 30134),
    LiveSetting("UIA_LiveSettingPropertyId", 30135),
    IsTextChildPatternAvailable("UIA_IsTextChildPatternAvailablePropertyId", 30136),
    IsDragPatternAvailable("UIA_IsDragPatternAvailablePropertyId", 30137),
    DragIsGrabbed("UIA_DragIsGrabbedPropertyId", 30138),
    DragDropEffect("UIA_DragDropEffectPropertyId", 30139),
    DragDropEffects("UIA_DragDropEffectsPropertyId", 30140),
    IsDropTargetPatternAvailable("UIA_IsDropTargetPatternAvailablePropertyId", 30141),
    DropTargetDropTargetEffect("UIA_DropTargetDropTargetEffectPropertyId", 30142),
    DropTargetDropTargetEffects("UIA_DropTargetDropTargetEffectsPropertyId", 30143),
    DragGrabbedItems("UIA_DragGrabbedItemsPropertyId", 30144),
    Transform2ZoomLevel("UIA_Transform2ZoomLevelPropertyId", 30145),
    Transform2ZoomMinimum("UIA_Transform2ZoomMinimumPropertyId", 30146),
    Transform2ZoomMaximum("UIA_Transform2ZoomMaximumPropertyId", 30147),
    FlowsFrom("UIA_FlowsFromPropertyId", 30148),
    IsTextEditPatternAvailable("UIA_IsTextEditPatternAvailablePropertyId", 30149),
    IsPeripheral("UIA_IsPeripheralPropertyId", 30150),
    IsCustomNavigationPatternAvailable("UIA_IsCustomNavigationPatternAvailablePropertyId", 30151),
    PositionInSet("UIA_PositionInSetPropertyId", 30152),
    SizeOfSet("UIA_SizeOfSetPropertyId", 30153),
    Level("UIA_LevelPropertyId", 30154),
    AnnotationTypes("UIA_AnnotationTypesPropertyId", 30155),
    AnnotationObjects("UIA_AnnotationObjectsPropertyId", 30156);

    public final String key;
    public final int value;

    private Property(String key, int value) {
        this.key = key;
        this.value = value;
    }

}
