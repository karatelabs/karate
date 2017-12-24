package demo.headers;

import cucumber.api.CucumberOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/headers/call-isolated-headers.feature")
public class CallIsolatedHeadersRunner extends TestBase {
    
}
