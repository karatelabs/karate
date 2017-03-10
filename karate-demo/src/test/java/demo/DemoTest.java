package demo;

import cucumber.api.CucumberOptions;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(plugin = {"pretty", "html:target/cucumber", "junit:target/cucumber-junit.xml"}, tags = {"~@ignore"})
public class DemoTest extends TestBase {
    // this class will automatically pick up all *.feature files
    // in src/test/java/demo and even recurse sub-directories
}
