package com.intuit.karate.driver;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class DriverOptionsTest {

    private static void test(String in, String out) {
        assertEquals(out, DriverOptions.preProcessWildCard(in));
    }

    @Test
    public void testPreProcess() {
        test("{}hi", "//*[normalize-space(text())='hi']");
        test("{^}hi", "//*[contains(normalize-space(text()),'hi')]");
        test("{^:}hi", "//*[contains(normalize-space(text()),'hi')]");
        test("{^:0}hi", "//*[contains(normalize-space(text()),'hi')]");
        test("{^:1}hi", "//*[contains(normalize-space(text()),'hi')][2]");
        test("{:1}hi", "//*[normalize-space(text())='hi'][2]");
        test("{a}hi", "//a[normalize-space(text())='hi']");
        test("{a:1}hi", "//a[normalize-space(text())='hi'][2]");        
        test("{^a:}hi", "//a[contains(normalize-space(text()),'hi')]");
        test("{^a/p}hi", "//a/p[contains(normalize-space(text()),'hi')]");
        test("{^a:1}hi", "//a[contains(normalize-space(text()),'hi')][2]");
    }

}
