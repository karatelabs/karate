package com.intuit.karate.driver;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class DriverOptionsTest {

    private static void test(String in, String out) {
        assertEquals(out, DriverOptions.preProcessIfWildCard(in));
    }

    @Test
    public void testPreProcess() {
        test("^hi", "//*[text()='hi']");
        test("*hi", "//*[contains(text(),'hi')]");
        test("^()hi", "//*[text()='hi']");
        test("^(:)hi", "//*[text()='hi']");
        test("^(:0)hi", "//*[text()='hi']");
        test("^(:1)hi", "//*[text()='hi'][2]");
        test("^(a:1)hi", "//a[text()='hi'][2]");
        test("^(a)hi", "//a[text()='hi']");
        test("^(a:)hi", "//a[text()='hi']");
        test("^(a/p)hi", "//a/p[text()='hi']");
        test("*(a:1)hi", "//a[contains(text(),'hi')][2]");
    }

}
