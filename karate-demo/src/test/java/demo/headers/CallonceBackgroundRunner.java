package demo.headers;

import cucumber.api.CucumberOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/headers/callonce-background-multiscenario.feature")
public class CallonceBackgroundRunner extends TestBase {
    
}
