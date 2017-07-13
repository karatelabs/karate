package demo.headers;

import cucumber.api.CucumberOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/headers/call-isolated-config.feature")
public class CallIsolatedConfigRunner extends TestBase {
    
}
