package demo;

import cucumber.api.CucumberOptions;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(tags = {"~@ignore"})
public class DemoTest extends TestBase {
    // this class will automatically pick up all *.feature files
    // in src/test/java/demo and even recurse sub-directories
}
