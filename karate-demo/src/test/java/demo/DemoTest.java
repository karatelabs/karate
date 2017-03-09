package demo;

import cucumber.api.CucumberOptions;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(plugin = {"pretty", "html:target/cucumber"}, tags = {"~@ignore"})
public class DemoTest extends TestBase {
    // this class will automatically pick up all *.feature files
    // in src/test/java/karate and even recurse sub-directories
}
