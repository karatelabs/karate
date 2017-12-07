package demo.error;

import cucumber.api.CucumberOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/error/error.feature")
public class ErrorRunner extends TestBase {
    
}
