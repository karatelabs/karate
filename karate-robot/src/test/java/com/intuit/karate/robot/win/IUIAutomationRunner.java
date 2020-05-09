package com.intuit.karate.robot.win;

import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class IUIAutomationRunner {

    private static final Logger logger = LoggerFactory.getLogger(IUIAutomationRunner.class);

    @Test
    public void testAutomation() {
        IUIAutomation ui = IUIAutomation.INSTANCE;
        IUIAutomationElement rootElement = ui.getRootElement();
        assertEquals("Desktop", rootElement.getCurrentName());
        IUIAutomationElement focused = ui.getFocusedElement();
        String focusedName = focused.getCurrentName();
        logger.debug("focused element name: {}", focusedName);
        IUIAutomationCondition trueCondition = ui.createTrueCondition();
        IUIAutomationElementArray children = rootElement.findAll("TreeScope_Children", trueCondition);
        int count = children.getLength();
        logger.debug("child length: {}", count);
        for (int i = 0; i < count; i++) {
            IUIAutomationElement e = children.getElement(i);
            logger.debug("name {}: {}", i, e.getCurrentName());
        }
        IUIAutomationCondition nameCondition = ui.createPropertyCondition("UIA_NamePropertyId", "Program Manager");
        IUIAutomationElement found = rootElement.findFirst("TreeScope_Children", nameCondition);
        assertEquals("Program Manager", found.getCurrentName());
        
        String testName = found.getCurrentPropertyValue("UIA_NamePropertyId").stringValue();
        assertEquals("Program Manager", testName);
        
        IUIAutomationCondition andCondition = ui.createAndCondition(nameCondition, trueCondition);
        children = rootElement.findAll("TreeScope_Children", andCondition);
        assertEquals(1, children.getLength());
        assertEquals("Program Manager", children.getElement(0).getCurrentName());
        int windowControlType = ComUtils.enumValue("UIA_ControlTypeIds", "UIA_WindowControlTypeId");
        IUIAutomationCondition windowCondition = ui.createPropertyCondition("UIA_ControlTypePropertyId", windowControlType);
        children = rootElement.findAll("TreeScope_Children", windowCondition);
        count = children.getLength();
        logger.debug("windows length: {}", count);
        for (int i = 0; i < count; i++) {
            IUIAutomationElement e = children.getElement(i);
            logger.debug("name {}: {}", i, e.getCurrentName());
        }        
        children.getElement(count - 1).setFocus();
    }

}
