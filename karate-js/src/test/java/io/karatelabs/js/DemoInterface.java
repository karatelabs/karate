package io.karatelabs.js;

/**
 * Test interface with static fields to verify JS engine can access them.
 * Similar to io.karatelabs.driver.Keys which has static String fields.
 */
public interface DemoInterface {

    // Static fields (implicitly public static final in interfaces)
    String ENTER = "\uE007";
    String TAB = "\uE004";
    String ESCAPE = "\uE00C";

    // A non-unicode string for easier testing
    String FOO = "foo-value";
    String BAR = "bar-value";

    // Interface methods
    void doSomething();

}
