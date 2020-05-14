package com.intuit.karate.robot.win;

import com.intuit.karate.StringUtils;
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
        IUIAutomationElementArray children = rootElement.findAll(TreeScope.Children, trueCondition);
        int count = children.getLength();
        logger.debug("child length: {}", count);
        for (int i = 0; i < count; i++) {
            IUIAutomationElement e = children.getElement(i);
            logger.debug("name {}: {}", i, e.getCurrentName());
        }
        IUIAutomationCondition nameCondition = ui.createPropertyCondition(Property.Name, "Program Manager");
        IUIAutomationElement found = rootElement.findFirst(TreeScope.Children, nameCondition);
        assertEquals("Program Manager", found.getCurrentName());
        
        String testName = found.getCurrentPropertyValue(Property.Name).stringValue();
        assertEquals("Program Manager", testName);
        
        IUIAutomationCondition andCondition = ui.createAndCondition(nameCondition, trueCondition);
        children = rootElement.findAll(TreeScope.Children, andCondition);
        assertEquals(1, children.getLength());
        assertEquals("Program Manager", children.getElement(0).getCurrentName());
        int windowControlType = ComUtils.enumValue("UIA_ControlTypeIds", "UIA_WindowControlTypeId");
        IUIAutomationCondition windowCondition = ui.createPropertyCondition(Property.ControlType, windowControlType);
        children = rootElement.findAll(TreeScope.Children, windowCondition);
        count = children.getLength();
        logger.debug("windows length: {}", count);
        IUIAutomationElement last = null;
        for (int i = 0; i < count; i++) {
            last = children.getElement(i);
            logger.debug("name {}: {}", i, last.getCurrentName());
        }        
        IUIAutomationTreeWalker walker = ui.getControlViewWalker();
        walk(walker, last, 0);       
    }
    
    private static void walk(IUIAutomationTreeWalker walker, IUIAutomationElement e, int depth) {
        String indent = StringUtils.repeat(' ', depth * 2);
        logger.debug("{}{}:{}|{}", indent, e.getControlType(), e.getClassName(), e.getCurrentName());
        IUIAutomationElement child = walker.getFirstChildElement(e);
        while (!child.isNull()) {
            walk(walker, child, depth + 1);
            child = walker.getNextSiblingElement(child);
        }
    }

}
