package com.intuit.karate.driver;

import com.intuit.karate.TestUtils;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class DriverOptionsTest {

    static void test(String in, String out) {
        assertEquals(out, DriverOptions.preProcessWildCard(in));
    }

    @Test
    void testPreProcess() {
        test("{}hi", "//*[normalize-space(text())='hi']");
        test("{^}hi", "//*[contains(normalize-space(text()),'hi')]");
        test("{^:}hi", "//*[contains(normalize-space(text()),'hi')]");
        test("{^:0}hi", "//*[contains(normalize-space(text()),'hi')]");
        test("{^:2}hi", "/(//*[contains(normalize-space(text()),'hi')])[2]");
        test("{:2}hi", "/(//*[normalize-space(text())='hi'])[2]");
        test("{a}hi", "//a[normalize-space(text())='hi']");
        test("{a:2}hi", "/(//a[normalize-space(text())='hi'])[2]");
        test("{^a:}hi", "//a[contains(normalize-space(text()),'hi')]");
        test("{^a/p}hi", "//a/p[contains(normalize-space(text()),'hi')]");
        test("{^a:2}hi", "/(//a[contains(normalize-space(text()),'hi')])[2]");
    }

    @Test
    void testRetry() {
        DriverOptions options = new DriverOptions(Collections.EMPTY_MAP, TestUtils.runtime(), 0, null);
        options.retry(() -> 1, x -> x < 5, "not 5", false);
    }

}
